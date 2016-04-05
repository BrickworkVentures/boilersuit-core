package ch.brickwork.bsuit.interpreter.interpreters;

import ch.brickwork.bsuit.database.IDatabase;
import ch.brickwork.bsuit.database.Record;
import ch.brickwork.bsuit.database.Variable;
import ch.brickwork.bsuit.globals.IBoilersuitApplicationContext;
import java.util.List;

/**
 * <p>
 *     Analyse how often a value occurs accross two tables.
 * </p>
 * <h2>Syntax</h2>
 * <p class="syntax">[result := ]<i>left-table</i><b>.</b><i>left-attribute</i><b> ./. </b><i>right-table</i><b>.</b><i>right-attribute</i></p>
 * <h2>Example</h2>
 * <pre>
 *  official.claim_id ./. previous.claim_id;
 * </pre>
 * <p>
 *     This will produce something like
 *     <table>
 *         <tr>
 *             <th>claim_id_or_claim_id</th>
 *             <th>official_count</th>
 *             <th>previous_count</th>
 *         </tr>
 *         <tr>
 *             <td>C123</td>
 *             <td>100</td>
 *             <td>50</td>
 *         </tr>
 *         <tr>
 *             <td>C234</td>
 *             <td>0</td>
 *             <td>50</td>
 *         </tr>
 *     </table>
 * </p>
 */
public class FrequencyDifferenceInterpreter extends AbstractInterpreter {

    private static final String FREQUENCY_DIFFERENCE_SYNTAX = "./.";
    private static final java.lang.String TXT_DO_NOT_UNDERSTAND = "Don't understand syntax. Use [var := ]table1.att1 ./. table2.att2";
    private static final java.lang.String TXT_DONE = "done";
    private static final java.lang.String TXT_TABLE_DOESNT_EXIST = "Table does not exist!";

    private final IDatabase database = context.getDatabase();

    /**
     * stores target variable and command text (both are automatically trimmed) for interpreters
     *
     * @param targetVariable     is target variable
     * @param command            is command text
     * @param context                is Logger instance
     */
    public FrequencyDifferenceInterpreter(final Variable targetVariable, final String command, final IBoilersuitApplicationContext context)
    {
        super(targetVariable, command, context);
    }

    /**
     * Search for tokens, e.g. from the following command:
     * official.dc_claim_id ./. previous.claim_id
     * will be extracted the following tokens:
     * official, dc_claim_id, previous and claim_id.
     *
     * @return the result of {@link #process(String, String, String, String, String)} method
     */
    @Override
    public ProcessingResult process() {
        final String[] tokens = command.split(FREQUENCY_DIFFERENCE_SYNTAX);
        String leftTable;
        String leftAttribute;
        String rightTable;
        String rightAttribute;
        if (tokens.length < 2) {
            return new ProcessingResult(ProcessingResult.ResultType.FATAL_ERROR,
                    TXT_DO_NOT_UNDERSTAND);
        } else {

            // split by anything but a character (e.g., space!)
            String[] leftPart = tokens[0].trim().split("\\.");
            String[] rightPart = tokens[1].trim().split("\\.");
            if (leftPart.length < 2 || rightPart.length < 2) {
                return new ProcessingResult(ProcessingResult.ResultType.FATAL_ERROR,
                        TXT_DO_NOT_UNDERSTAND);
            }
            leftTable = leftPart[0];
            leftAttribute = leftPart[1].replace(")", "");

            rightTable = rightPart[0];
            rightAttribute = rightPart[1].replace(")", "");

            // caution - if USE clause is present, it is within the right attribute piece:
            String processingInstructions = null;
            if(rightAttribute.contains("USE")) {
                processingInstructions = rightAttribute.split("USE")[1].trim();
                rightAttribute = rightAttribute.split("USE")[0].trim();
            }

            if (null != leftTable && null != leftAttribute && null != rightTable && null != rightAttribute) {
                return process(leftTable, leftAttribute, rightTable, rightAttribute, processingInstructions);
            } else {
                return new ProcessingResult(ProcessingResult.ResultType.FATAL_ERROR,
                        TXT_DO_NOT_UNDERSTAND);
            }
        }
    }

    @Override
    public boolean understands()
    {
        return command.contains(FREQUENCY_DIFFERENCE_SYNTAX);
    }

