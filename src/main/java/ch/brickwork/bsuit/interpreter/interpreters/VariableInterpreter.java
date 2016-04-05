package ch.brickwork.bsuit.interpreter.interpreters;

import ch.brickwork.bsuit.database.IDatabase;
import ch.brickwork.bsuit.database.Variable;
import ch.brickwork.bsuit.globals.IBoilersuitApplicationContext;

/**
 * Displays the records from selected variable (variables are connected with tables).
 * <p/>
 * Usage:
 * variable_name
 * <p/>
 */
public class VariableInterpreter extends AbstractInterpreter {

    /**
     * stores target variable and command text (both are automatically trimmed) for interpreters
     */
    public VariableInterpreter(final Variable targetVariable, final String command, final IBoilersuitApplicationContext context)
    {
        super(targetVariable, command, context);
    }

    /**
     * we are already a variable
     * @return
     */
    @Override
    public boolean needsTargetVariable() {
        return false;
    }

    @Override
    public ProcessingResult process()
    {
        // exists?
        final IDatabase database = context.getDatabase();
        if (database.existsTableOrView(command)) {
            String resultVariableName = command;

            // if target variable was set (most likely after the user used a variable as a right side of a defintion,
            // we create a new table:
            if(getTargetVariable() != null) {
                resultVariableName = getTargetVariable().getTableName();
                context.getDatabase().prepare("CREATE TABLE " + resultVariableName + " AS SELECT * FROM " + command);
            }

            // if not (most likely after the user simply typed in the var name in the console to view values):
            final ProcessingResult processingResult = new ProcessingResult(ProcessingResult.ResultType.VIEW, command);
            processingResult.setSql(new ParsedAssignment("SELECT * FROM " + command, null));

            return processingResult;
        } else {
            return new ProcessingResult(ProcessingResult.ResultType.FATAL_ERROR, "Could not find variable " + command);
        }
    }

    @Override
    public boolean understands()
    {
        return command.matches("[A-Za-z0-9_]+");
    }
}
