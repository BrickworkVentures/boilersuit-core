package ch.brickwork.bsuit.interpreter.util;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

/**
 * Parses a part of a command in the form
 * WITH param1(argument11, ...,  argument1n), param2(argument21, ..., argument2n), ...
 * Creates a list of parameters, contains every argument.
 */
public class WithClauseParser {

    private final Hashtable<String, String> parametersLowerCase = new Hashtable<>();

    private Hashtable<String, List<String>> parameters;

    private String withClause;

    public WithClauseParser(String withClause)
    {
        this.withClause = withClause;
    }


    public List<String> getArgumentsIgnoreCase(final String paramNameIgnoreCase)
    {
        final String paramNameLower = paramNameIgnoreCase.toLowerCase();
        final String paramName = parametersLowerCase.get(paramNameLower);
        if (paramName == null) {
            return null;
        } else {
            return parameters.get(paramName);
        }
    }

    /**
     * Fill the parameters(HashTable).
     * For instance when command ends like below:
     * ... with delim(';'), quote('\''), replacedelim('D'), replacequote('Q')
     * parameters will contains 'delim' as key and ';' as value,
     * 'quote' as key and ';' as value,
     * 'replacedelim' as key and 'D' as value,
     * and 'replacequote' as key and 'Q' as value.
     *
     * @return true if parsed successfully
     */
    public boolean parse()
    {
        withClause = withClause.trim();
        if (!withClause.toLowerCase().substring(0, 5).equals("with ")) {
            return false;
        }

        int pos = 5;
        int level = 0;
        String currentParameter = "";
        String currentTerm = "";
        List<String> currentArgumentList = new ArrayList<>();
        boolean inQuote = false;
        parameters = new Hashtable<>();
        while (pos < withClause.length()) {
            // if quoted content, write quotes (since they are part of the argument as well)
            // but remember and do ignore other steering characters until quote is closed
            if (withClause.charAt(pos) == '\"') {
                // at this point, earlier we had:
                // currentTerm += withClause.charAt(pos);
                // this would include the quotes in the parameter; so if the parameter is a String,
                // the client of WithClauseParser would have to "unquote" it to get the content.
                // Maybe in future we solve this differently, e.g. put without quotes, but have some
                // type property (e.g. not having Strings in the param list, but some Data Object class
                // which is able to differentiate on the types of the content (int, String, etc.)
                // then this would be the place to set the type
                pos++;
                inQuote = !inQuote;
                continue;
            }

            if (!inQuote) {
                if (withClause.charAt(pos) == '(') {
                    if (level < 1) {
                        currentParameter = currentTerm.trim();
                        currentArgumentList = new ArrayList<>();
                        currentTerm = "";
                        level++;
                    } else
                    // no brackets within brackets allowed
                    {
                        return false;
                    }
                } else if (withClause.charAt(pos) == ')') {
                    if (level == 1) {
                        level--;
                        currentArgumentList.add(currentTerm);
                        currentTerm = "";
                        parameters.put(currentParameter, currentArgumentList);
                        parametersLowerCase.put(currentParameter.toLowerCase(), currentParameter);
                    } else
                    // don't the hell know what's going on here...
                    {
                        return false;
                    }
                } else if (withClause.charAt(pos) == ',') {
                    if (level == 0) {
                        currentTerm = "";
                    } else {
                        currentArgumentList.add(currentTerm.trim());
                        currentTerm = "";
                    }
                } else {
                    currentTerm += withClause.charAt(pos);
                }
            } else {
                currentTerm += withClause.charAt(pos);
            }

            pos++;
        }

        return true;
    }
}
