package ch.brickwork.bsuit.matcher;

import ch.brickwork.bsuit.util.TextUtils;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

public class MatchArguments {

    private final List<String> leftAttributes;

    private final List<String> rightAttributes;

    private List<MatchOption> matchOptions = new ArrayList<>();

    /**
     * @param leftString  is a text between the brackets near the left table
     * @param rightString is a text between the brackets near the right table
     */
    private MatchArguments(final String leftString, final String rightString)
    {
        leftAttributes = new ArrayList<>();
        rightAttributes = new ArrayList<>();
        buildArgumentsHash(leftString, leftAttributes);
        buildArgumentsHash(rightString, rightAttributes);
    }

    /**
     * @param leftAttributesString  is a text between the brackets near the left table
     * @param rightAttributesString is a text between the brackets near the right table
     * @param withString            is a text contains additional matching options e.g. THRESHOLD(0.9)
     */
    public MatchArguments(final String leftAttributesString, final String rightAttributesString, final String withString)
    {
        this(leftAttributesString, rightAttributesString);
        if (withString != null) {
            matchOptions = buildMatchOptions(withString);
        }
    }


    public List<String> getLeftAttributes()
    {
        return leftAttributes;
    }

    /**
     * returns the option of type lookForOptionType, if exists, or null othwise
     * @param lookForOptionType
     * @return
     */
    public MatchOption getOption(int lookForOptionType) {
        for(MatchOption mo : matchOptions)
            if(mo.getType() == lookForOptionType)
                return mo;
        return null;
    }


    public List<MatchOption> getOptions()
    {
        return matchOptions;
    }


    public List<String> getRightAttributes()
    {
        return rightAttributes;
    }

    public boolean isInitSuccess()
    {
        return matchOptions != null;
    }

    /**
     * from something like attribute1{property1=xy, ... propertyn=zz},
     * attribute2{...} this makes a hashtable attribute_name -> assignmentHash,
     * where assignementHash = propertyName -> propertyValue
     * <p/>
     * MATCH y(attr) ON x(attr2)
     */
    private void buildArgumentsHash(final String argumentsString, final List<String> attributeList)
    {
        final String[] naiveTokens = argumentsString.split(",");
        Hashtable<String, String> arguments = null;
        for (String token : naiveTokens) {
            String attributeName;

            // begin argument list for this attribute (init arguments list)
            if (token.contains("{")) {
                // start subarguments
                attributeName = token.substring(0, token.indexOf("{"));
                attributeList.add(attributeName);
                arguments = new Hashtable<>();
            }

            // add last element to arguments list and put it to the arguments
            // hash
            else if (token.contains("}")) {
                // close subarguments
                final String assignment = token.substring(0, token.indexOf("}"));
                final String[] splitArguments = assignment.split("=");
                if (null != arguments && splitArguments.length > 1) {
                    arguments.put(splitArguments[0], splitArguments[1]);
                }
            }

            // add inner element to arguments list (if more than one attribute)
            else if (token.contains(",")) {
                final String assignment = token.substring(0, token.indexOf(","));
                final String[] splitArguments = assignment.split("=");
                if (null != arguments && splitArguments.length > 1) {
                    arguments.put(splitArguments[0], splitArguments[1]);
                }
            }

            // else (for elements without arguments), do nothing but add
            // attribute
            else {
                attributeList.add(token.trim());
            }
        }
    }

    /**
     * from a string like abcd, 123, blabla this builds a hashtable with entries {abcd=abcd, 123=123, blabla=blabla}
     * @param argumentString
     * @return hashtable, or null if either the argumentString is null or if the number of CSV is 0
     */
    private Hashtable<String, Object> buildHtFromCSVArguments(String argumentString)
    {
        if (argumentString == null) {
            return null;
        }

        String[] tokens = argumentString.split(",", -1);

        if (tokens.length == 0) {
            return null;
        }

        final Hashtable<String, Object> ht = new Hashtable<>();
        for (String token : tokens) {
            ht.put(token.trim(), token.trim());
        }
        return ht;
    }

    /**
     * @param withString is a text contains additional matching options e.g. THRESHOLD(0.9)
     *
     * @return the list of matching options parsed from given argument
     */

    private List<MatchOption> buildMatchOptions(final String withString)
    {
        List<MatchOption> matchOptions = new ArrayList<>();
        for (String token : TextUtils.splitIntoOuterArguments(withString, '(', ')', ',')) {
            String argument = token.trim();

            if (argument.contains("legastodetect")) {
                final Hashtable<String, Object> legastoList = new Hashtable<>();
                final String between = TextUtils.between(argument, "legastodetect(", ")");
                if (between != null) {
                    for (String legastoArg : between.split(",")) {
                        legastoList.put(legastoArg.trim(), "");
                    }
                }
                matchOptions.add(new MatchOption(MatchOption.OPT_LEGASTODETECT, legastoList));
            }

            if (argument.contains("suppresssecondbest")) {
                matchOptions.add(new MatchOption(MatchOption.OPT_SUPPRESSSECONDBEST));
            }

            if (argument.contains("threshold")) {
                final String thresholdString = TextUtils.between(argument, "threshold(", ")");
                final Hashtable<String, Object> ht = new Hashtable<>();
                Double threshold;
                try {
                    threshold = new Double(thresholdString);
                    ht.put("threshold", threshold);
                } catch (NumberFormatException e) {
                    return null;
                }
                matchOptions.add(new MatchOption(MatchOption.OPT_THRESHOLD, ht));
            }

            if (argument.contains("displayleft")) {
                String argumentString = TextUtils.between(argument.toLowerCase(), "displayleft(", ")");
                matchOptions.add(new MatchOption(MatchOption.OPT_DISPLAYLEFT, buildHtFromCSVArguments(argumentString)));
            }

            if (argument.contains("displayright")) {
                String argumentString = TextUtils.between(argument.toLowerCase(), "displayright(", ")");
                matchOptions.add(new MatchOption(MatchOption.OPT_DISPLAYRIGHT, buildHtFromCSVArguments(argumentString)));
            }
        }

        return matchOptions;
    }
}
