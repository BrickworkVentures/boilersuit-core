package ch.brickwork.bsuit.interpreter.interpreters;

import ch.brickwork.bsuit.database.IDatabase;
import ch.brickwork.bsuit.database.Record;
import ch.brickwork.bsuit.database.Variable;
import ch.brickwork.bsuit.globals.IBoilersuitApplicationContext;
import ch.brickwork.bsuit.util.TextUtils;
import java.util.List;

/**
 * <p>BoilerSuit understands a subset of the native SQL as long as it is understood by the underlying database system.</p>
 * <p>Currently, the following commands are supported:
 *
 *     <ul>
 *         <li>SELECT</li>
 *         <li>CREATE TABLE [AS]</li>
 *         <li>DELETE</li>
 *         <li>INSERT</li>
 *         <li>UPDATE</li>
 *     </ul>
 *     </p>
 * <h2>Syntax</h2>
 * <p class="syntax">[result :=] sql-statement</p>
 * <h2>Samples</h2>
 * <pre>SELECT * FROM mytable;</pre>
 * <pre>myvariable := SELECT * FROM mytable WHERE x = 2;</pre>
 */
public class NativeSQLInterpreter extends AbstractInterpreter {

    private static final String CREATE_TABLE_AS_REGEX = "create table [\\d\\w]+ as select .+";

    private static final String CREATE_TABLE_REGEX = "create table [\\d\\w]+.+";

    private static final String DELETE_REGEX = "delete.+from.+";

    private static final String INSERT_VALUES = "insert into.+";

    private static final String UPDATE_REGEX = "update.+set.+";

    private static final String DROP_REGEX = "drop.+table.+";

    private static final String TXT_TABLE_ALREADY_EXISTS = "Table already exists!";

    private IDatabase database = context.getDatabase();

    /**
     * stores target variable and command text (both are automatically trimmed) for interpreters
     *
     * @param targetVariable     is target variable
     * @param command            is command text
     * @param context                is Logger instance
     */
    public NativeSQLInterpreter(final Variable targetVariable, final String command, final IBoilersuitApplicationContext context)
    {
        super(targetVariable, command, context);
    }

    /**
     * target variable only needed in case of select statement - we create it there by ourselves
     */
    @Override
    public boolean needsTargetVariable() { return false; }

    /**
     * Executes a native sql command.
     *
     * @return A ProcessingResult VIEW type, if successful (exception: if CREATE TABLE ... AS is used, a TABLE type is returned), or an error if not successful.
     */
    @Override
    public ProcessingResult process()
    {
        final ProcessingResult processingResult = new ProcessingResult();
        context.getLog().info("Calculate...");

        if (command.trim().toLowerCase().matches(CREATE_TABLE_AS_REGEX)) {
            processCreateTableAsCommand(processingResult);
        } else if (command.trim().toLowerCase().matches(CREATE_TABLE_REGEX)) {
            context.getDatabase().prepare(command);
            ProcessingResult pr = new ProcessingResult(ProcessingResult.ResultType.MESSAGE, "Table created.", script);
            pr.setSql(new ParsedAssignment(command, null));
            return pr;
        } else if (command.trim().toLowerCase().matches(INSERT_VALUES)) {
            context.getDatabase().prepare(command);
            ProcessingResult pr = new ProcessingResult(ProcessingResult.ResultType.MESSAGE, "Row inserted.", script);
            pr.setSql(new ParsedAssignment(command, null));
            return pr;
        } else if (command.trim().toLowerCase().matches(UPDATE_REGEX)) {
            database.prepare(command);
            final ProcessingResult pr = new ProcessingResult(ProcessingResult.ResultType.MESSAGE, "Update processed.", script);
            pr.setSql(new ParsedAssignment(command, null));
            return pr;
        } else if (command.trim().toLowerCase().matches(DELETE_REGEX)) {
            database.prepare(command);
            final ProcessingResult pr = new ProcessingResult(ProcessingResult.ResultType.MESSAGE, "Delete processed.", script);
            pr.setSql(new ParsedAssignment(command, null));
            return pr;
        } else if(command.toLowerCase().matches(DROP_REGEX)) {
            database.prepare(command);
            final ProcessingResult pr = new ProcessingResult(ProcessingResult.ResultType.MESSAGE, "Drop processed.", script);
            pr.setSql(new ParsedAssignment(command, null));
            context.getLog().warn("You should use the minus (-) operator to drop tables in BS! Hope you know what you're doing");
            return pr;
        } else if(command.toLowerCase().indexOf("select ") == 0) {
            // create temporary target var if needed
            String targetName;
            if(getTargetVariable() == null)
                targetName = database.createTempName();
            else
                targetName = getTargetVariable().getVariableName();

            database.createOrReplaceVariable(targetName, command, null);
            database.prepare("CREATE TABLE " + targetName + " AS " + command);
            final ProcessingResult pr = new ProcessingResult(ProcessingResult.ResultType.VIEW, targetName);
            pr.setSql(new ParsedAssignment(command, null));
            return pr;
        }
        else {
            createTableOrView(processingResult);
        }
        return processingResult;
    }

