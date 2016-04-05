package ch.brickwork.bsuit.interpreter.interpreters;

import ch.brickwork.bsuit.database.Variable;
import ch.brickwork.bsuit.globals.IBoilersuitApplicationContext;
import ch.brickwork.bsuit.util.FileIOUtils;
import java.io.File;

/**
 * <p>Displays a list of files in the working directory.</p>
 *
 * <h2>Syntax</h2>
 * <p class="syntax">ls</p>
 *
 * <h2>Examples</h2>
 * <pre>
 * ls
 * </pre>
 * <pre>
 * ls *.csv
 * </pre>
 * <p/>
 */
public class ListFilesInterpreter extends AbstractInterpreter {

    public final static String TXT_NO_RESULTS = "No results";

    /**
     * stores target variable and command text (both are automatically trimmed) for interpreters
     *
     * @param targetVariable     is target variable
     * @param command            is command text
     * @param context                is Logger instance
     */
    public ListFilesInterpreter(final Variable targetVariable, final String command, final IBoilersuitApplicationContext context)
    {
        super(targetVariable, command, context);
    }

    @Override
    public boolean needsTargetVariable() { return false; }

    /**
     * @return the message contains list of file names from working directory
     */
    @Override
    public ProcessingResult process()
    {
        final ProcessingResult processingResult = new ProcessingResult(ProcessingResult.ResultType.COMPOSITE, "Files");
        final String[] arguments = command.split(" ");
        final String filter = arguments.length > 1 ? arguments[1] : "*";
        final File[] files = FileIOUtils.getFiles(context.getWorkingDirectory(), filter);
        for (final File file : files) {
            processingResult.addSubResult(new ProcessingResult(ProcessingResult.ResultType.MESSAGE, file.getName()));
        }
        if (files.length == 0) {
            processingResult.addSubResult(new ProcessingResult(ProcessingResult.ResultType.MESSAGE, TXT_NO_RESULTS));
        }
        processingResult.setScript(script);
        return processingResult;
    }

    @Override
    public boolean understands()
    {
        return command.toLowerCase().startsWith("ls");
    }
}
