package ch.brickwork.bsuit.interpreter.util;

import ch.brickwork.bsuit.database.Variable;
import ch.brickwork.bsuit.globals.IBoilersuitApplicationContext;

/**
 * Created by marcel on 06.08.15.
 */
public interface ICommandInterpreterFactory {
    ICommandInterpreter createCommandInterpreter(Variable targetVariable, String command, IBoilersuitApplicationContext context, String script);
}