    @Override
    public boolean understands()
    {
        return command.toLowerCase().indexOf("select ") == 0 ||
            command.toLowerCase().indexOf("insert ") == 0 ||
            command.toLowerCase().indexOf("drop ") == 0 ||
            command.toLowerCase().matches(UPDATE_REGEX) ||
            command.toLowerCase().matches(DELETE_REGEX) ||
            command.toLowerCase().matches(CREATE_TABLE_AS_REGEX) ||
            command.toLowerCase().matches(CREATE_TABLE_REGEX) ||
            command.toLowerCase().matches((INSERT_VALUES));
    }

    @SuppressWarnings("ConstantConditions")
    private void createTableOrView(ProcessingResult processingResult)
    {
        final String temporaryTargetVariableName = getTargetVariable().getVariableName() + "_tmp";
        final String sql = "CREATE TABLE " + temporaryTargetVariableName + " AS " + command;
        processingResult.setSql(sql, getTargetVariable().getVariableName());
        processingResult.setScript(script);
        final List<Record> result = database.prepare(sql);
        if (result != null) {
            database.renameTableName(temporaryTargetVariableName, getTargetVariable().getVariableName());
            processingResult.setResultSummary(Variable.getTableName(getTargetVariable().getVariableName()));
            processingResult.setType(ProcessingResult.ResultType.TABLE);
        } else {
            processingResult.setResultSummary("Illegal SQL statement - no result");
            processingResult.setType(ProcessingResult.ResultType.SYNTAX_ERROR);
        }
    }

    private void processCreateTableAsCommand(ProcessingResult processingResult)
    {
        String sql = command.trim();
        String createTableAsTarget = TextUtils.between(command.toLowerCase(), "create table ", " as ");
        boolean tableExists = false;
        if (null != createTableAsTarget) {
            tableExists = database.existsTableOrView(createTableAsTarget);
        }
        if (createTableAsTarget == null) {
            processingResult.setResultSummary("Illegal syntax for create table as, could not parse table name");
            processingResult.setType(ProcessingResult.ResultType.SYNTAX_ERROR);
        } else if (tableExists) {
            processingResult.setResultSummary(TXT_TABLE_ALREADY_EXISTS);
            processingResult.setType(ProcessingResult.ResultType.SYNTAX_ERROR);
        } else {
            database.createOrReplaceVariable(createTableAsTarget, "", "");
            createTableAsTarget = createTableAsTarget.toLowerCase();
            sql = sql.toLowerCase();
            processingResult.setSql(sql, createTableAsTarget);
            processingResult.setScript(script);
            final List<Record> result = database.prepare(sql);
            if (result != null) {
                processingResult.setResultSummary(createTableAsTarget);
                processingResult.setType(ProcessingResult.ResultType.TABLE);
            } else {
                processingResult.setResultSummary("Illegal SQL statment - no result");
                processingResult.setType(ProcessingResult.ResultType.SYNTAX_ERROR);
            }
        }
    }
}
