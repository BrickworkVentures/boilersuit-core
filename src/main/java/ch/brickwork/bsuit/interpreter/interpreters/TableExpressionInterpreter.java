package ch.brickwork.bsuit.interpreter.interpreters;

import ch.brickwork.bsuit.database.IDatabase;
import ch.brickwork.bsuit.database.Record;
import ch.brickwork.bsuit.database.Value;
import ch.brickwork.bsuit.database.Variable;
import ch.brickwork.bsuit.globals.IBoilersuitApplicationContext;
import ch.brickwork.bsuit.util.Partition;
import ch.brickwork.bsuit.util.Partitioning;
import ch.brickwork.bsuit.util.TextUtils;
import org.apache.poi.ss.usermodel.DateUtil;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * <p>
 * This is to interpret BS's shorthand notation for SQL queries
 * </p>
 * <h2>Special features</h2>
 * <h3>Attribute qualifiers</h3>
 * <p>
 *   Any given attribute in the brackets can be further qualified to filter it or to sort by it:
 * <pre>
 * -- this
 * customers(id, email{LIKE %co.uk, ASC});
 *
 * -- is equivalent to:
 * SELECT id, email FROM customers WHERE email LIKE '%.co.uk' ORDER BY email ASC;
 * </pre>
 * </p>
 * <h3>Pre-processing functions (bsfXXX)</h3>
 * <p>
 * In addition, the concept of a "pre-processing" function is introduced, which will pre-process specific
 * columns of a source table in certain ways. The pre-processing can be done using the following functions:
 * <table>
 * <tr><th>Function</th><th>Purpose</th></tr>
 * <tr>
 * <td>Row ID</td>
 * <td>Short-hand notation to add a row number to the result
 * <pre>
 * -- in sqlite for instance, this would translate to SELECT rowid AS id, ** FROM source
 * transformed := source(!id, **);
 * </pre>
 * </td>
 * </tr>
 * <tr>
 * <td>bsfMagicDate</td>
 * <td>Tries to recognize a date expression from various formats into the BS default format MM.dd.yyyy.
 * Works even if the formats in the source data vary from row
 * to row. For unrecognized expressions, a warning will be shown.
 * <pre>
 * -- checking against common formats
 * transformed := source(*, magicDate(MYDATE) AS STANDARDIZED_MY_DATE);
 *              </pre>
 * If a preferred set of allowed formats should be used, they can be added as arguments:
 * <pre>
 * -- checking against y-m-d and y.m.d:
 * transformed := source(*, magicDate(MYDATE, y-m-d, y.m.d[,...]) AS STANDARDIZED_MY_DATE);
 *              </pre>
 * </td>
 * </tr>
 * <tr>
 * <td>bsfFormatNumber</td>
 * <td>Constructs a string using a pre-existing number in a certain way often useful to construct artificial IDs
 * for things. Lets assume we have a table with id's
 * <pre>id
 * -----
 * 1
 * 239
 * 4000
 * </pre>
 * Then, we may want to construct id's of uniform length like so:
 * <pre>
 * result := table(id, formatNumber(id, A-DDDD) AS NEW_ID);
 *
 * -- Result will be as follows:
 * id       NEW_ID
 * ----     -----------
 * 1        A-0001
 * 239      A-0239
 * 4000     A-4000
 * </pre>
 * </td>
 * </tr>
 * <tr>
 * <td>bsfSuck</td>
 * <td>"Sucks" out specific patterns from the source data based on a regular expression</td>
 * </tr>
 * <tr>
 * <td>bsfHash</td>
 * <td><a href="https://en.wikipedia.org/wiki/Hash_function">Hashes</a> the source data using the String.hashCode method (probably depends on JVM used to run BS).
 * If the hash value is > 0, an 'X' is added to the hash as a prefix, otherwise and 'Y' - this explains the Xxxxxx resp. Yxxxxx format of the hash result.
 * </td>
 * </tr>
 * </table>
 * </p>
 * <ul>
 * <li>copy the result of an expression to a table</li>
 * <li>import a file into a table</li>
 * </ul>
 * </p>
 * <p>
 * The expression can be on of the following:
 * <ul>
 * <li>the name of an other, existing table or view (which will be copied to the assignment table)</li>
 * <li>the result of a <a href="#TableExpression">TableExpression</a>, which is a special short notation for certain SQL queries</li>
 * <li>a native SQL query</li>
 * <li>in the case of an import, a filename with or without wildcards in it</li>
 * </ul>
 * </p>
 * <h2>Syntax</h2>
 * <p class="syntax">
 * <i>[new_table_name]</i><b>:=</b><i>filename</i> | <i>table-name</i> | <i>table-expression</i> | <i>native-sql-expression</i> | <i>wildcards</i>
 * </p>
 * <h2>Examples</h2>
 * The most common and simple case is a short-hand notation for a SELECT statement:
 * <pre>
 * mytable(a, **)        -- equivalent to SELECT a, ** FROM mytable
 * </pre>
 */
public class TableExpressionInterpreter extends AbstractInterpreter {

    private static final int MAGIC_FUNCTIONS_PARTITION_SIZE = 10000;

    /**
     * in a table expression, you can use something like table(!id, a, b, c, *) instead of
     * table(rowid AS id, a, b, c, *)
     */
    public static final String ROW_ID_FUNCTION_PREFIX = "!";

    private final IDatabase database = context.getDatabase();

    private String assigned_variable;

    private String orderClause;

    private List<PreProcessInstruction> postProcessInstructions;

    private ArrayList<String> defaultPossibleFormats;

    /**
     * Used to number the suckattributes in the result in a sequential manner, if they have no 'AS' indicated,
     * e.g. if someone uses
     * <br/>
     * ...(suck(...), suck(...), ...)<br/>
     * instead of                         <br/>
     * ...(suck(...) AS myfirstsuck, suck(...) AS mysecondsuck, ...) ...<br/>
     */
    private int suckIndex;

