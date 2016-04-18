package ch.brickwork.bsuit.interpreter.interpreters;

import ch.brickwork.bsuit.database.Variable;
import ch.brickwork.bsuit.globals.IBoilersuitApplicationContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>
 *   Short notations for Creating/Dropping a table
 *  </p>
 * <h2>Syntax</h2>
 * Create Table
 * <p class="syntax">
 * +tablename([!primary_key], attribute_1, ..., attribue_n);
 * </p>
 * Drop Table
 * <p class="syntax">
 * -tablename;
 * </p>
 * <h2>Examples</h2>
 *   <pre>
 * +mytable(!id, attribute1, attribute2); -- create
 * -mytable; -- drop
 *  </pre>
 *</p>
 */
public class TableModificationInterpreter extends AbstractInterpreter {

    private static final String TXT_CREATED_SUCCESSFULLY = "Created: ";

    private static final String TXT_TABLE_DOES_NOT_EXIST = "Table does not exist!";

    private static final String TXT_DROPPED_SUCCESSFULLY = "Dropped: ";

    private static final String TXT_DROP_ERROR = "Could not be dropped: ";

    private static final String TXT_TABLE_ALREADY_EXISTS = "Table already exists!";

    private static final String TXT_SYNTAX_ERROR = "Don't understand";

    private static final String TXT_DROP_NO_MATCHES = "No matches: ";

    private static final String DROP_TABLE_COMMAND = "-";

    private static final String AUTO_PK_SUFFIX = "id";

    private static final String CREATE_TABLE_COMMAND = "+";

    private static final String WILDCARD = "*";

    private static final String PRIMARY_KEY_PREFIX = "!";

    /**
     * stores target variable and command text (both are automatically trimmed) for interpreters
     *
     * @param targetVariable is target variable
     * @param command        is command text
     * @param context            is Logger instance
     */
    public TableModificationInterpreter(final Variable targetVariable, final String command, final IBoilersuitApplicationContext context) {
        super(targetVariable, command, context);
    }

    @Override
    public boolean needsTargetVariable() { return false; }

    @Override
    public ProcessingResult process() {
        if (command.startsWith(CREATE_TABLE_COMMAND)) {
            return createTable();
        } else if (command.startsWith(DROP_TABLE_COMMAND)) {
            return dropTable();
        }
        return new ProcessingResult(ProcessingResult.ResultType.FATAL_ERROR, TXT_SYNTAX_ERROR);
    }

    @Override
    public boolean understands() {
        return command.startsWith(CREATE_TABLE_COMMAND) || command.startsWith(DROP_TABLE_COMMAND);
    }

    /**
     * Perform CREATE TABLE statement
     * Syntax:
     * +mytable(*att1, *att2, …, att_n)
     * <p>
     * SQL Triggered:
     * CREATE TABLE mytable (
     * att1 VARCHAR(1024),
     * att2 VARCHAR(1024),
     * …
     * att_n VARCHAR(1024),
     * PRIMARY KEY(att1, att2)
     * );
     * <p>
     * Syntax:
     * +mytable(*att1, att2, …, att_n)
     * <p>
     * SQL Triggered:
     * CREATE TABLE mytable (
     * att1 VARCHAR(1024),
     * att2 VARCHAR(1024),
     * …
     * att_n VARCHAR(1024),
     * PRIMARY KEY(att1)
     * );
     * Syntax:
     * +mytable(att1, att2, …, att_n)
     * <p>
     * SQL Triggered:
     * CREATE TABLE mytable (
     * mytableid INTEGER,
     * att1 VARCHAR(1024),
     * att2 VARCHAR(1024),
     * …
     * att_n VARCHAR(1024),
     * PRIMARY KEY(mytableid)
     * );
     * <p>
     * If ‘mytableid’ already exists, make it the
     * primary key, but do not add it again as an
     * attribute.
     *
     * @return execution summary
     */
    private ProcessingResult createTable() {
        int startBracket = command.indexOf("(");
        int endBracket = command.indexOf(")");
        String tableName = command.substring(1, startBracket);
        String argumentsAsString = command.substring(startBracket + 1, endBracket);
        List<String> arguments = new ArrayList<>(Arrays.asList(argumentsAsString.split(",")));
        Iterator<String> iterator = arguments.iterator();
        while (iterator.hasNext()) {
            if (!isTextContainsLetters(iterator.next())) {
                iterator.remove();
            }
        }
        List<String> primaryKeys = new ArrayList<>();
        List<String> primaryKeysTmp = new ArrayList<>();
        int idx = 0;
        for (String columnName : arguments) {
            columnName = columnName.trim();
            if (columnName.startsWith(PRIMARY_KEY_PREFIX)) {
                columnName = columnName.replace(PRIMARY_KEY_PREFIX, "");
                arguments.set(idx, columnName);
                primaryKeys.add(columnName);
            } else if (columnName.equals(tableName + AUTO_PK_SUFFIX)) {
                primaryKeysTmp.add(columnName);
            }
            idx++;
        }
        if (primaryKeys.isEmpty() && !primaryKeysTmp.isEmpty()) {
            primaryKeys.addAll(primaryKeysTmp);
        }
        if (primaryKeys.isEmpty()) {
            String pkColumn = tableName + AUTO_PK_SUFFIX;
            primaryKeys.add(pkColumn);
            arguments.add(pkColumn);
        }
        Variable variable = context.getDatabase()
                .createOrReplaceVariableAndTable(tableName, tableName, tableName, arguments.toArray(new String[arguments.size()]),
                        primaryKeys.toArray(new String[primaryKeys.size()]));
        if (null != variable) {
            final ProcessingResult processingResult = new ProcessingResult(ProcessingResult.ResultType.MESSAGE,
                    TXT_CREATED_SUCCESSFULLY + variable.getVariableName());
            processingResult.setScript(script);
            processingResult.setSql(new ParsedAssignment(variable.getDescription(), variable.getVariableName()));
            return processingResult;
        } else {
            return new ProcessingResult(ProcessingResult.ResultType.MESSAGE, TXT_TABLE_ALREADY_EXISTS);
        }
    }

