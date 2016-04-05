package ch.brickwork.bsuit.interpreter.interpreters;

import ch.brickwork.bsuit.database.IDatabase;
import ch.brickwork.bsuit.database.Record;
import ch.brickwork.bsuit.database.Variable;
import ch.brickwork.bsuit.globals.IBoilersuitApplicationContext;
import ch.brickwork.bsuit.util.TextUtils;

import java.util.List;

/**
 * Counts the number of rows for a given variable.
 * <h2>Syntax</h2>
 * <p class="syntax">
 * #table-name[(where-clause)]
 * </p>
 * <h2>Examples</h2>
 * <p>
 * <pre>#table;</pre>
 * <pre>#mytable(attr1 like 'some value');</pre>
 * <pre>#cars(serialnumber = 12345);</pre>
 * </p>
 */
public class CountInterpreter extends AbstractInterpreter {

    public static final String FIRST_PART_OF_MESSAGE = " has ";

    public static final String SECOND_PART_OF_MESSAGE = " records.";

    /**
     * stores target variable and command text (both are automatically trimmed) for interpreters
     *
     * @param targetVariable is target variable
     * @param command        is command text
     * @param context        is Logger instance
     */
    public CountInterpreter(final Variable targetVariable, final String command, final IBoilersuitApplicationContext context) {
        super(targetVariable, command, context);
    }

    @Override
    public boolean needsTargetVariable() {
        return false;
    }


    /**
     * Executes SELECT clause to count the total number of records.
     * Use name of first found column a an argument for SELECT clause instead of '*' symbol.
     *
     * @return a message contains information about the number of records in given table.
     */
    @Override
    public ProcessingResult process() {
        ProcessingResult pr = new ProcessingResult(ProcessingResult.ResultType.MESSAGE, "Unknown Result (count failed!)");
        for (String variableName : command.substring(command.indexOf("#") + 1, command.length()).split(",")) {
            final int indexOfOpeningBracket = variableName.indexOf("(");
            final int indexOfClosingBracket = variableName.indexOf(")");
            String condition = null;

            // brackets exist (condition)
            if (indexOfClosingBracket != -1 && indexOfOpeningBracket != -1) {
                condition = variableName.substring(indexOfOpeningBracket + 1, indexOfClosingBracket);
                if (condition.trim().isEmpty()) {
                    condition = null;
                }
                variableName = variableName.substring(0, indexOfOpeningBracket);
            }

            // no bracket (no condition)
            else if (indexOfClosingBracket == -1 && indexOfOpeningBracket == -1) {
                // fine - just go ahead with null condition
            }

            // problem (syntax) - one parenthesis missing:
            else {
                return new ProcessingResult(ProcessingResult.ResultType.SYNTAX_ERROR, "Wrong syntax for condition - only one parenthesis found!");
            }

            variableName = variableName.trim();
            final String tableOrViewName = Variable.getTableName(variableName);
            final IDatabase database = context.getDatabase();
            if (database.existsTableOrView(tableOrViewName)) {
                final List<String> tableOrViewColumnNames = database.getTableOrViewColumnNames(tableOrViewName);
                String firstColName = null;
                if (!tableOrViewColumnNames.isEmpty()) {
                    firstColName = tableOrViewColumnNames.get(0);
                }
                if (null != firstColName) {
                    List<Record> result;
                    String sql = "SELECT COUNT(" + context.getDatabase().sanitizeName(firstColName) + ") FROM " + tableOrViewName;
                    if (null != condition) {
                        sql += " WHERE " + condition;
                    }
                    result = database.prepare(sql);
                    String count = "0";
                    if (result != null) {
                        count = (String) result.get(0).getFirstValueContent();
                    }
                    final String resultSummary = variableName + FIRST_PART_OF_MESSAGE + count + SECOND_PART_OF_MESSAGE;
                    context.getLog().info(resultSummary);
                    pr.setResultSummary(resultSummary);
                    pr.setSql(sql, null);
                    pr.setSingleValue(count);
                    pr.setScript(script);
                }
            }
            else {
                return new ProcessingResult(ProcessingResult.ResultType.FATAL_ERROR, tableOrViewName + " does not exist!");
            }
        }

        return pr;
    }

    @Override
    public boolean understands() {
        // contains exactly one #: count
        return TextUtils.count(command, "#") == 1;
    }
}
