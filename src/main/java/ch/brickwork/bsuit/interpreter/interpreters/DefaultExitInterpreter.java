package ch.brickwork.bsuit.interpreter.interpreters;

import ch.brickwork.bsuit.database.Variable;
import ch.brickwork.bsuit.globals.IBoilersuitApplicationContext;

/**
 * Closes the application.
 * <p/>
 * Usage:
 * exit
 * <p/>
 */
public class DefaultExitInterpreter extends AbstractInterpreter {

    /**
     * stores target variable and command text (both are automatically trimmed) for interpreters
     *
     * @param targetVariable     is target variable
     * @param command            is command text
     * @param context                is Logger instance
     */
    public DefaultExitInterpreter(final Variable targetVariable, final String command, final IBoilersuitApplicationContext context) {
        super(targetVariable, command, context);
    }

    @Override
    public boolean needsTargetVariable() { return false; }

    @Override
    public ProcessingResult process() {
        context.getDatabase().deleteTemporaryTables();
        System.exit(0);

        // formality
        return new ProcessingResult();
    }

    @Override
    public boolean understands() {
        // exit system
        return command.equals("exit");
    }
}
