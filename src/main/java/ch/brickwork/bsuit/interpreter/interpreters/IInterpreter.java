package ch.brickwork.bsuit.interpreter.interpreters;

import ch.brickwork.bsuit.database.Variable;

/**
 * Interface which should be implemented by all of BoilerSuit interpreters.
 * <p/>
 * User: marcel
 * Date: 5/31/13
 * Time: 10:43 AM
 */
public interface IInterpreter {

    /**
     * @return ProcessingResult
     */

    ProcessingResult process();

    /**
     * @return true if current interpreter understands the command
     */
    boolean understands();

    void setTargetVariable(Variable targetVariable);

    boolean needsTargetVariable();
}


