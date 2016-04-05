package ch.brickwork.bsuit.util;



import java.util.ArrayList;
import java.util.List;

/**
 * Utility class helps with create SQL statements
 * <p/>
 * User: marcel
 * Date: 5/31/13
 * Time: 11:09 AM
 */
public final class TextUtils {
    /**
     * Returns chunk of text between first occurrence of "betweenThis" and
     * first occurrence of "andThis" in the text "text", such that neither
     * "betweenThis" nor "andThis" are part of the result. If "andThis"==null,
     * then the whole text between first occurrence of "betweenThis" until the
     * end of the text is returned. If betweenThis is not in the text, null
     * is returned. If betweenThis is null, and andThis not, it returns the
     * text from the beginning until just before the first occurrence of
     * andThis (or null if andThis is not found).
     *
     * @return text between, or null if either betweenThis is not found, or if
     * andThis is not null and not found
     */

    public static String between(final String text, final String betweenThis, final String andThis) {
        if (betweenThis == null && andThis != null) {
            if (!text.contains(andThis)) {
                return null;
            } else {
                return text.substring(0, text.indexOf(andThis));
            }
        }
        if (null != betweenThis) {
            if (!text.contains(betweenThis)) {
                return null;
            }

            if (andThis == null) {
                return text.substring(text.indexOf(betweenThis) + betweenThis.length());
            } else {
                if (!text.contains(andThis)) {
                    return null;
                }
                return text.substring(text.indexOf(betweenThis) + betweenThis.length(), text.indexOf(andThis));
            }
        } else {
            return null;
        }
    }

    /**
     * @return the text between the first occurrence of "betweenFirstThis" and
     * the last occurrence of "andLastThis". If any one of them isn't found,
     * returns null
     */

    public static String betweenFirstLast(final String text, final String betweenFirstThis, final String andLastThis) {
        int firstIndex = 0;
        if (betweenFirstThis != null) {
            firstIndex = text.indexOf(betweenFirstThis);
        }
        if (firstIndex < 0) {
            return null;
        }

        int lastIndex = -1;
        do {
            if (andLastThis != null) {
                lastIndex = text.indexOf(andLastThis, lastIndex + 1);
            }
        } while (lastIndex != -1 && lastIndex + 1 < text.length() && text.indexOf(andLastThis, lastIndex + 1) != -1
            && text.indexOf(andLastThis, lastIndex + 1) != lastIndex);

        if (lastIndex < 0) {
            return null;
        }

        if (firstIndex + 1 >= text.length()) {
            return null;
        } else {
            return text.substring(firstIndex + 1, lastIndex);
        }
    }

    /**
     * @return counts the number of occurrences of string "findThis" in string "findIn"
     */
    public static int count(final String findIn, final String findThis) {
        int count = 0;
        int currentIndex = 0;
        while (currentIndex < findIn.length() && (currentIndex = findIn.indexOf(findThis, currentIndex)) != -1) {
            count++;
            currentIndex++;
        }
        return count;
    }