    private String whereClause;

    /**
     * stores target variable and command text (both are automatically trimmed) for interpreters
     *
     * @param targetVariable is target variable
     * @param command        is command text
     * @param context        is Logger instance
     */
    public TableExpressionInterpreter(Variable targetVariable, String command, IBoilersuitApplicationContext context) {
        super(targetVariable, command, context);
        initDefaultPossibleFormats();
    }

    @Override
    public ProcessingResult process() {
        return processTableExpression(getTargetVariable().getVariableName(), command, false);
    }

    @Override
    public boolean understands() {
        // TODO: Ugly and actually wrong... The same way is used in all of the interpreters.
        return (command.contains("(") && command.contains(")") || TextUtils.count(command, "+") > 0) && (!command.toLowerCase().startsWith("map ")
                && !command.toLowerCase().startsWith("-") && !command.toLowerCase().startsWith("+"));
    }

    /**
     * D stands for digit or character
     * rest is interpreted as is. Format has the structure: Prefix-characters + 1..n D's + Postfix-characters
     * If the number is smaller in length than the D's, then trailing zeros are used to fill up.If it is equal or
     * longer, no zeros are used. If longer, the full length is used (e.g. 99999 in the example below)
     * Example:
     * A = { 1, 2, 10, 1000, 99999 }
     * if formatnumber(string, 1DDD) is applied on all the elements:
     * { 1001, 1002, 1010, 11000, 199999 }
     * <p>
     * formatnumber(string, XDYY)
     * { X1YY, X2YY, X10YY, X1000YY, X9999YY }
     */
    private String bsfFormatNumber(final String value, final String formatString) {
        if (formatString.contains("D")) {
            final int targetLength = TextUtils.count(formatString, "D");
            final int missing = Math.max(targetLength - value.trim().length(), 0);

            String trailingZeros = "";
            for (int i = 1; i <= missing; i++) {
                trailingZeros += '0';
            }
            final String prefix = formatString.substring(0, formatString.indexOf("D"));
            final String postfix = formatString.substring(formatString.indexOf("D") + targetLength);

            return prefix + trailingZeros + value.trim() + postfix;
        } else {
            return value;
        }
    }

    /**
     * @param value is a String used to create hash code
     * @return the hash code of the string "value", which is by nature of the Java's default hash for Strings,
     * either negative or positive, and makes it more beautiful by omitting sign and adding a prefix X (positive) or
     * Y (0 or negative)
     */

    private String bsfHash(final String value) {
        int hash = value.hashCode();
        return ((hash > 0) ? "X" : "Y") + Math.abs(hash);
    }

    /**
     * Magic date recognizes a series of different date formatting. It converts each value separately,
     * i.e. it doesn’t hurt if some of your values are in the format YYYY/MM/DD and others in the form DD.MM.YYYY;
     * it will convert all to DD.MM.YYYY.
     *
     * @param value text contains a date in some format
     * @return a date in format DD.MM.YYYY or null if couldn't parsed
     */

    private String bsfMagicDate(String value, PreProcessInstruction preProcessInstruction) {
        if (value != null && value.trim().length() == 0)
            return "";

        Date date = null;
        boolean yearIsFirst = false;
        int successfulParseCount = 0; // count successful parses - should be only one!

        ArrayList<String> possibleFormats = new ArrayList<String>();
        for (String argument : preProcessInstruction.getArguments().keySet()) {
            if (!argument.equals("attribute"))
                possibleFormats.add(preProcessInstruction.getArgument(argument));
        }

        // if no formats provided, use default formats
        if (possibleFormats.size() == 0) {
            possibleFormats = defaultPossibleFormats;
        }

        Date successFullParse = null;
        for (String tryFormat : possibleFormats) {
            date = null;
            if (tryFormat.equals("XLS")) {
                date = parseExcelDate(value, false);
            } else if (tryFormat.equals("XLSMAC")) {
                date = parseExcelDate(value, true);
            } else {
                try {
                    final SimpleDateFormat parser = new SimpleDateFormat(tryFormat);
                    parser.setLenient(false);
                    date = parser.parse(value);

                } catch (ParseException e) {
                    // must be empty!
                    // (to detect unsuccesful parse)

                    // System.out.println("Try to parse " + value + " using " + tryFormat);
                }
            }

            if (null != date) {
                successfulParseCount++;
                successFullParse = date;

                // System.out.println("Succesful parse: " + value + " with " + tryFormat);
            }
        }

        final SimpleDateFormat formatter = new SimpleDateFormat("dd.MM.yyyy");

        if (successFullParse == null) {
            context.getLog().warn("no parser found for: " + value);
            //    context.getDatabase().prepare("INSERT INTO warnings VALUES (date('now'), '" + assigned_variable + "', '" + value + "', 'magicdate', 'No parser worked')");
            return null;
        } else if (successfulParseCount > 1) {
            context.getLog().warn("multiple parsers found for: " + value);
            //    context.getDatabase().prepare("INSERT INTO warnings VALUES (date('now'), '" + assigned_variable + "', '" + value + "', 'magicdate', 'Ambiguous')");
            return null;
        } else
            return formatter.format(successFullParse);
    }

    /**
     * Suck enables you to ‘suck’ out tokens from the values that match a given regular expression. For instance, if you look for some number of the form A123343 in values like
     * Number: A123392
     * B940032 (Blabla)
     * dkkxoidD499302—doskea
     * you could use
     * suck(‘\w\d+’, 1)
     * to ‘suck out’
     * A123392
     * B940032
     * D499302
     * from the values.
     *
     * @return sucked part of value, or "" if the value was not found, or if lesser values were found than the index is set to
     */