    /**
     * Check if given string contains letters (a-z, A-Z).
     *
     * @param text is given String
     * @return true if text contains at least one letter
     */
    protected boolean isTextContainsLetters(final String text) {
        Pattern p = Pattern.compile("[a-zA-Z]");
        Matcher m = p.matcher(text);
        return m.find();
    }

    /**
     * Perform DROP table statement.
     * <p>
     * Syntax:
     * -mytable
     * <p>
     * SQL triggered:
     * DROP TABLE mytable;
     *
     * @return execution summary
     */
    private ProcessingResult dropTable() {
        final ProcessingResult pr = new ProcessingResult(ProcessingResult.ResultType.COMPOSITE, "Dropped a few things");
        pr.setScript(script);
        String variableName = command.substring(command.indexOf(DROP_TABLE_COMMAND) + 1, command.length()).trim();

        // prepare list of variable(s) to be dropped
        ArrayList<String> variableNames = new ArrayList<>();
        if(variableName.contains(WILDCARD)) {
            for(String variable : context.getDatabase().getTableNames()) {
                if(variable.matches(variableName.replaceAll("\\" + WILDCARD, ".*")))
                    variableNames.add(variable);
            }
        } else {
            variableNames.add(variableName);
        }

        // empty?
        if(variableNames.size() == 0)
            return new ProcessingResult(ProcessingResult.ResultType.MESSAGE, TXT_DROP_NO_MATCHES);

        // drop it/them
        for(String variableToBeDropped : variableNames) {

            // table or view? if neither, skip and go to next one...
            String tableOrView;
            if(context.getDatabase().existsTable(variableToBeDropped))
                tableOrView = "TABLE";

            else if(context.getDatabase().existsTableOrView(variableToBeDropped))
                tableOrView = "VIEW";

            else {
                pr.addSubResult(new ProcessingResult(ProcessingResult.ResultType.FATAL_ERROR, TXT_TABLE_DOES_NOT_EXIST));
                continue;
            }

            // ...otherwise, go and delete
            boolean dropped = context.getDatabase().dropIfExistsViewOrTable(variableToBeDropped);
            context.getDatabase().deleteVariable(variableToBeDropped);
            if (dropped) {
                ProcessingResult subResult = new ProcessingResult(ProcessingResult.ResultType.MESSAGE, TXT_DROPPED_SUCCESSFULLY + variableToBeDropped);
                subResult.setSql(new ParsedAssignment("DROP " + tableOrView + " " + variableToBeDropped, null));
                pr.addSubResult(subResult);
            } else {
                ProcessingResult subResult = new ProcessingResult(ProcessingResult.ResultType.FATAL_ERROR, TXT_DROP_ERROR + variableToBeDropped);
                subResult.setSql(new ParsedAssignment("DROP " + tableOrView + " " + variableToBeDropped, null));
                pr.addSubResult(subResult);
            }

        }

        // if was single result, return so, otherwise return whole set
        if(pr.getSubResults().size() > 1)
            return pr;
        else
            return pr.getSubResults().get(0);
    }
}
