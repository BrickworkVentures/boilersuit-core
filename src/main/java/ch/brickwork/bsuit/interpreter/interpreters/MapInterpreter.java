package ch.brickwork.bsuit.interpreter.interpreters;

import ch.brickwork.bsuit.database.IDatabase;
import ch.brickwork.bsuit.database.Record;
import ch.brickwork.bsuit.database.Value;
import ch.brickwork.bsuit.database.Variable;
import ch.brickwork.bsuit.globals.IBoilersuitApplicationContext;
import ch.brickwork.bsuit.util.TextUtils;
import java.util.Hashtable;
import java.util.List;

/**
 * <p>
 *     To map all occurences of a value in a specific attribute of a table to a target value as defined
 *     in the mapping
 * <p/>
 * <h2>Syntax</h2>
 * <p class="syntax">
 * <b>MAP</b> <i>table</i><b>(</b><i>>attribute</i><b>)</b> <b>USE</b> <i>mapping</i>
 * </p>
 * <p>
 *     where mapping is a table with columns <i>source</i> and <i>target</i>
 * </p>
 *
 * <h2>Example</h2>
 * <pre>
 * MAP cars(cartype) USE mapping
 * </pre>
 * <p>
 * Mapping table should look like:
 * "source", "target"
 * "mercedes", "Boring_German_Car"
 * "lada", "Racing_Machine"
 * All occurrences of value 'mercedes' in <i>cartype</i>will be replaced with 'Boring_German_Car' and all occurrences of value
 * 'lada' will be replaced by 'Racing_Machine'.
 * </p>
 */
public class MapInterpreter extends AbstractInterpreter {

    private static final String TXT_VALUES_NOT_MAPPED = " values could not be mapped because of missing mapping. ";

    private static final String TXT_CHECK_FOR_DETAILS = "For details, check: ";

    private static final String TXT_MAPPING_TABLE_NOT_EXISTS = "Mapping table does not exist: ";

    private static final String TXT_MUST_CONTAIN_SOURCE_AND_TARGET = "Mapping table must contain source and target column (case matters)!";

    private static final String NUMBER_ATTRIBUTE_NAME = "number";

    private static final String PROBLEM_TABLE_NAME_FIRST_PART = "problems_map_";

    private static final String PROBLEM_TABLE_NAME_SECOND_PART = "_using_";

    private static final String SOURCE_ATTRIBUTE_NAME = "source";

    private static final String SYNTAX_ERROR_MESSAGE = "Syntax error. Usage: MAP originaltable(status_code) USE mapping_table_status_code";

    private static final String TARGET_ATTRIBUTE_NAME = "target";

    private final IDatabase database = context.getDatabase();

    private String mappingTable;

    private String originalTable;

    private String originalTableAttribute;

    /**
     * stores target variable and command text (both are automatically trimmed) for interpreters
     *
     * @param targetVariable     is target variable
     * @param command            is command text
     * @param context                is Logger instance
     */
    public MapInterpreter(final Variable targetVariable, final String command, final IBoilersuitApplicationContext context)
    {
        super(targetVariable, command, context);
    }

    /**
     * Updates columns from original table and replace the values matching to those from source column (from mapping table) with values from target column.
     * If the original table does not contains all off the necessary source values displays warning and creates a table named like 'problems_map...' contains
     * the numbers of values not found in the original table.
     *
     * @return processing results contains a message e.g.:
     * 'Warning: 1 values could not be mapped because of missing mapping. Check problems_map_cartype_using_maptable for details.'
     */
    @Override

