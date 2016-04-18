package ch.brickwork.bsuit.interpreter.interpreters;

import ch.brickwork.bsuit.database.IDatabase;
import ch.brickwork.bsuit.database.Variable;
import ch.brickwork.bsuit.globals.IBoilersuitApplicationContext;
import ch.brickwork.bsuit.util.TextUtils;
import java.util.List;

/**
 * <p>
 * Imagine you have mycompanies and allcompanies and you want to look up the allcompanies records for all mycompanies.
 * Some of the mycompanies will have allcompanies records corresponding, others won’t.
 * You want to see those that have, with all the extra information from allcompanies.
 * You want also to see those that do not have a correspondence at the same time.
 * <p/>
 * <p>A way to do this in SQL is a <b>left outer join</b>  This is what BS left outer
 * does for you:
 * </p>
 * <h2>Syntax</h2>
 * <p class="syntax">[result :=] left-table(left-key-attribute) <b>-></b> companylist(right-key-attribute);</p>
 * <h2>Examples</h2>
 * <pre>
 * exactmatch := ourcompanies(name)->companylist(name);
 * </pre>
 * <p/>
 */
public class LeftOuterInterpreter extends AbstractInterpreter {

    private static final java.lang.String TXT_LEFT_OUTER_MISSING_VARS = "Wrong syntax. Use something like mytable(myattribute)->myothertable(attribute)";
    private static final java.lang.String TXT_WRONG_SYNTAX = "Please indicate left and right variable name";

    /**
     * stores target variable and command text (both are automatically trimmed) for interpreters
     *
     * @param targetVariable     is target variable
     * @param command            is command text
     * @param context                is Logger instance
     */
    public LeftOuterInterpreter(final Variable targetVariable, final String command, final IBoilersuitApplicationContext context)
    {
        super(targetVariable, command, context);
    }

    @Override

    public ProcessingResult process()
    {
        return processLeftOuter(command, getTargetVariable().getVariableName());
    }

    @Override
    public boolean understands()
    {
        if (command.contains("->")) {
            final String[] tokens = command.split("->");
            return tokens.length > 1;
        } else {
            return false;
        }
    }

    /**
     * Prepare the results of left outer join.
     *
     * @param input              is a command text
     * @param targetVariableName is target variable name if null, a temp name is generated
     *
     * @return ProcessingResult
     */

    private ProcessingResult processLeftOuter(final String input, final String targetVariableName)
    {
        final String trimmed = input.trim();
        final String[] tokens = trimmed.split("->");
        if (tokens.length != 2) {
            context.getLog().err(TXT_WRONG_SYNTAX);
            return new ProcessingResult(ProcessingResult.ResultType.SYNTAX_ERROR, TXT_LEFT_OUTER_MISSING_VARS);
        } else {
            final IDatabase database = context.getDatabase();

            final String leftExpression = tokens[0].trim();
            final String rightExpression = tokens[1].trim();
            String leftAttribute = TextUtils.between(leftExpression, "(", ")");
            String rightAttribute = TextUtils.between(rightExpression, "(", ")");
            if (leftAttribute == null) {
                context.getLog().err("Please indicate left attribute!");
                return new ProcessingResult(ProcessingResult.ResultType.SYNTAX_ERROR, "Please indicate left attribute!");
            } else {
                leftAttribute = leftAttribute.trim();
            }

            if (rightAttribute == null) {
                context.getLog().err("Please indicate right attribute!");
                return new ProcessingResult(ProcessingResult.ResultType.SYNTAX_ERROR, "Please indicate right attribute!");
            } else {
                rightAttribute = rightAttribute.trim();
            }

            final String leftNameExpr = TextUtils.between(leftExpression, null, "(");
            final String rightNameExpr = TextUtils.between(rightExpression, null, "(");
            final String leftName;
            final String rightName;
            if (null != leftNameExpr && null != rightNameExpr) {

                leftName = leftNameExpr.trim();
                rightName = rightNameExpr.trim();

                if (leftName.length() == 0 || rightName.length() == 0) {
                    context.getLog().err(TXT_LEFT_OUTER_MISSING_VARS);
                    return new ProcessingResult(ProcessingResult.ResultType.SYNTAX_ERROR, TXT_LEFT_OUTER_MISSING_VARS);
                }

                final String leftTableName = Variable.getTableName(leftName);
                final String rightTableName = Variable.getTableName(rightName);
                final StringBuilder sql = new StringBuilder("CREATE TABLE ");
                sql.append(targetVariableName);
                sql.append(" AS SELECT ");

                final List<String> leftTableColumnNames = database.getTableOrViewColumnNames(leftTableName);
                final List<String> rightTableColumnNames = database.getTableOrViewColumnNames(rightTableName);
                if (leftTableColumnNames.isEmpty() || rightTableColumnNames.isEmpty()) {
                    context.getLog().err("There is no column from left or right table. Maybe one of tables don't exist.");
                    return new ProcessingResult(ProcessingResult.ResultType.SYNTAX_ERROR,
                        "There is no column from left or right table. Maybe one of tables don't exist.");
                }
                sql.append(TextUtils.serializeListWithDelimitator(leftTableColumnNames, ", ", leftTableName + ".", " as " + leftTableName + "_<item>"));
                sql.append(", ");
                sql.append(TextUtils.serializeListWithDelimitator(rightTableColumnNames, ", ", rightTableName + ".", " as " + rightTableName + "_<item>"));

                sql.append(" FROM ");
                sql.append(leftTableName);
                sql.append(" LEFT OUTER JOIN ");
                sql.append(rightTableName);
                sql.append(" ON ");
                sql.append(leftTableName);
                sql.append(".");
                sql.append(leftAttribute);
                sql.append("=");
                sql.append(rightTableName);
                sql.append(".");
                sql.append(rightAttribute);

                database.prepare(sql.toString());

                final ProcessingResult pr = new ProcessingResult(ProcessingResult.ResultType.VIEW, targetVariableName, script);
                pr.setSql(new ParsedAssignment(sql.toString(), targetVariableName));
                return pr;
            } else {
                context.getLog().err(TXT_LEFT_OUTER_MISSING_VARS);
                return new ProcessingResult(ProcessingResult.ResultType.SYNTAX_ERROR, TXT_LEFT_OUTER_MISSING_VARS);
            }
        }
    }
}
