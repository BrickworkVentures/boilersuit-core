package ch.brickwork.bsuit.interpreter;

import ch.brickwork.bsuit.globals.BoilerSuitGlobals;
import ch.brickwork.bsuit.globals.IBoilersuitApplicationContext;
import ch.brickwork.bsuit.interpreter.interpreters.IInterpreter;
import ch.brickwork.bsuit.interpreter.interpreters.ParsedAssignment;
import ch.brickwork.bsuit.interpreter.interpreters.ProcessingResult;
import ch.brickwork.bsuit.interpreter.util.ICommandInterpreter;
import ch.brickwork.bsuit.interpreter.util.ICommandInterpreterFactory;
import ch.brickwork.bsuit.util.FileIOUtils;
import ch.brickwork.bsuit.util.LogMessage;
import org.apache.commons.lang3.StringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Processes scripts of Boilersuit Commands. The commands must be separated by ';'
 *
 * @author marcel
 */
public class ScriptProcessor {

    private static final String COMMENT_START = "-- ";

    private static final String SQL_LOG_START_TEXT = "-- Boilersuit SQL Log.\n-- These are the SQL statements created during your last script run\n\n";

    private Future<LogMessage> swingWorker;

    private IBoilersuitApplicationContext context;

    private ICommandInterpreterFactory commandInterpreterFactory;

    private String scriptText;


    public ScriptProcessor() {
        this(new DefaultCommandInterpreterFactory(), BoilerSuitGlobals.getApplicationContext());
    }

    public ScriptProcessor(final IBoilersuitApplicationContext context) {
        this();
        this.context = context;
    }

    public ScriptProcessor(ICommandInterpreterFactory commandInterpreterFactory, final IBoilersuitApplicationContext context) {
        this.commandInterpreterFactory = commandInterpreterFactory;
        this.context = context;
    }

    /**
     * clients using swing will use this constructor, where the script processor will update the swing worker
     * with regular events depending on progress
     */
    public ScriptProcessor(ICommandInterpreterFactory commandInterpreterFactory, final IBoilersuitApplicationContext context, final Object swingWorker) {
        this(commandInterpreterFactory, context);
        this.swingWorker = (Future<LogMessage>) swingWorker;
    }

    /**
     * Processes ;-separated command blocks and comments.
     * Script text was taken from editor window and each token is split using new line character.
     *
     * @param scriptText      text which should contains a command valid with BoilerSuit syntax
     * @param alternativeText text which will be consider when scriptText won't be understand
     * @return the processing results contains summary of execution, e.g. error message if something went wrong.
     */
    public ProcessingResult processScript(String scriptText, List<IInterpreter> interpreters, String alternativeText) {
        this.scriptText = scriptText;

        ProcessingResult pr = new ProcessingResult();
        pr.setType(ProcessingResult.ResultType.COMPOSITE);
        pr.setResultSummary("Script Result");

        // clean from double ' ' and tabs
        while (scriptText.contains("  ")) {
            scriptText = scriptText.replaceAll("  ", " ");
        }
        scriptText = scriptText.replaceAll("\t", "");

        // if there is now ; at all - just assume it at the end:
        if (scriptText.indexOf(';') == -1) {
            scriptText += ';';
        }

        // also, simulate a) \n-- comments and b) xyz -- comments. a)
        final List<String> tokens = new ArrayList<>();
        parseTokens(scriptText, tokens);

        // if no tokens, error
        if (tokens.size() == 0) {
            return new ProcessingResult(ProcessingResult.ResultType.FATAL_ERROR, "Please use ending ';'. Command/script ignored.");
        }

        if (tokens.size() > 1) {
            for (final String command : tokens) {
                if (command.trim().length() > 0) {
                    final ProcessingResult subResult = processCommand(command, interpreters, alternativeText);
                    pr.addSubResult(subResult);
                    pr.setScript(command);

                    // but if it was an error, break here:
                    if (subResult.getType() == ProcessingResult.ResultType.SYNTAX_ERROR || subResult.getType() == ProcessingResult.ResultType.FATAL_ERROR || subResult.getType() == ProcessingResult.ResultType.FATAL_ASSERT) {
                        if (swingWorker != null)
                            swingWorker.cancel(true);

                        // note that in the context of a script, a syntax error is fatal
                        return new ProcessingResult(ProcessingResult.ResultType.FATAL_ERROR, subResult.getResultSummary());
                    }

                    // if was assertion with fatal (=stop) instruction, stop as well
                    if (subResult.getType() == ProcessingResult.ResultType.FATAL_ASSERT) {
                        if (swingWorker != null)
                            swingWorker.cancel(true);
                        return new ProcessingResult(ProcessingResult.ResultType.FATAL_ASSERT, "Script stopped due to false assertion in " + subResult.getResultSummary());
                    }
                }
            }
        } else {
            final String command = tokens.get(0);
            pr = processCommand(command, interpreters, alternativeText);
            pr.setScript(command);
        }

        // SQL
        createLogFile(pr);

        return pr;
    }

