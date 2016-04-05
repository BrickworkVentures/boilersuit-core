package ch.brickwork.bsuit.interpreter.interpreters;

import ch.brickwork.bsuit.database.Record;
import ch.brickwork.bsuit.database.Variable;
import ch.brickwork.bsuit.globals.IBoilersuitApplicationContext;
import ch.brickwork.bsuit.interpreter.ScriptProcessor;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *     Compares a boilersuit expression against a value and takes
 *     a specific action if the assertion is not true.
 * </p>
 * <p>
 *     Any syntax or fatal error occurring during assertion will be handled as a
 *     fatal error and the script will be stopped. Assertion will be automatically
 *     false.
 * </p>
 * <h2>Syntax</h2>
 * <p class="syntax">
 *     <b>ASSERT</b> <i>{bs-expression}</i> <b>=</b> <i>{value}</i> <b>ELSE</b> <i>{action}</i>
 * <p/>
 * <p>
 *     <span class="syntax"><i>{action}</i></span> may be may be either <span class="syntax"><b>STOP</b></span> or <span class="syntax"><b>WARN</b></span>
 * </p>
 *
 * <h2>Special features</h2>
 * <ul>
 *     <li>If {bs-expression}'s result is a table or view, the first column of the first row will be compared to the expected value</li>
 * </ul>
 * <h2>Examples</h2>
 * <pre>
 * -- stop script if mytable has more or less than 3 rows
 * ASSERT #mytable = 3 ELSE STOP;
 * </pre>
 *  <pre>
 * -- the previous one is equivalent to...
 * ASSERT SELECT COUNT(*) FROM mytable = 3 ELSE STOP
 *  </pre>
 *  <pre>
 * -- ...but it could be anything (here we would check first col of first row):
 * ASSERT mytable = 3 ELSE STOP
 *  </pre>
 */
public class AssertInterpreter extends AbstractInterpreter {

    private static final String ASSERT_COMMAND = "assert ";

    private static final String ELSE_COMMAND = "else";

    private static final String TXT_ERROR_SYNTAXERROR = "assert [BoilerSuitCommand] = [Result] [Action];";

    /**
     * value delivered as "singleValue" in the processing result if the assertion is true
     */
    public static final String TRUE_SINGLE_VALUE = "true";

    /**
     * value delivered as "singleValue" in the processing result if the assertion is false
     */
    public static final String FALSE_SINGLE_VALUE = "false";


    protected static final String EQUALS_OPERATOR = "=";
    private String assertionInfo;
    private String boilerSuitCommand;
    private String expectedResult;
    private Action action;

    /**
     * stores target variable and command text (both are automatically trimmed) for interpreters
     *
     * @param targetVariable     is target variable
     * @param command            is command text
     * @param context                is Logger instance
     */
    public AssertInterpreter(final Variable targetVariable, final String command, final IBoilersuitApplicationContext context) {
        super(targetVariable, command, context);
    }

    @Override
    public boolean needsTargetVariable() { return false; }

    public List<IInterpreter> getInterpreters(String command) {
        final List<IInterpreter> interpreters = new ArrayList<>();
        interpreters.add(new CountInterpreter(getTargetVariable(), command, context));
        interpreters.add(new NativeSQLInterpreter(getTargetVariable(), command, context));
        interpreters.add(new TableExpressionInterpreter(getTargetVariable(), command, context));
        return interpreters;
    }

    @Override
    public ProcessingResult process() {
        ProcessingResult parseResult = parse();
        if(parseResult != null)
            return parseResult;

        return processEqualsOperator();
    }

