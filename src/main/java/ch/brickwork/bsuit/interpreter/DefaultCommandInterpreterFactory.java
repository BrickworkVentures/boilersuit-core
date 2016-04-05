package ch.brickwork.bsuit.interpreter;

import ch.brickwork.bsuit.database.Variable;
import ch.brickwork.bsuit.globals.IBoilersuitApplicationContext;
import ch.brickwork.bsuit.interpreter.interpreters.IInterpreter;
import ch.brickwork.bsuit.interpreter.util.ICommandInterpreter;
import ch.brickwork.bsuit.interpreter.util.ICommandInterpreterFactory;
import ch.brickwork.bsuit.util.ILog;

/**
 * Created by marcel on 06.08.15.
 */
public class DefaultCommandInterpreterFactory implements ICommandInterpreterFactory {
    @Override
    public ICommandInterpreter createCommandInterpreter(Variable targetVariable, String command, IBoilersuitApplicationContext context, String script) {
        return new DefaultCommandInterpreter(targetVariable, command, context);
    }
}
