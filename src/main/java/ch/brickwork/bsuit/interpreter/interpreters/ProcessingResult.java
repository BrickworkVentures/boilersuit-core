package ch.brickwork.bsuit.interpreter.interpreters;



import java.util.ArrayList;
import java.util.List;

/**
 * When commands interpreters finished its processing
 * this class is used to return the results
 * User: marcel
 * Date: 5/15/13
 * Time: 4:06 PM
 */
public class ProcessingResult {

    private static final String SYNTAX_ERROR_MSG = "Syntax error! ";

    private String resultSummary;

    private String singleValue;

    private String script;

    private ParsedAssignment sql;

    /**
     * a processing result may consist of subresults, ie. if a script with several commands is executed and
     * each command has one processing result
     */
    private List<ProcessingResult> subResults = null;

    private ResultType type;

    /**
     * creates default MESSAGE with message ""
     */
    public ProcessingResult()
    {
        this(ResultType.MESSAGE, "UNDEFINED RESULT!");
    }

    public ProcessingResult(final ResultType type, final String resultSummary)
    {
        this.type = type;
        setResultSummary(resultSummary);
    }

    public ProcessingResult(final ResultType type, final String resultSummary, final String command)
    {
        this.script = command;
        this.type = type;
        setResultSummary(resultSummary);
    }

    public ProcessingResult(ResultType type, String resultSummary, final String command, String singleValue) {
        this(type, resultSummary, command);
        setSingleValue(singleValue);
    }


    public static ProcessingResult syntaxError()
    {
        return new ProcessingResult(ResultType.SYNTAX_ERROR, "Syntax error! ");
    }

    /**
     * If message is null there is a default message
     *
     * @param message text of message
     *
     * @return ProcessingResult
     */

    public static ProcessingResult syntaxError(String message)
    {
        if (null != message) {
            return new ProcessingResult(ResultType.SYNTAX_ERROR, SYNTAX_ERROR_MSG + message);
        } else {
            return new ProcessingResult(ResultType.SYNTAX_ERROR, SYNTAX_ERROR_MSG);
        }
    }


    public String getResultSummary()
    {
        return resultSummary;
    }

    public void setResultSummary(final String resultSummary)
    {
        this.resultSummary = resultSummary;
    }


    public String getScript()
    {
        return script;
    }

    public void setScript(String script)
    {
        this.script = script;
    }


    public ParsedAssignment getSql()
    {
        return sql;
    }

    public void setSql(final ParsedAssignment sql)
    {
        this.sql = sql;
    }


    public String getSingleValue() {
        return singleValue;
    }

    public void setSingleValue(String singleValue) {
        this.singleValue = singleValue;
    }

    public List<ProcessingResult> getSubResults()
    {
        return subResults;
    }


    public ResultType getType()
    {
        return type;
    }

    public void setType(ResultType type)
    {
        this.type = type;
    }

    public void setSql(final String sql, final String variableName)
    {
        setSql(new ParsedAssignment(sql, variableName));
    }

    public void addSubResult(final ProcessingResult processingResult)
    {
        if (subResults == null) {
            subResults = new ArrayList<>();
        }
        subResults.add(processingResult);
    }

    /**
     * If Non-composite: if syntax error or fatal error
     * If composite: if at least one of the subresults isError, then the composite isError.
     */
    public boolean isError()
    {
        if (type == ResultType.COMPOSITE) {
            if (null != subResults) {
                for (ProcessingResult subResult : subResults) {
                    if (subResult.isError()) {
                        return true;
                    }
                }
            }
        }

        return type == ResultType.SYNTAX_ERROR || type == ResultType.FATAL_ERROR;
    }

    public enum ResultType {SYNTAX_ERROR, TABLE, FATAL_ERROR, MESSAGE, VIEW, COMPOSITE, FATAL_ASSERT}
}