    private String bsfSuck(final String value, final String regex, int num) {
        final List<String> results = new ArrayList<>();
        int i = 0;
        for (Matcher m = Pattern.compile(regex).matcher(value); i <= num && m.find(); i++) {
            results.add(m.toMatchResult().group());
        }
        if (num - 1 < results.size())
            return results.get(num - 1);
        else
            return "";
    }

    /**
     * Processes any right-hand-side assignment that is NOT a sql statement and
     * that is not one.
     *
     * @param variableName name of variable
     * @param assignment   right side of command (after :=)
     * @return ParsedAssignment
     */

    private ParsedAssignment createAssignmentSql(final String variableName, final String assignment) {
        orderClause = "";
        whereClause = "";
        ArrayList<String> errors = new ArrayList<String>();

        final StringBuilder selectSql = new StringBuilder("SELECT ");

        // if (count(assignment, "(") == 1) {
        final int bracketOpenPosition = assignment.indexOf('(');

        assigned_variable = (bracketOpenPosition > -1) ? assignment.substring(0, bracketOpenPosition).trim() : assignment.trim();

        // it is a xy(...) expression, but xy is not defined:
        if (bracketOpenPosition != 1 && !database.existsTableOrView(Variable.getTableName(assigned_variable))) {
            context.getLog().warn("Variable " + assigned_variable + " does not exist!");
            return null;
        } else {
            errors = prepareSelectAttributes(assignment, selectSql);
        }

        selectSql.append(" FROM ");
        selectSql.append(Variable.getTableName(assigned_variable));

        // add clauses
        if (whereClause.length() > 0) {
            selectSql.append(" WHERE ");
            selectSql.append(whereClause);
        }
        if (orderClause.length() > 0) {
            selectSql.append(" ORDER BY ");
            selectSql.append(orderClause);
        }

        context.getLog().info(selectSql.toString());
        return new ParsedAssignment(selectSql.toString(), variableName);
    }

    /**
     * @param selectSql      text contains the select statement
     * @param attributeToken attribute used for extract token
     * @return list of errourness attribute names used, or empty list if everything ok
     * @TODO: hm....use toLower against as...!!
     */
    private String createTokenFromAttributes(final StringBuilder selectSql, final String attributeToken) {
        // find token and predefine AS text - assuming it there in the form (..., token AS predefined-as-text, ...)
        String attributeTokenBeforeAs = TextUtils.between(attributeToken, null, " AS ");
        String preDefinedAs = TextUtils.between(attributeToken, " AS ", null);
        if (attributeTokenBeforeAs == null) {
            attributeTokenBeforeAs = TextUtils.between(attributeToken, null, " as ");
            preDefinedAs = TextUtils.between(attributeToken, " as ", null);
        }

        if (attributeTokenBeforeAs == null) {
            attributeTokenBeforeAs = TextUtils.between(attributeToken, null, " As ");
            preDefinedAs = TextUtils.between(attributeToken, " As ", null);
        }

        if (attributeTokenBeforeAs == null) {
            attributeTokenBeforeAs = TextUtils.between(attributeToken, null, " aS ");
            preDefinedAs = TextUtils.between(attributeToken, " aS ", null);
        }

        // no AS, so simply full token (predef null then):
        if (attributeTokenBeforeAs == null)
            attributeTokenBeforeAs = attributeToken;

        // trim the stuff
        attributeTokenBeforeAs = attributeTokenBeforeAs.trim();
        if (preDefinedAs != null) {
            preDefinedAs = preDefinedAs.trim();
        }

        // pre-process if this is a 'steering attribute' in the form {...}
        attributeTokenBeforeAs = preProcessOptions(attributeTokenBeforeAs);

        // pre-process if this is a BoilerSuit command /
        // operation
        suckIndex = 0;
        attributeTokenBeforeAs = preProcessBoilerSuitOperations(attributeTokenBeforeAs, preDefinedAs);

        // generate "as" text to be used as default (if less
        final String asText;
        if (attributeTokenBeforeAs != null) {
            asText = attributeTokenBeforeAs;

            // add
            selectSql.append(attributeTokenBeforeAs);

            // add as (unless it exists, and unless it's *, and unless preprocessBSOps already added one
            if (!attributeTokenBeforeAs.trim().equals("*") && !attributeTokenBeforeAs.toLowerCase().contains(" as ")) {
                selectSql.append(" AS ");
                if (preDefinedAs == null) {
                    selectSql.append(asText.trim());
                } else {
                    selectSql.append(preDefinedAs.trim());
                }
            }
        }

        return null;
    }

    /**
     * Prepares the UNION sql.
     *
     * @param pr            processing result contains sql statement
     * @param viewNames     names of view
     * @param attributeList list of attributes
     * @return processing result with SQL statement that will be used for create a table
     */
    private ProcessingResult createUnionSql(final ProcessingResult pr, final List<String> viewNames,
                                            final List<String> attributeList) {
        // create union SQL
        final StringBuilder sql = new StringBuilder();

        boolean firstComponent = true;
        for (final String viewName : viewNames) {
            if (firstComponent) {
                firstComponent = false;
            } else {
                sql.append(" UNION ");
            }
            sql.append("SELECT ");
            boolean firstAttribute = true;
            for (final String columnName : attributeList) {
                if (firstAttribute) {
                    firstAttribute = false;
                } else {
                    sql.append(", ");
                }
                final Hashtable<String, Integer> tableOrViewColumnNamesHash = database.getTableOrViewColumnNamesHash(viewName);
                if (null != tableOrViewColumnNamesHash && tableOrViewColumnNamesHash.get(columnName) != null) {
                    sql.append(database.sanitizeName(columnName));
                } else {
                    sql.append("'' AS ");
                    sql.append(database.sanitizeName(columnName));
                }
            }
            sql.append(" FROM ");
            sql.append(viewName);
        }

        final StringBuilder createViewAs = new StringBuilder("CREATE TABLE ");
        createViewAs.append(getTargetVariable().getVariableName());
        createViewAs.append(" AS ");
        createViewAs.append(sql);
        context.getLog().info("Processing SQL Statement: " + createViewAs.toString());

        if (database.prepare(createViewAs.toString()) == null) {
            final String message = "Something in your SQL statement must be wrong. Make sure you use data_x as table name for variable x!";
            context.getLog().err(message);
            return new ProcessingResult(ProcessingResult.ResultType.SYNTAX_ERROR, message);
        } else {
            database.createOrReplaceVariable(getTargetVariable().getVariableName(), createViewAs.toString(), "");
        }
        pr.setSql(createViewAs.toString(), getTargetVariable().getVariableName());
        return pr;
    }

