package ch.brickwork.bsuit.interpreter.interpreters;

import ch.brickwork.bsuit.database.Variable;
import ch.brickwork.bsuit.globals.IBoilersuitApplicationContext;
import ch.brickwork.bsuit.util.ILog;

/**
 * <p>Lists all tables</p>
 * <h2>Syntax</h2>
 * <p class="syntax">?</p>
 */
public class ListVariablesInterpreter extends AbstractInterpreter {

    /**
     * stores target variable and command text (both are automatically trimmed) for interpreters
     *
     * @param targetVariable is target variable
     * @param command            is command text
     * @param context                is Logger instance
     */
    public ListVariablesInterpreter(final Variable targetVariable, final String command, final IBoilersuitApplicationContext context)
    {
        super(targetVariable, command, context);
    }

    @Override
    public boolean needsTargetVariable() { return false; }

    @Override

    public ProcessingResult process()
    {
        context.getLog().info("Check output table for overview.");
        final ProcessingResult pr = new ProcessingResult(ProcessingResult.ResultType.TABLE, "variables", script);
        pr.setSql(new ParsedAssignment("SELECT * FROM VARIABLES", "variables"));
        return pr;
    }

    @Override
    public boolean understands()
    {
        return command.equals("?");
    }
}
