package ch.brickwork.bsuit.interpreter.interpreters;

import ch.brickwork.bsuit.database.IDatabase;
import ch.brickwork.bsuit.database.Record;
import ch.brickwork.bsuit.database.Variable;
import ch.brickwork.bsuit.globals.IBoilersuitApplicationContext;
import ch.brickwork.bsuit.util.TextUtils;
import org.apache.commons.lang3.StringUtils;
import java.util.Hashtable;
import java.util.List;

/**
 * <p>Counts the occurrence of selected attribute in the table, shows if there are any duplicates.
 * <p/>
 * <h2>Syntax</h2>
 * <p class="syntax">?table_name(attribute)</p>
 */
public class StatisticsInterpreter extends AbstractInterpreter {

    private IDatabase database = context.getDatabase();

    /**
     * Stores target variable and command text (both are automatically trimmed) for interpreters.
     *
     * @param targetVariable     is target variable
     * @param command            is command text
     * @param context                is Logger instance
     */
    public StatisticsInterpreter(final Variable targetVariable, final String command, final IBoilersuitApplicationContext context)
    {
        super(targetVariable, command, context);
    }

    /**
     * Parses command, next invoke {@link #countOccurrences(String, String, String)} to counts the occurrence of selected attribute.
     *
     * @return processing results contains a message
     */
    @Override

    public ProcessingResult process()
    {
        String variableOrName;

        // attributes indicated -> name is between ? and (
        if (command.indexOf('(') != -1) {
            variableOrName = TextUtils.between(command, "?", "(");
        }
        // no attributes indicated -> name just after ?
        else {
            variableOrName = TextUtils.between(command, "?", null);
        }

        if (null != variableOrName) {
            variableOrName = variableOrName.trim().toLowerCase();
        }

        // get content of ( ) (should be the column name, exactly one)
        String firstColName = TextUtils.between(command, "(", ")");

        if (null != variableOrName && null != firstColName) {
            final String tableOrViewName = Variable.getTableName(variableOrName);
            if (database.existsTableOrView(tableOrViewName)) {
                // in case no column is indicated, just take the first column in the table
                final List<String> tableOrViewColumnNames = database.getTableOrViewColumnNames(tableOrViewName);
                if (!tableOrViewColumnNames.isEmpty() && StringUtils.isEmpty(firstColName)) {
                    firstColName = tableOrViewColumnNames.get(0);
                }
                // else, first check that it exists:
                else {
                    firstColName = firstColName.toLowerCase();
                    final Hashtable<String, Integer> tableOrViewColumnNamesHash = database.getTableOrViewColumnNamesHash(tableOrViewName);
                    if (null != tableOrViewColumnNamesHash && !tableOrViewColumnNamesHash.isEmpty() && tableOrViewColumnNamesHash.get(firstColName) == null) {
                        context.getLog().err(firstColName + " does not exist in table " + tableOrViewName + "!");
                        return ProcessingResult.syntaxError(null);
                    }
                }
                return countOccurrences(firstColName, tableOrViewName, variableOrName);
            } else {
                context.getLog().err(variableOrName + " does not exist!");
            }
        }
        return ProcessingResult.syntaxError("Usage: ?table(attr)");
    }

    @Override
    public boolean understands()
    {
        return command.indexOf("?") == 0 && command.length() > 1;
    }

    /**
     * For instance when the following command will be executed:
     * ?ourcompanies(name)
     *
     * @param firstColName    will contain 'name'
     * @param tableOrViewName will contain 'ourcompanies'
     * @param variableOrName  will contain 'ourcompanies'
     *                        <p/>
     *                        So in table 'ourcompanies' will be search for attribute 'name'.
     *
     * @return processing results contains the message e.g.:
     * 'ourcompanies(name) has 2564 records, no duplicates.'
     */
    private ProcessingResult countOccurrences(final String firstColName, final String tableOrViewName, final String variableOrName)
    {
        final ProcessingResult processingResult;
        final List<Record> results = database.prepare("SELECT COUNT(" + firstColName + ") FROM " + tableOrViewName);
        if (null != results && !results.isEmpty()) {
            final String countResult = (String) results.get(0).getFirstValueContent();
            final List<Record> distinctResults = database.prepare(
                "SELECT COUNT(" + firstColName + ") FROM " + "(SELECT DISTINCT " + firstColName + " FROM " + tableOrViewName + ")");
            if (null != distinctResults && !distinctResults.isEmpty()) {
                final String distinctCountResults = (String) distinctResults.get(0).getFirstValueContent();
                if (null != countResult && null != distinctCountResults && !countResult.trim().equals(distinctCountResults.trim())) {
                    final String tempCountName = database.createTempName();
                    final String detailsSQL =
                        "SELECT " + firstColName + ", count(" + firstColName + ") as " + tempCountName + " from " + tableOrViewName + " GROUP BY "
                            + firstColName + " HAVING " + tempCountName + " <> 1 ORDER BY " + tempCountName + " DESC";
                    final List<Record> listDuplicates = database.prepare(detailsSQL + " LIMIT 5");
                    String info = "For instance:\n";
                    if (null != listDuplicates && null != firstColName) {
                        for (final Record duplicate : listDuplicates) {
                            info += duplicate.getValue(firstColName).getValue().toString() + " (" + duplicate.getValue(tempCountName).getValue().toString()
                                + "x)\n";
                        }
                    }
                    info += "Run this for all:\n" + detailsSQL;

                    processingResult = new ProcessingResult(ProcessingResult.ResultType.MESSAGE,
                        "DUPLICATES! " + variableOrName + (firstColName == null ? "" : ("(" + firstColName + ")")) + " has " + countResult + " records, "
                            + distinctCountResults + " are unique.\n" + info
                    );
                    processingResult.setScript(script);
                    return processingResult;
                } else {
                    processingResult = new ProcessingResult(ProcessingResult.ResultType.MESSAGE,
                        variableOrName + (firstColName == null ? "" : ("(" + firstColName + ")")) + " has " + countResult + " records, no duplicates.");
                    processingResult.setScript(script);
                    return processingResult;
                }
            }
        }
        return ProcessingResult.syntaxError("Usage: ?table(attr)");
    }
}