    /**
     * @param text           is a text which will be processed
     * @param findCharacter  is a char will be looking for
     * @param onLevel        is a level of bracket
     * @param openDelimiter  is a character for opening level
     * @param closeDelimiter is a character for closing level
     *
     * @return counts the occurrence of character 'findCharacter' on level 'onLevel'. E.g. for text<br/>
     * A(x) + B(x) + C(hash(x + y)),<br/> the<br/>
     * level 0 count is 2,<br/>
     * level 1 count is 0, and <br/>
     * level 2 count is 1 (within hash function)
     */
    public static int countLevelOccurrence(final String text, final char findCharacter, final int onLevel, final char openDelimiter,
                                           final char closeDelimiter) {
        int count = 0;
        int level = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == openDelimiter) {
                level++;
            } else if (text.charAt(i) == closeDelimiter) {
                level--;
            }
            if (text.charAt(i) == findCharacter && level == onLevel) {
                count++;
            }
        }
        return count;
    }

    /**
     * From a list l = {"A", "B", "C"}, serializeListWithDelimitator(l, "***") will
     * make "A***B***C". To concatenate the tokens and delimitators,
     * the += operator is used.
     *
     * @param items       used for concatenate
     * @param delimitator put between tokens
     *
     * @return text contains tokens
     */

    public static String serializeListWithDelimitator(final List<String> items, final String delimitator) {
        return serializeListWithDelimitator(items, delimitator, "");
    }

    /**
     * Like serialiseWithDelimitator, but adding a prefix before each item in the serialized result.
     * From a list l = {"A", "B", "C"}, serializeListWithDelimitator(l, "***", "prefix")
     * result will be "prefixA***prefixB***prefixC"
     *
     * @param items       used for concatenate
     * @param delimitator put between tokens
     * @param prefix      put before each token
     *
     * @return text contains tokens
     */

    public static String serializeListWithDelimitator(final List<String> items, final String delimitator, final String prefix) {
        return serializeListWithDelimitator(items, delimitator, prefix, "");
    }

    /**
     * Like serialiseWithDelimitator, but adding a prefix before each item in the serialized result.
     * From a list l = {"A", "B", "C"}, serializeListWithDelimitator(l, "***", "prefix", "postfix")
     * result will be "prefixApostfix***prefixBpostfix***prefixCpostfix"
     * <p/>
     * Put keyword <item> within prefix or postfix and it will be replaced by item name
     *
     * @param items       used for concatenate
     * @param delimitator put between tokens
     * @param prefix      put before each token
     *
     * @return text contains tokens
     */

    public static String serializeListWithDelimitator(final List<String> items, final String delimitator, final String prefix,
                                                      final String postfix) {
        String serializedString = "";
        boolean first = true;
        String newPrefix;
        String newPostfix;
        for (String item : items) {
            if (first) {
                first = false;
            } else {
                serializedString += delimitator;
            }
            newPrefix = prefix.replaceAll("<item>", item);
            newPostfix = postfix.replaceAll("<item>", item);
            serializedString += newPrefix + item + newPostfix;
        }

        return serializedString;
    }

    /**
     * In a string like mainfunction(a, b, c, d, (a + function_blabla(x, y)), e,
     * function(f2(f3(x,y), f4(z, x)), x)), splitting by comma will not deliver
     * "mainfunction"'s arguments, it will also split up the inner arguments
     * like the x and y in function_blabla etc.<br/>
     * <br/>
     * Attention: The string must necessarily start from within the main brackets,
     * e.g. don't use myfunc(a, b(c), d) or even (a, b(c), d) - always call with
     * a, b(c), d
     * <br/>
     * This method will pass strictly the "outer arguments", i.e. in the example
     * above, you will want to call with string "a, b, c, d, ..." and the method
     * will deliver:<br>
     * <p/>
     * a<br>
     * b<br>
     * c<br>
     * d<br>
     * (a + function_blabla(x, y))<br>
     * e<br>
     * function(f2(f3(x,y), f4(z, x))<br>
     * etc.
     *
     * @param string string to be splitted, starting with FIRST CHARACTER within main bracket!
     *
     * @return list of arguments splitted from string
     */

    public static List<String> splitIntoOuterArguments(final String string, final char openDelimiter, final char closeDelimiter,
                                                       final char argumentDelimiter) {
        List<String> tokens = new ArrayList<>();
        int lastDelimIndex = -1;
        int level = 0;
        for (int i = 0; i < string.length(); i++) {
            if (string.charAt(i) == openDelimiter) {
                level++;
            } else if (string.charAt(i) == closeDelimiter) {
                level--;
            } else if (string.charAt(i) == argumentDelimiter) {
                if (level == 0) {
                    tokens.add(string.substring(lastDelimIndex + 1, i).trim());
                    lastDelimIndex = i;
                }
            }
            if (i == string.length() - 1) {
                tokens.add(string.substring(lastDelimIndex + 1).trim());
            }
        }
        return tokens;
    }
}