    /**
     * Search for
     * ASC, DESC, LIKE etc.
     *
     * @param attributeString value of attribute used for particular clause
     * @param optionTokens    tokens used for search
     */
    private void getWhereOrOrderClauseOptions(final String attributeString, final String[] optionTokens) {
        if (optionTokens != null) {
            for (final String optionToken : optionTokens) {
                if (optionToken.trim().equalsIgnoreCase("ASC")) {
                    orderClause += ((orderClause.length() > 0) ? ", " : "") + attributeString + " ASC";
                } else if (optionToken.trim().equalsIgnoreCase("DESC")) {
                    orderClause += ((orderClause.length() > 0) ? ", " : "") + attributeString + " DESC";
                } else if (optionToken.trim().contains("=")) {
                    final String between = TextUtils.between(optionToken, "=", null);
                    if (null != between) {
                        whereClause += ((whereClause.length() > 0) ? " AND " : "") + attributeString + "='" + between.trim() + "'";
                    }
                } else if (optionToken.trim().contains("like")) {
                    final String between = TextUtils.between(optionToken, "like", null);
                    if (between != null) {
                        whereClause += ((whereClause.length() > 0) ? " AND " : "") + attributeString + " LIKE '" + between.trim() + "'";
                    }
                } else if (optionToken.trim().contains("LIKE")) {
                    final String between = TextUtils.between(optionToken, "LIKE", null);
                    if (between != null) {
                        whereClause += ((whereClause.length() > 0) ? " AND " : "") + attributeString + " LIKE '" + between.trim() + "'";
                    }
                } else {
                    context.getLog().warn("Unprocessed attribute option: " + optionToken);
                }
            }
        }
    }

    /**
     * parses a serial date code known from Microsoft Excel into a date
     *
     * @param value   a serial date like 35981
     * @param macMode if true, uses the 1904 date system normally used by MS Excel on a Mac, otherwise the 1900 system which is normally used on PCs, and which is default (cf. http://support.microsoft.com/kb/180162)
     * @return date
     */
    private Date parseExcelDate(String value, boolean macMode) {
        return DateUtil.getJavaDate(new Double(value), macMode);
    }

    /**
     * Prepares records for insert to database and transform them according to used functions.
     * If BoilerSuit functions were found, performs them.
     *
     * @param queryResult list of records which will be inserted into table specified by a target variable.
     */
    private boolean postProcess(final List<Record> queryResult) {
        boolean perfectRun = true;  // be optimistic

        if (queryResult == null) {
            context.getLog().err("Check your statement. An error was reported!");
        } else { // apply post processing instructions
            for (final Record r : queryResult) {
                for (final PreProcessInstruction pi : postProcessInstructions) {
                    if (pi.getName().equalsIgnoreCase("MAGICDATE")) {
                        perfectRun &= useMagicdateFunction(r, pi);
                    } else if (pi.getName().equalsIgnoreCase("HASH")) {
                        perfectRun &= useHashFunction(r, pi);
                    } else if (pi.getName().equalsIgnoreCase("SUCK")) {
                        perfectRun &= useSuckFunction(r, pi);
                    } else if (pi.getName().equalsIgnoreCase("FORMATNUMBER")) {
                        perfectRun &= useFormatnumberFunction(r, pi);
                    }
                }
            }
            database.insert(getTargetVariable().getVariableName().toLowerCase(), queryResult);
        }

        return perfectRun;
    }

    /**
     * For a given TOKEN, e.g. magicdate(x), process this token such that it can
     * be used as part of a sql statement. For instance in magicdate(x), it will
     * remove magicdate() and only return x, but on the other hand add a
     * post-instruction such that the x will be processed after the executing of
     * the sql query
     *
     * @param arg          - definition of the column/argument, e.g.
     *                     magicdate(myattribute)
     * @param preDefinedAs AS name of this column, e.g. mymagicdate
     * @return text between brackets
     */

    private String preProcessBoilerSuitOperations(String arg, final String preDefinedAs) {
        String sqlAttributeDefinition;

        String preDefinedAsAddOn = (preDefinedAs == null) ? "" : " AS " + preDefinedAs;

        sqlAttributeDefinition = preprocessMagicDateClause(arg, preDefinedAs);
        if (sqlAttributeDefinition != null)
            return sqlAttributeDefinition;

        sqlAttributeDefinition = preprocessSuckClause(arg, preDefinedAs);
        if (sqlAttributeDefinition != null)
            return sqlAttributeDefinition;

        sqlAttributeDefinition = preprocessFormatNumberClause(arg, preDefinedAs);
        if (sqlAttributeDefinition != null)
            return sqlAttributeDefinition;

        sqlAttributeDefinition = preprocessHashClause(arg, preDefinedAs);
        if (sqlAttributeDefinition != null)
            return sqlAttributeDefinition;


        // for now, only allow table names as parts (e.g. no functions)
        if (sqlAttributeDefinition != null) {
            for (String attribute : sqlAttributeDefinition.split("\\+")) {
                if (!context.getDatabase().existsColumn(assigned_variable, attribute)) {
                    context.getLog().err("At least one boilersuit function cannot be performed because attribute " + attribute + " does not exist in table " + assigned_variable);
                    return null;
                }
            }
        }

        int prefixIndex = arg.indexOf(ROW_ID_FUNCTION_PREFIX);
        if(prefixIndex != -1) {
            preDefinedAsAddOn = " AS " + arg.substring(prefixIndex + 1);
            sqlAttributeDefinition = context.getDatabase().getRowIdKeyWord().replace("+", "||");
        } else {
            // allow + as a concat operator
            sqlAttributeDefinition = arg.replace("+", "||");
        }

        return sqlAttributeDefinition + preDefinedAsAddOn;
    }

