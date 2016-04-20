package ch.brickwork.bsuit.interpreter;

import ch.brickwork.bsuit.database.Variable;
import ch.brickwork.bsuit.globals.BoilerSuitGlobals;
import ch.brickwork.bsuit.globals.IBoilersuitApplicationContext;
import ch.brickwork.bsuit.interpreter.interpreters.*;
import ch.brickwork.bsuit.interpreter.util.ICommandInterpreter;
import ch.brickwork.bsuit.util.ILog;

import java.util.ArrayList;
import java.util.List;

/**
 * Main entry point for commands being processed. As an interpreter, the CommandInterpreter itself understands
 * only comments (beginning with --). For any other command, it asks all listed interpreters whether they understand.
 * <p/>
 * <p/>
 * User: marcel
 * Date: 5/31/13
 * Time: 10:35 AM
 */
public class DefaultCommandInterpreter extends AbstractInterpreter implements ICommandInterpreter {

    public static final String CORE_VERSION = "1.0.1 (210416)";

    private List<IInterpreter> interpreters = new ArrayList<>();

    private IInterpreter preferredInterpreter = null;

    public DefaultCommandInterpreter(final Variable targetVariable, final String command, final IBoilersuitApplicationContext context) {
        super(targetVariable, command, context);
    }

    /**
     * @return the list of interpreters used in application
     */
    protected List<IInterpreter> getInterpreters() {
        if (null == interpreters || interpreters.isEmpty())
            initInterpreters();
        return interpreters;
    }

    private void initInterpreters() {
        interpreters = new ArrayList<>();
        addCoreInterpreters();
        addCustomInterpreters();
        addGreedyInterpreters();
    }

    /**
     * override this to add custom interpreters
     */
    protected void addCustomInterpreters() {

    }

    private void addGreedyInterpreters() {
        // very simplistic pattern - should only be considered last:
        interpreters.add(new TableExpressionInterpreter(getTargetVariable(), command, context));

        // even greedier:
        interpreters.add(new VariableInterpreter(getTargetVariable(), command, context));
    }

    private void addCoreInterpreters() {
        interpreters.add(new AssertInterpreter(getTargetVariable(), command, context));
        interpreters.add(new DefinitionInterpreter(command, context));
        interpreters.add(new ExportInterpreter(getTargetVariable(), command, context));
        interpreters.add(new LeftOuterInterpreter(getTargetVariable(), command, context));
        interpreters.add(new CountInterpreter(getTargetVariable(), command, context));
        interpreters.add(new ExecuteExternalScriptInterpreter(getTargetVariable(), command, context));
        interpreters.add(new DefaultExitInterpreter(getTargetVariable(), command, context));
        interpreters.add(new ListFilesInterpreter(getTargetVariable(), command, context));
        interpreters.add(new ListVariablesInterpreter(getTargetVariable(), command, context));
        interpreters.add(new MatchInterpreter(getTargetVariable(), command, context));
        interpreters.add(new NativeSQLInterpreter(getTargetVariable(), command, context));
        interpreters.add(new StatisticsInterpreter(getTargetVariable(), command, context));
        interpreters.add(new FrequencyDifferenceInterpreter(getTargetVariable(), command, context));
        interpreters.add(new MapInterpreter(getTargetVariable(), command, context));
        interpreters.add(new TableModificationInterpreter(getTargetVariable(), command, context));
        interpreters.add(new ChangeDirectoryInterpreter(getTargetVariable(), command, context));
    }

    /**
     * add an interpreter. will be added second last in the list (last is always variable interpreter,
     * du to its "greedy" nature). Any interpreter added must have a pattern specific enough to not to
     * be confounded with the others.
     * @param interpreter
     */
    public void addInterpreter(IInterpreter interpreter) {
        interpreters.add(interpreter);
    }

    /**
     * replaces existing interpreter of this class by "interpreter" - at the same position
     * @param interpreterClass
     * @param interpreter
     */
    public void replaceInterpreter(Class interpreterClass, IInterpreter interpreter) {
        int i = 0;
        for(IInterpreter existingInterpreter : interpreters) {
            if(existingInterpreter.getClass().equals(interpreterClass))
                break;
            else
                i++;
        }

        if(i < interpreters.size()) {
            interpreters.remove(i);
            interpreters.add(i, interpreter);
        }
    }

    public void setInterpreters(List<IInterpreter> interpreters)
    {
        this.interpreters = interpreters;
    }

    @Override
    public String getCoreVersion() {
        return CORE_VERSION;
    }

    /**
     * If command is comment do nothing(only display a message).
     * Otherwise find interpreter which understands the command and let him take care of the rest.
     *
     * @return processing result prepared by found interpreter
     */

    @Override
    public ProcessingResult process() {
        // is it a comment
        if (command.length() > 1 && command.substring(0, 2).equals("--")) {
            return new ProcessingResult(ProcessingResult.ResultType.MESSAGE, "Comment: " + command);
        }

        // is a comment put to the end of the command? -> remove it
        command = command.replaceAll("\\-\\-.*", "");

        if (preferredInterpreter == null && !understands()) {
            getLog().err("Not found / Wrong Syntax: " + command);
            return new ProcessingResult(ProcessingResult.ResultType.FATAL_ERROR, "Not found / Wrong Syntax: " + command);
        }

        if(preferredInterpreter.needsTargetVariable() && getTargetVariable() == null) {
            String tempName = context.getDatabase().createTempName();
            preferredInterpreter.setTargetVariable(context.getDatabase().createOrReplaceVariable(tempName, "Temporary Variable", null));
        }

        ProcessingResult pr = preferredInterpreter.process();
        if(pr.isError())
            getLog().err(pr.getResultSummary());

        return pr;
    }

    /**
     * @return true if will be find an interpreter which understands the command
     */
    @Override
    public boolean understands() {
        // comments are handled here
        if (command.length() > 1 && command.substring(0, 2).equals("--")) {
            return true;
        }

        for (final IInterpreter interpreter : getInterpreters()) {
            if (interpreter.understands()) {
                preferredInterpreter = interpreter;
                return true;
            }
        }

        // no one understands...
        return false;
    }

    public ILog getLog() {
        return context.getLog();
    }

    public String getCommand() {
        return command;
    }
}