    public ProcessingResult process()
    {
        final String targetVariableName = getTargetVariable().getVariableName();

        // parse in command to initialize originalTableToken and mappingTable:
        if (!parse()) {
            return new ProcessingResult(ProcessingResult.ResultType.SYNTAX_ERROR, SYNTAX_ERROR_MESSAGE);
        }

        if (!database.existsTable(mappingTable)) {
            return new ProcessingResult(ProcessingResult.ResultType.FATAL_ERROR,
               TXT_MAPPING_TABLE_NOT_EXISTS, mappingTable);
        }

        final List<String> mappingTableColumns = database.getTableOrViewColumnNames(mappingTable);
        if (!mappingTableColumns.contains(SOURCE_ATTRIBUTE_NAME) || !mappingTableColumns.contains(TARGET_ATTRIBUTE_NAME)) {
            return new ProcessingResult(ProcessingResult.ResultType.FATAL_ERROR, TXT_MUST_CONTAIN_SOURCE_AND_TARGET);
        }

        // check column attribute
        final Hashtable<String, Integer> tableOrViewColumnNamesHash = database.getTableOrViewColumnNamesHash(originalTable);
        if (null != tableOrViewColumnNamesHash && tableOrViewColumnNamesHash.get(originalTableAttribute) == null) {
            return new ProcessingResult(ProcessingResult.ResultType.SYNTAX_ERROR,
                "Table attribute " + originalTableAttribute + " not found in " + originalTable);
        }
        // copy table or view -> materializedOriginalTable
        String materializedOriginalTable;
        if (database.existsTableOrView(originalTable)) {
            materializedOriginalTable = database.createTempName();
            database.prepare("CREATE TABLE " + materializedOriginalTable + " AS SELECT * FROM " + originalTable);
        } else {
            return new ProcessingResult(ProcessingResult.ResultType.FATAL_ERROR, "Table or view does not exist: " + originalTable);
        }

        // create update statements
        final List<Record> mappingEntries = database.getAllRecordsFromTableOrView(mappingTable, null, null);
        String tableName;
        List<Record> orphanEntries;
        final String sql =
            "select " + originalTableAttribute + ", count(" + originalTableAttribute + ") as number from " + originalTable + " o left join " + mappingTable
                + " m on o." + originalTableAttribute + " = m." + SOURCE_ATTRIBUTE_NAME + " where m." + SOURCE_ATTRIBUTE_NAME + " is null group by "
                + originalTableAttribute;
        orphanEntries = database.prepare(sql);
        tableName = PROBLEM_TABLE_NAME_FIRST_PART + originalTableAttribute + PROBLEM_TABLE_NAME_SECOND_PART + mappingTable;
        final String[] columnNames = {originalTableAttribute, NUMBER_ATTRIBUTE_NAME};
        database.createOrReplaceVariableAndTable(tableName, "", "", columnNames, null);
        if (null != orphanEntries) {
            database.insert(tableName, orphanEntries);
        }

        if (mappingEntries == null) {
            return new ProcessingResult(ProcessingResult.ResultType.FATAL_ERROR, "Fatal error: Mapping table " + mappingTable + " could not be read");
        }

        String message;

        if(null != orphanEntries && orphanEntries.size() > 0) {
            message = orphanEntries.size() + TXT_VALUES_NOT_MAPPED + TXT_CHECK_FOR_DETAILS + tableName;
            return new ProcessingResult(ProcessingResult.ResultType.FATAL_ERROR, message, script);
        }

        message = "Applied " + mappingTable + " on " + originalTable + " for " + originalTableAttribute;
        final ProcessingResult processingResult = getProcessingResult(materializedOriginalTable, mappingEntries, message);

        // if target variable null, replace original table or view with mapped table
        if (getTargetVariable() == null) {
            final Variable tmp = database.getVariable(originalTable);
            database.dropIfExistsViewOrTable(originalTable);
            if (null != tmp) {
                database.createOrReplaceVariable(tmp.getVariableName(), "", tmp.getFileName());
            }
            database.prepare("ALTER TABLE " + materializedOriginalTable + " RENAME TO " + originalTable.toLowerCase());
        }
        // if not, the materialized view is the result:
        else {
            database.prepare("ALTER TABLE " + materializedOriginalTable + " RENAME TO " + targetVariableName.toLowerCase());

            processingResult.setType(ProcessingResult.ResultType.TABLE);
            processingResult.setResultSummary(targetVariableName);
        }

        processingResult.setScript(script);
        return processingResult;
    }

    @Override
    public boolean understands()
    {
        return command.toLowerCase().startsWith("map ");
    }

    private ProcessingResult getProcessingResult(String materializedOriginalTable, List<Record> mappingEntries, String message)
    {
        final ProcessingResult processingResult = new ProcessingResult(ProcessingResult.ResultType.MESSAGE, message);
        for (Record mappingEntry : mappingEntries) {
            final Value sourceValue = mappingEntry.getValue(SOURCE_ATTRIBUTE_NAME);
            final Value targetValue = mappingEntry.getValue(TARGET_ATTRIBUTE_NAME);

            final String updateStatement = ("UPDATE " + materializedOriginalTable + " SET " + originalTableAttribute + "='" + targetValue.getValue().toString()
                + "' WHERE " + originalTableAttribute + "='" + sourceValue.getValue().toString() + "'");
            final ProcessingResult subResult = new ProcessingResult(ProcessingResult.ResultType.MESSAGE, updateStatement);
            processingResult.addSubResult(subResult);

            context.getLog().info(
                "Mapping " + originalTable + "." + originalTableAttribute + " (" + sourceValue.getValue().toString() + "->" + targetValue.getValue().toString()
                    + ")"
            );
            database.prepare(updateStatement);
        }
        return processingResult;
    }

    /**
     * @return true if parsed successfully
     */
    private boolean parse()
    {
        // MAP originaltable(status_code) USE mapping_table_status_code
        final String originalTableTokenLowerCase = TextUtils.between(command.toLowerCase(), "map ", "use");
        if (originalTableTokenLowerCase == null) {
            return false;
        }

        int originalTableTokenStart = command.toLowerCase().indexOf(originalTableTokenLowerCase);
        int originalTableTokenEnd = originalTableTokenStart + originalTableTokenLowerCase.length();
        String originalTableToken = command.substring(originalTableTokenStart, originalTableTokenEnd);

        originalTableAttribute = TextUtils.between(originalTableToken, "(", ")");
        if (originalTableAttribute == null) {
            return false;
        }

        originalTable = TextUtils.between(originalTableToken, null, "(");
        if (originalTable == null) {
            return false;
        }

        mappingTable = TextUtils.between(command.toLowerCase(), "use ", null);
        return !(null == mappingTable || null == originalTable);
    }
}