    /**
     * @param leftTable           table from the left side of './.'
     * @param rightTable          table from the right side of './.'
     * @param leftTableCountAttr  attribute name use for count query
     * @param rightTableCountAttr attribute name use for count query
     * @param overviewTableName   table contains the final results
     * @param useScript             script to be returned in this processing result
     * //@param notFoundTableName   table name contains the information about how many times the left attribute was found (or not and then column has null value) in the right table and vice versa
     * //@param notEqualTableName   table name contains the information about cases when the occurrence of left attribute in the right table was not equal the occurrence of right attribute in the left table and vice versa
     *
     * @return processing results contains a message e.g.:
     * 9 unique values in official and previous. 3 identical, 4 not present in both, 2 more often in one than in the other.
     */
    private ProcessingResult getProcessingResult(String leftTable, String rightTable, String leftTableCountAttr, String rightTableCountAttr,
                                                 String overviewTableName, String useScript) {
        int uniqueCount = 0, identicalCount = 0, notPresentCount = 0, moreOftenCount = 0;
        List<Record> countRecords = database.prepare("SELECT count(*) FROM " + overviewTableName);
        if (null != countRecords && !countRecords.isEmpty()) {
            uniqueCount = Integer.parseInt((String) countRecords.get(0).getFirstValueContent());
        }
        countRecords = database.prepare("SELECT count(*) FROM " + overviewTableName + " WHERE " + leftTableCountAttr + " = " + rightTableCountAttr + ";");
        if (null != countRecords && !countRecords.isEmpty()) {
            identicalCount = Integer.parseInt((String) countRecords.get(0).getFirstValueContent());
        }

        return new ProcessingResult(ProcessingResult.ResultType.MESSAGE,
                TXT_DONE, useScript
        );
    }



    /**
     * During this process will be created few tables used for further calculations but
     * the most important table ends with 'overview' and contains information about how often
     * the left attribute occurs in the right table and how often the right attribute occurs in the left table.
     *
     * @param leftTable      table from the left side of './.'
     * @param leftAttribute  attribute from the left side of './' used for calculate the results
     * @param rightTable     table from the right side of './.'
     * @param rightAttribute attribute from the right side of './' used for calculate the results
     * @param processingInstructions if contains "SUM", uses sum as aggregate function, if contains "COUNT", count.
     *
     *
     * @return messages contains a summary of whole process
     */