    /**
     * For a given TOKEN, e.g. {name ASC} or x{ASC}, removes the options such that null is returned in the first case
     * (general option). If a general option, then the post option text is enhanced.
     *
     * @param text is a command text
     * @return text between brackets
     */

    private String preProcessOptions(final String text) {
        // if not enclosed in { and }, then ignore
        final int openingBracketPos = text.trim().indexOf("{");
        final int closingBracketPos = text.trim().indexOf("}");

        // extract option
        String optionString = null;
        if (openingBracketPos > -1 && closingBracketPos > -1) {
            optionString = text.trim().substring(openingBracketPos + 1, closingBracketPos).trim();
        }

        // if attribute option, add to attribute (e.g. myattribute{ASC; like 'b*'}
        if (openingBracketPos > 0 && closingBracketPos > -1) {
            final String attributeString = text.trim().substring(0, openingBracketPos);
            String[] optionTokens = null;
            if (!attributeString.contains(";")) {
                optionTokens = new String[1];
                optionTokens[0] = optionString;
            } else {
                if (optionString != null) {
                    optionTokens = optionString.split(";");
                }
            }
            getWhereOrOrderClauseOptions(attributeString, optionTokens);
            return attributeString;
        } else {
            return text;
        }
    }

    /**
     * Appends attributes to SELECT clause
     *
     * @param assignment text used for extract attributes
     * @param selectSql  select statement
     */
    private ArrayList<String> prepareSelectAttributes(final String assignment, final StringBuilder selectSql) {
        ArrayList<String> errors = new ArrayList<String>();

        final String attributeString = TextUtils.betweenFirstLast(assignment, "(", ")");

        if (attributeString != null) {
            final List<String> attributeTokens = TextUtils.splitIntoOuterArguments(attributeString.trim(), '(', ')', ',');
            boolean firstAttributeToken = true;

            // iterate through all attributes of the table
            for (final String attributeToken : attributeTokens) {
                if (!firstAttributeToken) {
                    selectSql.append(", ");
                } else {
                    firstAttributeToken = false;
                }

                String error = createTokenFromAttributes(selectSql, attributeToken);
                if (error != null)
                    errors.add(error);
            }
        } else {
            selectSql.append("*");
        }

        return errors;
    }

    /**
     * @param arg          text containing formatnumber function with arguments
     * @param preDefinedAs e.g. formatnumber(string, XDYY) as formatnumber, in this example preDefinedAs will be 'formatnumber'
     * @return list of found arguments
     */

    private String preprocessFormatNumberClause(final String arg, final String preDefinedAs) {
        final int formatNumberPos = arg.toLowerCase().indexOf("formatnumber");

        // this is not a format number function
        if (formatNumberPos == -1) {
            return null;
        }

        final List<String> formatnumberArguments = TextUtils.splitIntoOuterArguments(arg.substring(arg.indexOf('(', formatNumberPos) + 1, arg.length() - 1),
                '(', ')', ',');

        // get arguments
        if (formatnumberArguments == null || formatnumberArguments.size() != 2) {
            context.getLog().err("Wrong syntax for formatnumber - use: formatnumber(stringattribute, formatstring)");
            return null;
        }

        // prepare instruction with arguments
        final PreProcessInstruction prepo = new PreProcessInstruction("FORMATNUMBER", preDefinedAs == null ? database.sanitizeName(arg) : preDefinedAs);
        prepo.addArgument("attribute", formatnumberArguments.get(0).trim().replace("+", "||"));
        prepo.addArgument("formatstring", formatnumberArguments.get(1).trim());
        postProcessInstructions.add(prepo);

        return prepo.getArgument("attribute") + (preDefinedAs == null ? " AS " + database.sanitizeName(arg) : "");
    }

    /**
     * @param arg          text containing hash function with arguments
     * @param preDefinedAs e.g. hash(attr1, attr2) as hash_fun, in this example preDefinedAs will be 'hash_fun'
     * @return found arguments concatenated by ||
     */

    private String preprocessHashClause(final String arg, final String preDefinedAs) {
        final int hashPos = arg.toLowerCase().indexOf("hash");

        // this is not a hash function
        if (hashPos == -1) {
            return null;
        }


        final String hashArgument = TextUtils.between(arg.substring(hashPos), "(", ")");

        String sqlHashString = null;

        if (hashArgument == null) {
            context.getLog().err("Invalid use of hash; use: hash(attribute, ..., attribute)");
            return null;
        }

        // just replace by concatenation of arguments, e.g. if
        // hash(x,y,z) - the attribute loaded in the sql query should be
        // x||y||z, such that the post-instruction can build a hash from
        // this concatenated arg:
        sqlHashString = hashArgument.replace(",", "||");

        // ...but in the end,postprocess!
        postProcessInstructions.add(new PreProcessInstruction("HASH", preDefinedAs == null ? database.sanitizeName(arg) : preDefinedAs));
        context.getLog().log("Added post-processing instruction HASH for attribute " + preDefinedAs);

        // + operator -> ||
        sqlHashString = sqlHashString.replace("+", "||");

        return sqlHashString + (preDefinedAs == null ? " AS " + database.sanitizeName(arg) : "");
    }

