package ch.brickwork.bsuit.interpreter.interpreters;



import java.util.Hashtable;

/**
 * Used in {@link TableExpressionInterpreter}
 * Carrying some of parsed assignments to further processing.
 */
class PreProcessInstruction {

    private final Hashtable<String, String> arguments;

    private final String name;

    private final String param;

    public PreProcessInstruction(final String name, final String param) {
        this.name = name;
        this.param = param;
        this.arguments = new Hashtable<>();
    }


    public String getArgument(final String arg)
    {
        return arguments.get(arg);
    }


    public String getName()
    {
        return name;
    }


    public String getParam()
    {
        return param;
    }

    public void addArgument(final String arg, final String value)
    {
        arguments.put(arg, value);
    }

    public Hashtable<String, String> getArguments() {
        return arguments;
    }
}