    private ProcessingResult process(final String leftTable, final String leftAttribute, final String rightTable,
                                     final String rightAttribute, final String processingInstructions)
    {
        ProcessingResult processingResult;

        if (!database.existsTable(leftTable) || !database.existsTable(rightTable)) {
            return new ProcessingResult(ProcessingResult.ResultType.FATAL_ERROR,
                    TXT_TABLE_DOESNT_EXIST);
        }

        /*
         * NOTE - the SUM-stuff does not really make sense. So theoretically the user could use the SUM
         * syntax and it is parsed, but it just takes the sum of the indicated attribute, which is normally
         * some sort of id, so it is simply the id which will be summed up... But we'll leave it as is
         * in the code for the moment.
         */

        String aggregateFunction = "count";
        String castAggregateExpressionLeft = "CAST(" + aggregateFunction + "(" + leftAttribute + ") AS INTEGER)";
        String castAggregateExpressionRight = "CAST(" + aggregateFunction + "(" + rightAttribute + ") AS INTEGER)";
        if(processingInstructions != null && processingInstructions.toLowerCase().contains("sum")) {
            aggregateFunction = "sum";
            castAggregateExpressionLeft = aggregateFunction + "(" + leftAttribute + ")";
            castAggregateExpressionRight = aggregateFunction + "(" + rightAttribute + ")";

        }

        final String tempName = database.createTempName() + "_";
        final String leftAggregateTableName = tempName + leftTable + "_" + aggregateFunction;
        database.createOrReplaceVariable(leftAggregateTableName, leftAggregateTableName, "");

        final String leftTableAggregateAttr = leftTable + "_" + aggregateFunction;
        final String rightTableAggregateAttr = rightTable + "_" + aggregateFunction;

        context.getLog().info("  pre-process " + leftTable);

        final String leftTableAggregateSQL =
                "CREATE TABLE " + leftAggregateTableName + " AS SELECT " + leftAttribute + ", " + castAggregateExpressionLeft + " AS " + leftTableAggregateAttr
                        + " FROM " + leftTable + " GROUP BY " + leftAttribute + ";";

        database.prepare(leftTableAggregateSQL);


        if(!leftTable.trim().equalsIgnoreCase(rightTable.trim())) {
            context.getLog().info("  pre-process " + rightTable);

            final String rightAggregateTableName = tempName + rightTable + "_" + aggregateFunction;
            database.createOrReplaceVariable(rightAggregateTableName, rightAggregateTableName, "");

            final String rightTableAggregateSQL =
                    "CREATE TABLE " + rightAggregateTableName + " AS SELECT " + rightAttribute + ", " + castAggregateExpressionRight + " AS " + rightTableAggregateAttr
                            + " FROM " + rightTable + " GROUP BY " + rightAttribute + ";";
            database.prepare(rightTableAggregateSQL);

            // the replace trick works for most DB's concatenate operator, so FreqDist will also
            // usable for something like x.a||n ./. y.a||b:
            final String overviewAttribute = (leftAttribute + "_or_" + rightAttribute).replaceAll("_*\\|\\|_*", "_");

            // build union of keys
            context.getLog().info("  calculate union of keys");
            String unionTableName = tempName + "union_" + overviewAttribute;
            String unionSql = "CREATE TABLE " + unionTableName + " AS SELECT " + leftAttribute + " AS " + overviewAttribute + " FROM " + leftTable + " UNION SELECT " + rightAttribute + " AS " + overviewAttribute + " FROM " + rightTable;
            database.createOrReplaceVariable(unionTableName, unionTableName, "");
            database.prepare(unionSql);

            final String leftTableStartChar = leftTable.substring(0, 1);
            final String rightTableStartChar = rightTable.substring(0, 1);

            final String leftJoinTableName = tempName + "lj_" + leftTableStartChar + "_" + rightTableStartChar;
            database.createOrReplaceVariable(leftJoinTableName, leftJoinTableName, "");

            context.getLog().info("  build left join table (may take time!)");
            final String leftJoinTableSQL =
                    "CREATE TABLE " + leftJoinTableName + " AS SELECT l." + overviewAttribute + ", r." + leftTableAggregateAttr + " FROM "
                            + unionTableName + " l LEFT OUTER JOIN " + leftAggregateTableName + " r ON l." + overviewAttribute + "=r." + leftAttribute + ";";
            database.prepare(leftJoinTableSQL);
            database.prepare("UPDATE " + leftJoinTableName + " SET " + leftTableAggregateAttr + " = " + "'0' WHERE " + leftTableAggregateAttr + " IS NULL");

            context.getLog().info("  build right join table (may take time!)");
            final String rightJoinTableName = getTargetVariable().getTableName();
            database.dropIfExistsViewOrTable(rightJoinTableName);
            database.createOrReplaceVariable(rightJoinTableName, rightJoinTableName, "");
            final String rightJoinTableSQL =
                    "CREATE TABLE " + rightJoinTableName + " AS SELECT l.*, r." + rightTableAggregateAttr + " FROM "
                            + leftJoinTableName + " l LEFT OUTER JOIN " + rightAggregateTableName + " r ON l." + overviewAttribute + "=r." + rightAttribute + ";";
            database.prepare(rightJoinTableSQL);

            context.getLog().info("  calculate result");

            database.prepare("UPDATE " + rightJoinTableName + " SET " + rightTableAggregateAttr + " = " + "'0' WHERE " + rightTableAggregateAttr + " IS NULL");

            String completeScript = "-- BEGIN " + command + "\n" +
                    leftTableAggregateSQL + "\n" +
                    rightTableAggregateSQL + "\n" +
                    leftJoinTableSQL + "\n" +
                    rightJoinTableSQL;

            script = completeScript;
            processingResult = new ProcessingResult(ProcessingResult.ResultType.TABLE, getTargetVariable().getTableName(), script);
            processingResult.setScript(script);

            // delete uninteresting temporary tables
            context.getDatabase().dropIfExistsViewOrTable(leftJoinTableName);
        }
        else {
            database.prepare("UPDATE " + leftAggregateTableName + " SET " + leftTableAggregateAttr + " = " + "'0' WHERE " + leftTableAggregateAttr + " IS NULL");
            script = "-- BEGIN " + command + "\n" + leftTableAggregateSQL + "--";
            processingResult = new ProcessingResult(ProcessingResult.ResultType.TABLE, leftAggregateTableName, script);
        }

        return processingResult;
    }
}