    /**
     * @param preDefinedAs      e.g. magicdate(date) as mymagicdate, in this example preDefinedAs will be 'mymagicdate'
     * @param datemagicArgument argument between the brackets
     * @return found argument, or null if magicdate doesn't apply
     */
    private String preprocessMagicDateClause(final String datemagicArgument, final String preDefinedAs) {
        final int magicdatePos = datemagicArgument.toLowerCase().indexOf("magicdate");

        // this is not a magicdate function
        if (magicdatePos == -1) {
            return null;
        }

        final List<String> arguments = TextUtils.splitIntoOuterArguments(
                datemagicArgument.substring(datemagicArgument.indexOf('(', magicdatePos) + 1, datemagicArgument.length() - 1), '(', ')', ',');

        if (arguments == null) {
            context.getLog().err("Invalid use of MAGICDATE; use: MAGICDATE([attribute], [format]) or: MAGICDATE([attribute])");
            return null;
        }

        // prepare instruction with arguments
        final PreProcessInstruction prepo = new PreProcessInstruction("MAGICDATE",
                preDefinedAs == null ? database.sanitizeName(datemagicArgument) : preDefinedAs);

        prepo.addArgument("attribute", arguments.get(0).trim().replace("+", "||"));
        for (int i = 1; i < arguments.size(); i++) {
            prepo.addArgument("" + i, arguments.get(i).trim());
        }
        postProcessInstructions.add(prepo);
        context.getLog().log("Added post-processing instruction MAGICDATE for attribute " + preDefinedAs);

        return prepo.getArgument("attribute") + (preDefinedAs == null ? " AS " + database.sanitizeName(datemagicArgument) : "");
    }

    /**
     * @param arg          text containing hash function with arguments
     * @param preDefinedAs e.g. suck(date, '\w\d+', 1) as mysuck, in this example preDefinedAs will be 'mysuck'
     * @return list of found arguments
     */
    private String preprocessSuckClause(final String arg, final String preDefinedAs) {
        final int suckPos = arg.toLowerCase().indexOf("suck");

        // this is not a suck function
        if (suckPos == -1) {
            return null;
        }

        final List<String> suckArguments = TextUtils.splitIntoOuterArguments(arg.substring(arg.indexOf('(', suckPos) + 1, arg.length() - 1), '(', ')', ',');

        // get arguments
        if (suckArguments.size() != 3) {
            context.getLog().err("Wrong syntax for suck - use: suck(stringattribute, 'your regex', numberofresult_starting_with_0)");
            return null;
        }

        // target name of the result attribute
        String suckName;
        if (preDefinedAs == null) {
            suckName = "suck_nr_" + this.suckIndex;
            suckIndex++;
        } else {
            suckName = preDefinedAs;
        }

        // prepare instruction with arguments
        final PreProcessInstruction prepo = new PreProcessInstruction("SUCK", preDefinedAs == null ? database.sanitizeName(suckName) : preDefinedAs);
        prepo.addArgument("attribute", suckArguments.get(0).trim().replace("+", "||"));
        prepo.addArgument("regex", suckArguments.get(1).trim());
        prepo.addArgument("num", suckArguments.get(2).trim());
        postProcessInstructions.add(prepo);

        return prepo.getArgument("attribute") + (preDefinedAs == null ? " AS " + database.sanitizeName(arg) : "");
    }

    /**
     * At first search for SELECT statement, if cannot be found and we want to fetch data from many tables, prepare the union statement.
     *
     * @param variableName name of variable
     * @param assignment   is a right side of command (after :=)
     * @param store        if true, store the results to target table
     * @return null if the assignment seems not to be a valid statement or View with parsed sql statement.
     */
    private ProcessingResult processTableExpression(final String variableName, final String assignment, boolean store) {

        ProcessingResult pr = processTableExpressionComponent(variableName, assignment, store);

        String viewNameFirstTry = null;
        if (pr != null) {
            viewNameFirstTry = pr.getResultSummary();
        }

        if (viewNameFirstTry != null) {
            return pr;
        }

        // if not an expression, check whether maybe it is an union expression
        // (+)
        // TODO: Second clause ugly and wrong (only works for X+Y+Z, not for X(...)+Y...) - We need to talk what I can change here.
        else if (TextUtils.countLevelOccurrence(assignment, '+', 0, '(', ')') > 0 && !assignment.contains("(")) {

            pr = new ProcessingResult(ProcessingResult.ResultType.TABLE, Variable.getTableName(variableName), script);

            final String[] tokens = assignment.split("\\+");
            final String tempName = database.createTempName();

            // create components view:
            final List<String> viewNames = new ArrayList<>();
            final Hashtable<String, String> attributes = new Hashtable<>();
            final ArrayList<String> attributeList = new ArrayList<>();
            for (int i = 0; i < tokens.length; i++) {
                final String token = tokens[i];
                final ProcessingResult thisResult = processTableExpression(tempName + "_" + i, token.trim(), false);
                viewNames.add(thisResult.getResultSummary());
                // add to hash of all attributes
                final List<String> tableOrViewColumnNames = database.getTableOrViewColumnNames(thisResult.getResultSummary());
                for (String columnName : tableOrViewColumnNames) {
                    if (attributes.get(columnName) == null) {
                        attributes.put(columnName, columnName);
                        attributeList.add(columnName);
                    }
                }
            }
            return createUnionSql(pr, viewNames, attributeList);
        } else {
            // if (!columnErrorDetected)
            final String message = "The assignment seems not to be a valid statement!";
            context.getLog().err(message);
            return new ProcessingResult(ProcessingResult.ResultType.SYNTAX_ERROR, message);
        }
    }

