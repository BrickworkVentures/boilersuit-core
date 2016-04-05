package ch.brickwork.bsuit.database;

/**
 * value in a Record. This can be used to handle data conversion stuff later on.
 */
public class Value {

    private final String attributeName;

    private Object value;

    public Value(final String attributeName, final Object value) {
        this.attributeName = attributeName;
        this.value = value;
    }

    public String getAttributeName()
    {
        return attributeName;
    }

    public Object getValue()
    {
        return value;
    }

    public void setValue(final Object value)
    {
        this.value = value;
    }
}
