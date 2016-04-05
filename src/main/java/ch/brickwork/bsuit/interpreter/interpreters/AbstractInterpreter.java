package ch.brickwork.bsuit.interpreter.interpreters;

import ch.brickwork.bsuit.database.Variable;
import ch.brickwork.bsuit.globals.IBoilersuitApplicationContext;

/**
 * Base class of an interpreter. Interpreters handle any command entered into Boilersuit or part
 * of a BS script.<br/>
 * User: marcel
 * Date: 5/31/13
 * Time: 10:44 AM
 */
public abstract class AbstractInterpreter implements IInterpreter {

    protected final IBoilersuitApplicationContext context;

    /**
     * boilersuit command handled by this interpreter
     */
    protected String command;


    protected String script;

    private Variable targetVariable;

    /**
     * stores target variable and command text (both are automatically trimmed) for interpreters
     *
     * @param command            is command text
     * @param context                is Logger instance
     */

    public AbstractInterpreter(final Variable targetVariable, final String command, final IBoilersuitApplicationContext context)
    {
        this.targetVariable = targetVariable;
        this.command = command.trim();
        this.context = context;
    }

    /**
     * if the result achieved by the interpreter's process can be formulated using an SQL script,
     * then the interpreter should set this here. This will be used by the SQL log
     */
    public void setScript(String script) {
        this.script = script;
    }

    /**
     * get the SQL representation of this interpreter's action
     * @return SQL script or null
     */
    public String getScript() {
        return script;
    }

    /**
     * most interpreters need a variable. those who don't, please overwrite this method and return false
     */
    public boolean needsTargetVariable() { return true; }

    /**
     * set the target variable. this is used by interpreters that do delegate processing but know
     * what the target variable will be, e.g. the definition interpreter handling x := ...
     * will delegate the ... but set the variable to x
     */
    public void setTargetVariable(Variable targetVariable) { this.targetVariable = targetVariable; }

    /**
     * get target variable for this interpreter's action - i.e. the interpreters should write their result
     * into this variable
     */
    protected Variable getTargetVariable() {
        return targetVariable;
    }
}