    /**
     * Try to parse assignment and search for SELECT clause.
     *
     * @param variableName name of variable
     * @param assignment   is a right side of command (after :=)
     * @param store        if true, store the results to target table
     * @return parsed assignment or null if that was not possible
     */

    private ProcessingResult processTableExpressionComponent(final String variableName, final String assignment, boolean store) {
        postProcessInstructions = new ArrayList<>();
        ParsedAssignment parsedAssignment;
        final ProcessingResult processingResult = new ProcessingResult(ProcessingResult.ResultType.TABLE, variableName, script);

        // assume that the assignment is a sql statement
        String selectSql = assignment;

        // what if not?
        if (!assignment.toLowerCase().contains("SELECT")) {
            parsedAssignment = createAssignmentSql(variableName, assignment);
            if (parsedAssignment != null) {
                selectSql = parsedAssignment.getSqlString();
            } else {
                context.getLog().log("Tried to parse assignment " + assignment + " without success");
                return null;
            }
        }
        context.getLog().log("Processing table expression Will use statement " + selectSql);

        // process statement
        // if store option is true, or if the magic functions are applied, then we have to first
        // fire the select statement, process it, and then store the result in the target table
        // (i.e., not view is created, a real table is created):
        if (!postProcessInstructions.isEmpty() || store) {
            processingResult.setSql("-- In principle:\n-- " + selectSql
                    + "\n-- (however, some magic was applied. SQL support for magic functions not provided in community edition.)", getTargetVariable().getVariableName());

            final Partitioning partitioning = new Partitioning(database.count(assigned_variable), MAGIC_FUNCTIONS_PARTITION_SIZE);
            for (final Partition partition : partitioning) {
                context.getLog().info("Postprocessing partition [" + partition.getNumber() + "/" + partitioning.countPartitions() + "]");
                final String limitClause = " LIMIT " + partition.getFirstRecord() + "," + partition.getLength();
                final List<Record> queryResult = database.prepare(selectSql + limitClause);
                if (queryResult == null) {
                    return new ProcessingResult(ProcessingResult.ResultType.FATAL_ERROR, "Serious problem post-processing " + assigned_variable);
                }
                // first partition: set up target structure
                if (partition.getNumber() == 0) {
                    database.createOrReplaceVariableAndTable(getTargetVariable().getVariableName().toLowerCase(), "", "", queryResult.get(0).getColumnNames(), null);
                }
                if (!postProcess(queryResult)) {
                    return new ProcessingResult(ProcessingResult.ResultType.FATAL_ERROR, "Problems post-processing (check magicdates, etc.!) and check warnings!");
                }
            }
        }
        // create view only
        else {
            final StringBuilder createViewAs = new StringBuilder("CREATE TABLE ");
            createViewAs.append(variableName);
            createViewAs.append(" AS ");
            createViewAs.append(selectSql);
            context.getLog().log("Processing SQL Statement: " + createViewAs.toString());
            processingResult.setSql(createViewAs.toString(), variableName);
            if (database.prepare(createViewAs.toString()) == null) {
                context.getLog().err("Something in your SQL statement must be wrong. Make sure you use data_x as table name for variable x!");
                return null;
            }
        }
        return processingResult;
    }

    /**
     * default date formats for bfsMagicDate
     */
    private void initDefaultPossibleFormats() {
        defaultPossibleFormats = new ArrayList<>();

        // compromise between comprehensiveness and practicability; e.g. if d.m.y AND m.d.y are
        // included, too many d.m.y (or m.d.y) formatted dates will have ambiguous results; there
        // fore in this example we only consider the "usual" suspect d.m.y
        defaultPossibleFormats.add("d.M.y");

        defaultPossibleFormats.add("d-M-y");
        defaultPossibleFormats.add("y-M-d");

        defaultPossibleFormats.add("d:M:y");
        defaultPossibleFormats.add("y:M:d");

        defaultPossibleFormats.add("M/d/y");
        defaultPossibleFormats.add("y/M/d");

                     /*
            // not sure under what circumstances (JVM version, OS, bad weather, etc.) this was really necessary,
            // but lets keep them just in case
            possibleFormats.add("d:M:yyyy");
            possibleFormats.add("dd:M:yyyy");
            possibleFormats.add("dd:MM:yyyy");
            possibleFormats.add("d:MM:yyyy");
            possibleFormats.add("dd.MM.yyyy");
            possibleFormats.add("d.M.yyyy");
            possibleFormats.add("dd.M.yyyy");
            possibleFormats.add("d.MM.yyyy");
            possibleFormats.add("d/M/yyyy");
            possibleFormats.add("dd/MM/yyyy");
            possibleFormats.add("dd/M/yyyy");
            possibleFormats.add("d/MM/yyyy");
            possibleFormats.add("dd-MM-yyyy");
            possibleFormats.add("d-M-yyyy");
            possibleFormats.add("dd-M-yyyy");
            possibleFormats.add("d-MM-yyyy");

            possibleFormats.add("d:M:yy");
            possibleFormats.add("dd:M:yy");
            possibleFormats.add("dd:MM:yy");
            possibleFormats.add("d:MM:yy");
            possibleFormats.add("dd.MM.yy");
            possibleFormats.add("d.M.yy");
            possibleFormats.add("dd.M.yy");
            possibleFormats.add("d.MM.yy");
            possibleFormats.add("dd.MM.yy");
            possibleFormats.add("d/M/yy");
            possibleFormats.add("dd/MM/yy");
            possibleFormats.add("dd/M/yy");
            possibleFormats.add("d/MM/yy");
            possibleFormats.add("dd-MM-yy");
            possibleFormats.add("d-M-yy");
            possibleFormats.add("dd-M-yy");
            possibleFormats.add("d-MM-yy");

            possibleFormats.add("yyyy:M:d");
            possibleFormats.add("yyyy:MM:dd");
            possibleFormats.add("yyyy:M:dd");
            possibleFormats.add("yyyy:MM:d");
            possibleFormats.add("yyyy.MM.dd");
            possibleFormats.add("yyyy.M.d");
            possibleFormats.add("yyyy.MM.d");
            possibleFormats.add("yyyy.M.dd");
            possibleFormats.add("yyyy/M/d");
            possibleFormats.add("yyyy/MM/dd");
            possibleFormats.add("yyyy/MM/d");
            possibleFormats.add("yyyy/M/dd");
            possibleFormats.add("yyyy-MM-dd");
            possibleFormats.add("yyyy-d-M");
            possibleFormats.add("yyyy-M-dd");
            possibleFormats.add("yyyy-MM-d");
            */
    }


