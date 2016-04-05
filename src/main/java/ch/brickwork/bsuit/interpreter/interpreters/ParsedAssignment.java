package ch.brickwork.bsuit.interpreter.interpreters;




/**
 * Result of parsing a table assignment
 *
 * @author marcel
 */
public class ParsedAssignment {
    private String sqlString = null;

    /**
     * if the result is a parsed variable (existing as a variable, as opposed to
     * a native SQL query which does not yet exist as a variable), then
     * variableName is filled, otherwise it is null. If it is null, we assume
     * that the result is a native SQL statement.
     */
    private String variableName = null;

    public ParsedAssignment(final String sqlString, final String variableName)
    {
        this.sqlString = sqlString;
        this.variableName = variableName;
    }

    public String getSqlString()
    {
        return sqlString;
    }

    public String getVariableName()
    {
        return variableName;
    }
}
