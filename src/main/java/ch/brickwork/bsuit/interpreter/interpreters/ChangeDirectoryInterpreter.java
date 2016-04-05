package ch.brickwork.bsuit.interpreter.interpreters;

import ch.brickwork.bsuit.database.Variable;
import ch.brickwork.bsuit.globals.IBoilersuitApplicationContext;
import ch.brickwork.bsuit.util.FileIOUtils;
import org.apache.commons.lang3.StringUtils;
import java.io.File;
import java.nio.file.*;

/**
 * <p>
 *  Change or prints out current working directory
 * </p>
 * <p>
 *
 * </p>
 * <h2>Syntax</h2>
 * <p>
 *     CD {path};   -- to change directory
 *     CD;          -- to print directory
 * </p>
 * <h2>Special features</h2>
 * <p>
 *     Depending on <b>operating system,</b> syntax of path may vary. On Windows, backslash
 *     has been seen working; on linux slash.
 * </p>
 * <p>
 *     After (successful) change of working directory, Boilersuit will automatically
 *     open the data.db file as current database.
 * </p>
 */
public class ChangeDirectoryInterpreter extends AbstractInterpreter {

    private static final String CHANGE_DIRECTORY_COMMAND = "cd";

    private static final java.lang.String TXT_DIRECTORY_DOES_NOT_EXIST = "Directory does not exist!";

    private static final java.lang.String TXT_CURRENT_DIR_IS = "Current working directory is ";

    /**
     * stores target variable and command text (both are automatically trimmed) for interpreters
     *
     * @param targetVariable     is target variable
     * @param command            is command text
     * @param context                is Logger instance
     */
    public ChangeDirectoryInterpreter(final Variable targetVariable, final String command, final IBoilersuitApplicationContext context)
    {
        super(targetVariable, command, context);
    }

    @Override
    public boolean needsTargetVariable() { return false; }

    @Override
    public ProcessingResult process()
    {
        String pathString = command.replace(CHANGE_DIRECTORY_COMMAND, "").trim();

        // simply to print ("cd"):
        if(pathString.equals(""))
            return new ProcessingResult(ProcessingResult.ResultType.MESSAGE, context.getWorkingDirectory(), command);

        // to change (with path):
        StringUtils.replace(pathString, "\\\\|/", File.separator);
        if (pathString.endsWith("/") || pathString.endsWith("\\")) {
            pathString = pathString.substring(0, pathString.length() - 1);
        }
        final FileSystem fileSystem = FileSystems.getDefault();
        Path path;
        try {
            path = fileSystem.getPath(context.getWorkingDirectory() + File.separator + pathString);
        } catch (InvalidPathException e) {
            try {
                path = fileSystem.getPath(pathString);
            } catch (InvalidPathException ex) {
                return new ProcessingResult(ProcessingResult.ResultType.SYNTAX_ERROR, TXT_DIRECTORY_DOES_NOT_EXIST);
            }
        }
        if (!Files.exists(path)) {
            try {
                path = fileSystem.getPath(pathString);
            } catch (InvalidPathException ex) {
                return new ProcessingResult(ProcessingResult.ResultType.SYNTAX_ERROR, TXT_DIRECTORY_DOES_NOT_EXIST);
            }
        }
        if (!FileIOUtils.checkPath(path.toString())) {
            final String exceptionMessage = "You have no write and read access to file or directory: '" + path + "' or path doesn't exists!";
            return new ProcessingResult(ProcessingResult.ResultType.FATAL_ERROR, exceptionMessage, command);
        }
        else {
            if (context.changeDBFileDirectory(path.toString())) {
                context.setWorkingDirectory(path.toString());
                context.getLog().info("New current working directory: " + context.getWorkingDirectory());
                return new ProcessingResult(ProcessingResult.ResultType.MESSAGE, TXT_CURRENT_DIR_IS + path, script);
            }
        }
        return ProcessingResult.syntaxError("Please check if directory (" + path.toString() + ") exists!");
    }

    @Override
    public boolean understands()
    {
        return command.toLowerCase().startsWith(CHANGE_DIRECTORY_COMMAND);
    }
}
