package ch.brickwork.bsuit.interpreter.interpreters;

import ch.brickwork.bsuit.database.FileLoader;
import ch.brickwork.bsuit.database.IDatabase;
import ch.brickwork.bsuit.database.Variable;
import ch.brickwork.bsuit.globals.IBoilersuitApplicationContext;
import ch.brickwork.bsuit.interpreter.DefaultCommandInterpreter;
import ch.brickwork.bsuit.interpreter.util.WithClauseParser;
import ch.brickwork.bsuit.util.FileIOUtils;
import java.io.File;
import java.util.List;

/**
 * <p>
 * The definition interpreter is probably the most frequently used in any BS script; it is used for two purposes:
 * <ul>
 *     <li>copy the result of an expression to a table</li>
 *     <li>import a file into a table</li>
 * </ul>
 * </p>
 * <p>
 *     The expression can be on of the following:
 *     <ul>
 *         <li>the name of an other, existing table or view (which will be copied to the assignment table)</li>
 *         <li>the result of a <a href="#TableExpression">TableExpression</a>, which is a special short notation for certain SQL queries</li>
 *         <li>a native SQL query</li>
 *         <li>in the case of an import, a filename with or without wildcards in it</li>
 *     </ul>
 * </p>
 * <h2>Syntax</h2>
 * <p class="syntax">
 * <i>[new_table_name]</i><b>:=</b><i>filename</i> | <i>table-name</i> | <i>table-expression</i> | <i>native-sql-expression</i> | <i>wildcards</i> [with-clause]
 * </p>
 * <h3>WITH Clauses</h3>
 * <p>
 * <pre>WITH delim([delim])</pre> can be used to pre-define the delimitor. If not indicated, the delim is automatically recognized based on frequency analysis
 * </p>
 * <h2>Examples</h2>
 * <pre>
 * new_table_name := select * from existing_table_name;     -- sql
 * new_table_name := file.csv;                              -- import file
 * := file.csv;                                             -- import file, give table name automatically (will be file_csv)
 * := file*.csv;                                            -- import list of files
 * :=                                                       -- import all files in directory (eq to := *)
 * := file.csv WITH delim(',')                              -- import file.csv assuming the delimitor is ,
 * </pre>
 */
public class DefinitionInterpreter extends AbstractInterpreter {

    private static final java.lang.String TXT_MORE_THAN_ONE = "You're trying assign more than one file to a single variable!";
    private static final java.lang.String TXT_IMPORT_SUCCESS = "Imported successfully!";
    private static final java.lang.String TXT_IMPORT_FAILURE = "Import failure! File was empty or invalid.";
    private static final java.lang.String TXT_SYNTAX_ERROR = "Syntax error. Usage: new_table_name := filename | wildcards | table-expression | native-sql-expression | [*]";

    private final IDatabase database = context.getDatabase();

    private String encoding;

    private String delim;

    public DefinitionInterpreter(final String command, final IBoilersuitApplicationContext context)
    {
        super(null, command, context);
    }

