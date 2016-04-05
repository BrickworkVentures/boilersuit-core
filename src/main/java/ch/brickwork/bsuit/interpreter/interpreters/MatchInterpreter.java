package ch.brickwork.bsuit.interpreter.interpreters;

import ch.brickwork.bsuit.database.IDatabase;
import ch.brickwork.bsuit.database.Variable;
import ch.brickwork.bsuit.globals.IBoilersuitApplicationContext;
import ch.brickwork.bsuit.matcher.MagicMatcher;
import ch.brickwork.bsuit.matcher.MatchArguments;
import ch.brickwork.bsuit.util.TextUtils;

import java.util.Hashtable;

/**
 * <p>
 * Fuzzy matching two data sets based on an attribute from both data sets. For instance, you may have table1 with "name" and table2 with "name".
 * Result would be a new table containing all entries where table1's name is similar to table2's name. Various text similarity methods are used
 * to determine the quality of the match (currently the <a href="https://en.wikipedia.org/wiki/Jaro%E2%80%93Winkler_distance">Jaro/Jaro-Winkler Distance</a>
 * and <a href="https://en.wikipedia.org/wiki/S%C3%B8rensen%E2%80%93Dice_coefficient">Dice's coefficient</a>
 * <p/>
 * <h2>Syntax</h2>
 * <p class="syntax">
 * <b>[result_table_prefix :=] MATCH</b> <i>left_table</i><b>(</b><i>left_attribute</i><b>)</b> <b>ON</b> <i>right_table</i><b>(</b><i>right_attribute</i><b>)</b> [<b>WITH</b> <i>with-clause</i>]
 * <p>
 *     The with clause may contain the following parameters:
 *     <table>
 *         <tr>
 *             <th>Parameter</th>
 *             <th>Result</th>
 *         </tr>
 *         <tr>
 *             <td class="syntax"><b>DISPLAYLEFT(</b><i>attribute_1</i>, ..., <i>attribute_n</i><b>)</b></td>
 *             <td>
 *                 Will show all indicated attributes of the left table in the result table of the match
 *                 (unless explicitly indicated, only match attributes plus match info will be shown but no other attributes,
 *                 to keep the result table clean)
 *             </td>
 *         </tr>
 *         <tr>
 *             <td class="syntax"><b>DISPLAYRIGHT(</b><i>attribute_1</i>, ..., <i>attribute_n</i><b>)</b></td>
 *             <td>
 *                 Will show all indicated attributes of the right table in the result table of the match
 *                 (unless explicitly indicated, only match attributes plus match info will be shown but no other attributes,
 *                 to keep the result table clean)
 *             </td>
 *         </tr>
 *         <tr>
 *             <td class="syntax"><b>THRESHOLD(</b><i>value</i><b>)</b></td>
 *             <td>
 *                 Will ignore all "matches" below a certain threshold. If at least one of the applied similarity
 *                 measures is above (greater than) the threshold indicated, the result match will appear, otherwise
 *                 it will not. <b>Note that there is no default threshold, so using this argument is important
 *                 to prevent too many match results.</b>
 *             </td>
 *         </tr>
 *         <tr>
 *             <td class="syntax"><b>SUPPRESSSECONDBEST
 *             <td>
 *                 For most records, there will be more than one match, and they all vary by quality. If the best
 *                 match is just enough, then this option can be used to restrict the result to the best matches per
 *                 left record against right record.
 *             </td>
 *         </tr>*
 *     </table>
 * </p>
 * </p>
 * Usage:
 * MATCH table_x(attribute1) ON table_y(attribute2)
 * MATCH table_x(attribute1) ON table_y(attribute2) WITH threshold(0.9)
 * MATCH table_x(attribute1, attribute2) ON table_y(attribute3, attribute4)
 * ______________________________________________________________________________
 * THRESHOLD is used for different fuzzy match strategies, such as Jaro, Dice etc.
 * <p/>
 */
public class MatchInterpreter extends AbstractInterpreter {

    private static final String SYNTAX_HELP = "Syntax:\n MATCH table_a(attributes, ...) ON table_b(attributes, ...)";

    private final IDatabase database = context.getDatabase();

    private Variable leftVariable;

    private MatchArguments matchArguments;

    private Variable rightVariable;

    /**
     * stores target variable and command text (both are automatically trimmed) for interpreters
     */
    public MatchInterpreter(final Variable targetVariable, final String command, final IBoilersuitApplicationContext context)
    {
        super(targetVariable, command, context);
    }

    /**
     * Parses command (see {@link #parseCommand(ProcessingResult)}) and uses {@link MagicMatcher} class to prepare the match results.
     *
     * @return processing results
     */
    @Override