    /**
     * Performs appropriate action when user use formatnumber() function (see {@link #bsfFormatNumber(String, String)} method).
     *
     * @param r  processing record from database
     * @param pi instruction contains particular function
     * @return true, if record could be successfully processed, false otherwise
     */
    private boolean useFormatnumberFunction(final Record r, final PreProcessInstruction pi) {
        // @TODO: Workaround... check the sanitizing of column names. May be it should be consequentially
        // done EVERYWHERE
        String paramName;
        if (pi.getParam().trim().startsWith("\"") && pi.getParam().trim().endsWith("\""))
            paramName = pi.getParam().trim().substring(1, pi.getParam().trim().length() - 1);
        else
            paramName = pi.getParam().trim();

        final String value = (String) r.getValue(paramName).getValue();
        final String formatstring = pi.getArgument("formatstring");
        if (null != formatstring) {
            final String newValue = bsfFormatNumber(value, formatstring);
            r.put(paramName, new Value(paramName, newValue).getValue());
            return true;
        } else
            return false;
    }

    /**
     * Performs appropriate action when user use hash() function (see {@link #bsfHash(String)} method).
     *
     * @param r  processing record from database
     * @param pi instruction contains particular function
     * @return true, if record could be successfully processed, false otherwise
     */
    private boolean useHashFunction(final Record r, final PreProcessInstruction pi) {
        // @TODO: Workaround... check the sanitizing of column names. May be it should be consequentially
        // done EVERYWHERE
        String paramName;
        if (pi.getParam().trim().startsWith("\"") && pi.getParam().trim().endsWith("\""))
            paramName = pi.getParam().trim().substring(1, pi.getParam().trim().length() - 1);
        else
            paramName = pi.getParam().trim();

        final String value = (String) r.getValue(paramName).getValue();
        final String newValue = bsfHash(value);
        r.put(paramName, new Value(paramName, newValue).getValue());
        return true;
    }

    /**
     * Performs appropriate action when user use magicdate() function (see {@link #bsfMagicDate(String, PreProcessInstruction)} method).
     *
     * @param r  processing record from database
     * @param pi instruction contains particular function
     * @return true, if record could be successfully processed, false otherwise
     */
    private boolean useMagicdateFunction(final Record r, final PreProcessInstruction pi) {
        // @TODO: Workaround... check the sanitizing of column names. May be it should be consequentially
        // done EVERYWHERE
        String paramName;
        if (pi.getParam().trim().startsWith("\"") && pi.getParam().trim().endsWith("\""))
            paramName = pi.getParam().trim().substring(1, pi.getParam().trim().length() - 1);
        else
            paramName = pi.getParam().trim();

        String value = (String) r.getValue(paramName).getValue();

        final String newValue = bsfMagicDate(value, pi);

        if (newValue != null) {
            r.put(paramName, new Value(paramName, newValue).getValue());
            return true;
        } else {
            r.put(paramName, new Value(paramName, markUnprocessedValue(value)).getValue());
            return false;
        }
    }

    /**
     * if a bs function is not able to correctly process a value, the target values will be the original values,
     * but marked as "unprocessable" for convenience. Currently, this marking is to put them in "double" brackets like
     * so: ((11.xx.2012))  (in this example, date 11.xx.2012 could not be parsed)
     * @param originalValue
     * @return
     */
    private String markUnprocessedValue(String originalValue) {
        return "((" + originalValue + "))";
    }

    /**
     * Performs appropriate action when user use suck() function (see {@link #bsfSuck(String, String, int)} method).
     *
     * @param r  processing record from database
     * @param pi instruction contains particular function
     * @return true, if record could be successfully processed, false otherwise
     */
    private boolean useSuckFunction(final Record r, final PreProcessInstruction pi) {
        // @TODO: Workaround... check the sanitizing of column names. May be it should be consequentially
        // done EVERYWHERE
        String paramName;
        if (pi.getParam().trim().startsWith("\"") && pi.getParam().trim().endsWith("\""))
            paramName = pi.getParam().trim().substring(1, pi.getParam().trim().length() - 1);
        else
            paramName = pi.getParam().trim();

        final String value = (String) r.getValue(paramName).getValue();
        int num = 0;
        try {
            num = new Integer(pi.getArgument("num"));
        } catch (Exception e) {
            context.getLog().err("Numberformat problem in applying postprocessing suck");
        }
        final String attribute = pi.getArgument("attribute");
        final String regex = pi.getArgument("regex");
        if (null != attribute && null != regex) {
            try {
                final String newValue = bsfSuck(value, regex, num);
                r.put(paramName, new Value(paramName, newValue).getValue());
                return true;
            } catch (PatternSyntaxException pse) {
                context.getLog().err("Invalid syntax in regexp: " + pse.getPattern());
                return false;
            }
        } else
            return false;
    }
}