    public ProcessingResult processEqualsOperator() {
        final List<IInterpreter> interpreters = getInterpreters(boilerSuitCommand);
        final ProcessingResult processingResult = new ScriptProcessor(context).processScript(boilerSuitCommand, interpreters, null);

        String realResult = "N/A";
        boolean assertionTrue;
        switch (processingResult.getType()) {
            case MESSAGE:
                realResult = processingResult.getSingleValue();
                if(realResult == null)
                    realResult = processingResult.getResultSummary();
                assertionTrue = compare(expectedResult, realResult);
                break;
            case COMPOSITE:
                context.getLog().warn("Assert cannot be used with composite results! We let it fail.");
                assertionTrue = false;
                break;
            case FATAL_ERROR:
                context.getLog().err("Fatal error during assertion (which will fail): " + assertionInfo + ". Error: " + processingResult.getResultSummary());
                assertionTrue = false;
                break;
            case SYNTAX_ERROR:
                context.getLog().err("Wrong syntax in assertion value " + boilerSuitCommand);
                assertionTrue = false;
                break;
            case TABLE:
            case VIEW:
                final List<Record> recordList = context.getDatabase().getAllRecordsFromTableOrView(processingResult.getResultSummary(), null, null);
                if (recordList != null) {
                    if (recordList.size() > 0) {
                        final Record record = recordList.get(0);
                        realResult = (String) record.getFirstValueContent();
                        assertionTrue = expectedResult.equals(realResult);
                    } else {
                        assertionTrue = false;
                        context.getLog().warn("Assert on table or view with empty set!");
                    }
                } else {
                    context.getLog().err("Assert on table or view but table or view not found!");
                    assertionTrue = false;
                }
                break;
            default:
                assertionInfo = "N/A";
                assertionTrue = false;
                context.getLog().err("Internal error - type of processing result not supported by assert interpreter");
        }

        if (assertionTrue) {
            context.getLog().info(assertionInfo + " PASSED");
            return new ProcessingResult(ProcessingResult.ResultType.MESSAGE, assertionInfo + " " + "PASSED" + " (result: " + realResult + ")", script, TRUE_SINGLE_VALUE);
        }

        // else
        context.getLog().info(assertionInfo + " FAILED");
        return performElseAction(assertionInfo + " " + "FAILED" + " (result: " + realResult + ")", action);
    }

    /**
     * pre-processes / parses the command.
     * post: command, assertionInfo, action, boilerSuitCommand, expectedResult fields are set correctly or error is returned
     * @return if error occurs, a syntax/fatal error ProcessingResult, if everything OK: null
     */
    protected ProcessingResult parse() {
        assertionInfo = command.replaceAll(";", "");

        /**
         * (?i) means case insensitive
         */
        command = command.replaceAll("(?i)" + ASSERT_COMMAND, "");
        command = command.replaceAll("(?i)" + ELSE_COMMAND, "");
        command = command.replaceAll(";", "");
        final int indexOfOperator = command.indexOf(EQUALS_OPERATOR);
        if (indexOfOperator == -1) {
            return ProcessingResult.syntaxError(TXT_ERROR_SYNTAXERROR);
        }
        boilerSuitCommand = command.substring(0, indexOfOperator).trim();
        action = null;
        for (Action actionEnum : Action.values()) {
            if (StringUtils.containsIgnoreCase(command, actionEnum.name())) {
                action = actionEnum;
                break;
            }
        }
        if (null == action) {
            return ProcessingResult.syntaxError(TXT_ERROR_SYNTAXERROR);
        }
        final int indexOfAction = StringUtils.indexOfIgnoreCase(command, action.name());
        if (indexOfAction == -1) {
            return ProcessingResult.syntaxError(TXT_ERROR_SYNTAXERROR);
        }
        expectedResult = command.substring(indexOfOperator + 1, indexOfAction).trim().replaceAll("'", "");

        return null;
    }

    private boolean compare(String result, String assumedResult) {
        return result.equals(assumedResult);
    }

    @Override
    public boolean understands() {
        return command.toLowerCase().startsWith(ASSERT_COMMAND);
    }


    private ProcessingResult performElseAction(String warning, Action action) {
        switch (action) {
            case WARN:
                context.getLog().warn(warning);
                return new ProcessingResult(ProcessingResult.ResultType.MESSAGE, warning, script, FALSE_SINGLE_VALUE);
            case STOP:
                return new ProcessingResult(ProcessingResult.ResultType.FATAL_ASSERT, warning, script, FALSE_SINGLE_VALUE);
        }
        return ProcessingResult.syntaxError();
    }

    private enum Action {
        WARN,
        STOP
    }
}