    /**
     * If processing result has sql statement, save it to a file.
     *
     * @param pr ProcessingResult used for looking a sql statements
     */
    private void createLogFile(ProcessingResult pr) {
        final StringBuilder builder = new StringBuilder();
        final String filePath = context.getWorkingDirectory() + "/" + "sqllog.txt";
        builder.append(SQL_LOG_START_TEXT);
        final List<ProcessingResult> subResults = pr.getSubResults();
        final StringBuilder partialBuilder = new StringBuilder();
//        TODO Should it going recursively to subresults?
        boolean isEmptySubresults = true;
        if (subResults != null && !subResults.isEmpty()) {
            for (final ProcessingResult subResult : subResults) {
                final ParsedAssignment pa = subResult.getSql();
                if (pa != null) {
                    partialBuilder.append(pa.getSqlString()).append("\n");
                    leftOrReplace(subResult);
                } else if (null != subResult.getScript()) {
                    partialBuilder.append(COMMENT_START).append(subResult.getScript().trim()).append("\n");
                    leftOrReplace(subResult);
                }
            }
            isEmptySubresults = partialBuilder.toString().isEmpty();
        }
        if (null != pr.getSql() && isEmptySubresults) {
            partialBuilder.append(pr.getSql().getSqlString()).append("\n");
            leftOrReplace(pr);
        } else if (null != pr.getScript() && isEmptySubresults) {
            partialBuilder.append(COMMENT_START).append(pr.getScript().trim()).append("\n");
            leftOrReplace(pr);
        }
        builder.append(scriptText);
        FileIOUtils.overwriteFile(filePath, builder.toString());
    }

    private void leftOrReplace(ProcessingResult pr) {
        final ParsedAssignment sql = pr.getSql();
        final String script = null != pr.getScript() ? pr.getScript().trim() : null;
        if (null != script && null != sql && scriptText.contains(script)) {
            scriptText = StringUtils.replaceOnce(scriptText, script, sql.getSqlString());
        } else if (null != script && !scriptText.contains(COMMENT_START + script)) {
            scriptText = StringUtils.replaceOnce(scriptText, script, COMMENT_START + script);
        }
    }

    /**
     * If text ends with new line character and is not a comment will be added to token list.
     *
     * @param scriptText text which will be parsed
     * @param tokens     list of found tokens
     */
    private void parseTokens(String scriptText, List<String> tokens) {
        boolean inString = false;
        String stringOpenerSymbol = null;
        boolean afterNewLine;
        boolean inComment = false;
        boolean escape = false;
        String currentToken = "";
        for (int i = 0; i < scriptText.length(); i++) {
            final char charAtI = scriptText.charAt(i);

            if (escape) {
                currentToken += interpretEscapeCharacter(charAtI);
                escape = false;
                continue;
            }
            if (!inComment && charAtI == '\\') {
                escape = true;
                continue;
            }

            // entering/leaving string using the " symbol:
            // we're not in comment, it's a ", and either we are not in a string yet (open!) or
            // if we are, we react only if the opener symbol was " (close!)
            if (!inComment && charAtI == '"' && (!inString || stringOpenerSymbol.equals("\""))) {
                inString = !inString;
                if(inString)    // we entered
                    stringOpenerSymbol = "" + charAtI;
                else
                    stringOpenerSymbol = null;
            }

            // entering/leaving string using the ' symbol:
            // we're not in comment, it's a ', and either we are not in a string yet (open!) or
            // if we are, we react only if the opener symbol was ' (close!)
            if (!inComment && charAtI == '\'' && (!inString || stringOpenerSymbol.equals("'"))) {
                inString = !inString;
                if(inString)    // we entered
                    stringOpenerSymbol = "" + charAtI;
                else
                    stringOpenerSymbol = null;
            }

            if (charAtI == '\n') {
                afterNewLine = true;
                inComment = false;
            } else {
                afterNewLine = false;
            }
            // if you encounter ';' and you're not in a comment, nor in a '', then this is the end
            // of the block:
            if (charAtI == ';' && !inString && !inComment) {
                tokens.add(currentToken.replaceAll("\n", ""));
                currentToken = "";
            }
            // first '-' in a row (just ignore)
            else //noinspection StatementWithEmptyBody
                if (!inString && (i < scriptText.length() - 1) && (charAtI == '-' && scriptText.charAt(i + 1) == '-')) {
                    // don't do anything yet, but don't use '-' for the command
                }
                // second  '-' in a row (ok, now we are IN the comment area)
                else if (!inString && (i > 0) && (charAtI == '-' && scriptText.charAt(i - 1) == '-')) {
                    // don't use '-' for the command and set comment mode
                    inComment = true;
                } else {
                    // use character as part of command, but only if you're not in comment
                    if (!inComment) {
                        // otherwise, if user does not use tab or something after new line,
                        // the line end will be glued to the new line's start and the command
                        // makes no sense
                        if (afterNewLine) {
                            currentToken += ' ';
                        }
                        currentToken += charAtI;
                    }
                }
        }
    }

    /**
     * interprets the character following the escape literal; for instance, if this char is 'n', we
     * will replace it by a "\n" literal. if it does not correspond to any currently supported
     * characters, we simply return the character itself
     */
    private String interpretEscapeCharacter(char charAtI) {
        if(charAtI == 't')
            return "\t";
        else if(charAtI == 'n')
            return "\n";
        else
            return "" + charAtI;
    }


    private ProcessingResult processCommand(final String input, List<IInterpreter> interpreters, String alternativeText) {
        ICommandInterpreter commandInterpreter = commandInterpreterFactory.createCommandInterpreter(null, input, context, input);
        if (null != alternativeText && !commandInterpreter.understands()) {
            commandInterpreter = commandInterpreterFactory.createCommandInterpreter(null, alternativeText, context, alternativeText);
        }
        commandInterpreter.setInterpreters(interpreters);
        if (commandInterpreter.understands()) {
            return commandInterpreter.process();
        } else {
            return new ProcessingResult(ProcessingResult.ResultType.FATAL_ERROR, "Cannot understand command " + input);
        }
    }
}
