package ch.brickwork.bsuit.interpreter.interpreters;

import ch.brickwork.bsuit.database.Variable;
import ch.brickwork.bsuit.globals.IBoilersuitApplicationContext;
import ch.brickwork.bsuit.interpreter.ScriptProcessor;
import ch.brickwork.bsuit.util.FileIOUtils;
import ch.brickwork.bsuit.util.TextUtils;

/**
 * <p>
 * Reads in a text file and interprets it line per line as a BoilerSuit script. Commands have to be separated by ; and
 * comments begin with --
 * <p/>
 * <h2>Syntax</h2>
 * <p class="syntax">
 * execute("filename");
 * </p>
 * <h2>Example</h2>
 * execute("scriptname.bs")
 * <p/>
 */
public class ExecuteExternalScriptInterpreter extends AbstractInterpreter {

    /**
     * stores target variable and command text (both are automatically trimmed) for interpreters
     *
     * @param targetVariable     is target variable
     * @param command            is command text
     * @param context                is Logger instance
     */
    public ExecuteExternalScriptInterpreter(final Variable targetVariable, final String command, final IBoilersuitApplicationContext context)
    {
        super(targetVariable, command, context);
    }

    @Override
    public boolean needsTargetVariable() { return false; }

    @Override
    public ProcessingResult process()
    {
        return processExecute(command);
    }

    @Override
    public boolean understands()
    {
        return command.toLowerCase().indexOf("execute") == 0;
    }

    /**
     * Execute subscript.
     * If input contains the valid file name or path to existing file, read it, next execute each line of the file and execute it as a BoilerSuit command (in the same way if enter was pressed at the end of the line).
     *
     * @param input is command text
     */
    private ProcessingResult processExecute(final String input) {
        String fileName = TextUtils.between(input, "(", ")");

        if (fileName == null || TextUtils.count(fileName, "\"") != 2) {
            return new ProcessingResult(ProcessingResult.ResultType.SYNTAX_ERROR, "Please use: execute(\"filename\")");
        }
        else
            fileName = fileName.trim().replaceAll("\\\"", "");

        ProcessingResult pr = new ProcessingResult();
        pr.setType(ProcessingResult.ResultType.COMPOSITE);

        final String completeFile = FileIOUtils.readCompleteFile(context.getWorkingDirectory(), fileName);
        if (completeFile != null) {
            for (String line : completeFile.split("\\r?\\n")) {
                if (line.trim().length() > 1 && !line.trim().substring(0, 2).equals("--")) {
                    ScriptProcessor sp = new ScriptProcessor(context);
                    pr.addSubResult(sp.processScript(line, null, null));
                }
            }
        } else {
            pr = new ProcessingResult(ProcessingResult.ResultType.SYNTAX_ERROR, "File indicated in " + input + " does not exist.");
        }

        return pr;
    }
}