    public ProcessingResult process()
    {
        final ProcessingResult syntaxError = new ProcessingResult(ProcessingResult.ResultType.SYNTAX_ERROR, "Wrong syntax! Usage: " + SYNTAX_HELP);

        if (!parseCommand(syntaxError)) {
            return syntaxError;
        }

        // fuzzyMatch:
        final String targetVariableNameTmp = getTargetVariable().getVariableName();
        database.deleteVariable(targetVariableNameTmp);
        final MagicMatcher matcher = new MagicMatcher(leftVariable, rightVariable, matchArguments, targetVariableNameTmp, targetVariableNameTmp, context);
        final ProcessingResult match = matcher.match();
        if (null != match) {
            match.setScript(script);
            return match;
        } else {
            return syntaxError;
        }
    }

    @Override
    public boolean understands()
    {
        return command.toLowerCase().indexOf("match ") == 0;
    }

    /**
     * For instance the following command:
     * fuzzymatch := match ourcompanies(name) ON companylist(name) WITH THRESHOLD(0.9);
     * Will be parsed to:
     * {@link #leftVariable}  will contain ourcompanies as variable name
     * {@link #rightVariable}   will contain  companylist as variable name
     * {@link #matchArguments}  will contain name attribute from ourcompanies table, name attribute from companylist table and also a value of threshold which will be 0.9 in this case.
     * {@link #targetVariable}   will contain  fuzzymatch as variable name
     *
     * @param syntaxError processing results contains a message if syntax error occurred.
     *
     * @return true if parsed successfully
     */
    private boolean parseCommand(final ProcessingResult syntaxError)
    {
        // rightSide: everything after "match"
        final String rightSide = TextUtils.between(command.toLowerCase(), "match ", null);
        if (null == rightSide) {
            context.getLog().err("Wrong syntax!");
            return false;
        }
        final int openingBracketLeft = rightSide.indexOf("(");
        if (openingBracketLeft == -1) {
            context.getLog().err("Argument-less matching not yet implemented!");
        }
        final String leftVariableName = rightSide.substring(0, openingBracketLeft).trim();
        leftVariable = database.getVariable(leftVariableName);
        if (null == leftVariable || !database.existsTable(leftVariable.getTableName())) {
            context.getLog().err(leftVariableName + " does not exist!\n" + SYNTAX_HELP);
            syntaxError.setResultSummary(leftVariableName + " does not exist!");
            return false;
        }

        final String leftAttributesString = rightSide.substring(rightSide.indexOf("(") + 1, rightSide.indexOf(")"));
        final int onStart = rightSide.indexOf(" on ");
        if (onStart == -1) {
            context.getLog().err("Invalid syntax for MATCH - missing 'ON'");
        }

        // rightOfrightSide = everything after "on"
        final String rightOfRightSide = rightSide.substring(onStart + 4);
        final String rightVariableName = rightOfRightSide.substring(0, rightOfRightSide.indexOf("(")).trim();
        rightVariable = database.getVariable(rightVariableName);
        if (null == rightVariable || !database.existsTableOrView(rightVariable.getTableName())) {
            context.getLog().err("Table " + rightVariableName + " does not exist!\n" + SYNTAX_HELP);
            syntaxError.setResultSummary("\"Table \" + rightVariableName + \" does not exist!");
            return false;
        }

        final String rightAttributesString = rightOfRightSide.substring(rightOfRightSide.indexOf("(") + 1, rightOfRightSide.indexOf(")"));
        System.out.println("***TEST: " + command + "\n" + rightAttributesString);

        final String withString = TextUtils.between(rightOfRightSide, " with ", null);
        matchArguments = new MatchArguments(leftAttributesString, rightAttributesString, withString);
        if (!matchArguments.isInitSuccess()) {
            context.getLog().err("Problems with initializing match arguments.");
        }

        // check domains
        final Hashtable<String, Integer> leftTableOrViewColumnNamesHash = database.getTableOrViewColumnNamesHash(leftVariable.getTableName());
        for (final String leftAttr : matchArguments.getLeftAttributes()) {
            if (null != leftTableOrViewColumnNamesHash && null == leftTableOrViewColumnNamesHash.get(leftAttr)) {
                context.getLog().err("Attribute " + leftAttr + " not found!");
                syntaxError.setResultSummary("Attribute " + leftAttr + " not found!");
                return false;
            }
        }

        final Hashtable<String, Integer> rightTableOrViewColumnNamesHash = database.getTableOrViewColumnNamesHash(rightVariable.getTableName());
        for (final String rightAttr : matchArguments.getRightAttributes()) {
            if (null != rightTableOrViewColumnNamesHash && null == rightTableOrViewColumnNamesHash.get(rightAttr)) {
                context.getLog().err("Attribute " + rightAttr + " not found!");
                syntaxError.setResultSummary("Attribute " + rightAttr + " not found!");
                return false;
            }
        }
        return true;
    }
}
