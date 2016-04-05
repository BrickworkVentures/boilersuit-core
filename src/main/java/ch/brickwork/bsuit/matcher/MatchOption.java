package ch.brickwork.bsuit.matcher;


import java.util.Hashtable;
import java.util.Set;

class MatchOption {

    public static final int OPT_LEGASTODETECT = 1;

    public static final int OPT_SUPPRESSSECONDBEST = 2;

    public static final int OPT_THRESHOLD = 3;

    public static final int OPT_DISPLAYLEFT = 4;

    public static final int OPT_DISPLAYRIGHT = 5;

    private final Hashtable<String, Object> arguments;

    private final int type;

    public MatchOption(final int type, final Hashtable<String, Object> arguments)
    {
        this.arguments = arguments;
        this.type = type;
    }

    /**
     * match option without arguments, i.e. match "flag"
     *
     * @param type is option type
     */
    public MatchOption(final int type)
    {
        this(type, null);
    }


    public Set<String> getArgumentKeys()
    {
        return arguments != null ? arguments.keySet() : null;
    }

    /**
     * @return arguments, or null, if it is a match flag
     */

    public Hashtable<String, Object> getArguments()
    {
        return arguments;
    }

    public int getType()
    {
        return type;
    }
}