    /**
     * Imports file containing csv data to a table or creates new table using other existing table.
     * Can handles with single file or more ones using a wildcard (*).
     * File should have a .csv extension.
     *
     * @return processing result contains the message whether imported successfully or not.
     */
    @Override
    public ProcessingResult process() {
        final String[] tokens = command.split(":=");
        final int tokensLength = tokens.length;

        if(tokensLength < 1 && !command.trim().equals(":=")) // simply := alone is ok
            return new ProcessingResult(ProcessingResult.ResultType.SYNTAX_ERROR, TXT_SYNTAX_ERROR);

        // get variable (left)
        String varName = "";
        if(tokens != null && tokens.length >= 1)
            varName = tokens[0].trim();

        // get right one, or if empty, replace with *
        final String assignment = tokensLength > 1 ? tokens[1].trim() : "*";

        // for file import def., assignement without WITH clause will be used (file filter!), for
        // all other, assignment (raw, with WITH clause) will be used. The principle is that each interpreter
        // handles the WITH clause independently, which is why "preProcessWith" does only the DefinitionInterpreter
        // specific parsing, whereas for other interpreters, we would just pass on the raw assignement including
        // their with clause
        String assignmentWithoutWith = preProcessWith(assignment);

        final File[] files = FileIOUtils.getFiles(context.getWorkingDirectory(), assignmentWithoutWith);
        final int filesLength = files.length;

        // more than 2 tokens...strange!:
        if (tokens.length > 2) {
            context.getLog().err("Syntax error. Check manual.");
            return new ProcessingResult(ProcessingResult.ResultType.SYNTAX_ERROR, "You should have only one := in your statement!");
        }

        // more than one files and varName is given
        if (filesLength > 1 && !varName.isEmpty()) {
            context.getLog().err("Syntax error. Check manual.");
            return new ProcessingResult(ProcessingResult.ResultType.SYNTAX_ERROR, TXT_MORE_THAN_ONE);
        }

        // try files first
        String resultVariableName = null;
        for (final File file : files) {

            final String fileName = file.getName();

            if (filesLength != 1 || varName.isEmpty()) {
                varName = fileName.replaceAll("\\.[a-zA-Z0-9]+", "").replaceAll("\\W", "_");
            }

            context.getDatabase().createOrReplaceVariable(varName, fileName, fileName);
            resultVariableName = loadFile(varName, file, fileName, encoding);
        }

        if (filesLength == 1 && null == resultVariableName) {
            return new ProcessingResult(ProcessingResult.ResultType.MESSAGE, TXT_IMPORT_FAILURE);
        }

        if (filesLength > 0) {
            return new ProcessingResult(ProcessingResult.ResultType.MESSAGE, TXT_IMPORT_SUCCESS, script);
        } else {
            // neither - process right-hand side (whatever it is...):
            Variable var = context.getDatabase().createOrReplaceVariable(varName, assignment, null);
            final DefaultCommandInterpreter commandInterpreter = new DefaultCommandInterpreter(var, assignment, context);
            return commandInterpreter.process();
        }
    }

    /**
     * we are creating our own variables depending on the variable part of the command
     * @return
     */
    @Override
    public boolean needsTargetVariable() {
        return false;
    }

    private String preProcessWith(String assignment) {
        WithClauseParser wcp;
        int indexOfWITH = assignment.toLowerCase().lastIndexOf(" with ");
        if (indexOfWITH != -1) {
            wcp = new WithClauseParser(assignment.substring(indexOfWITH));
            if (wcp.parse()) {
                List<String> encodings = wcp.getArgumentsIgnoreCase("encoding");
                if(encodings == null)
                    encoding = null;
                else
                    encoding = encodings.get(0);

                if(wcp.getArgumentsIgnoreCase("delim") != null)
                    delim = wcp.getArgumentsIgnoreCase("delim").get(0);
            }

            // cut off parameters for further processing
            return assignment.substring(0, indexOfWITH);
        }
        else
        return assignment;
    }

    @Override
    public boolean understands()
    {
        return command.contains(":=");
    }

    /**
     * @param varName  name of Variable where we want to save imported file
     * @param file     is a file which we try to import
     * @param fileName name of file which we try to import, for now we import only files end with .csv or .txt
     *
     * @param encoding
     * @return name of Variable where imported file will be saved
     */
    private String loadFile(String varName, File file, String fileName, String encoding)
    {
        context.getLog().info("Reading " + fileName + " as " + varName + "...");

        // find the file extension (after .)
        // since files may be named like ABCD.1.2.csv, ABCD., ABCD, ABCD.1.2., we will walk forward
        // until we reach the last chunk. Files with '.' at the end will be interpreted like
        // files without a '.'
        String remainingFileName = fileName;
        String extension = "";
        while (remainingFileName.contains(".")) {
            // is '.' at end (e.g., ABCD.)
            if (remainingFileName.indexOf(".") == remainingFileName.length() - 1) {
                extension = "";
                remainingFileName = ""; // will break the loop
            }

            // no. -> cut after '.' and go on
            else {
                remainingFileName = remainingFileName.substring(remainingFileName.indexOf(".") + 1);
                extension = remainingFileName;
            }
        }

        // if understandable by file importer then import
        String resultVariableName = null;
        if (file.isFile()) {
            FileLoader fl = new FileLoader(database, context);
            resultVariableName = fl.loadFile(varName, "", file, encoding, delim);
        }
        return resultVariableName;
    }
}
