package ch.brickwork.bsuit.database;

import ch.brickwork.bsuit.util.OrderedHashTable;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

/**
 * BoilerSuits own representation of a record from a database table or view. A record is a list of Value objects,
 * each of them entered under a specific "attribute name"
 */
public class Record implements Iterable<Value> {

    private OrderedHashTable<Value> values = new OrderedHashTable<>(true);

    /**
     * @return string array with column names in this record
     */

    public String[] getColumnNames() {
        final String[] columnNames = new String[values.size()];

        for (int i = 0; i < values.size(); i++) {
            columnNames[i] = values.get(i).getAttributeName();
        }

        return columnNames;
    }

    /**
     * @return the first value of the first record, e.g. if this record is the
     * result of a select count(x) statement, where you know its gonna deliver
     * exactly one value and no more (or you'll want the very first one anyhow).
     * If the record is empty, null is returned.
     */
    public Object getFirstValueContent() {
        if (countValues() > 0) {
            return values.get(0).getValue();
        } else {
            return null;
        }
    }

    /**
     * @param attributeName name of attribute whose value should be found. case sensitive
     *
     * @return value with name "attributeName"
     */

    public Value getValue(String attributeName)
    {
        return values.get(attributeName);
    }

    /**
     * @return number of values stored in this record
     */
    public int countValues()
    {
        return values.size();
    }

    /**
     * @param attributeName name of table attribute
     *
     * @return true, if this record contains a value with attribute name "attributeName"
     */
    public boolean hasAttribute(String attributeName)
    {
        return values.get(attributeName) != null;
    }

    /**
     * Iterates over the values contained in this record
     *
     * @return Iterator
     */
    @Override
    public Iterator<Value> iterator()
    {
        return values.getValuesIterator();
    }

    /**
     * if attribute does not yet exist, adds a new one. If exists, changes current value to "value"
     *
     * @param attributeName name of the attribute to add or update
     * @param value         value that the attribute with name "attributeName" will have after calling
     */
    public void put(String attributeName, Object value) {
        final Value currentValue = values.get(attributeName);
        if (currentValue == null) {
            addValue(new Value(attributeName, value));
        } else {
            currentValue.setValue(value);
        }
    }

    /**
     * for debugging purposes, prints out the record's values on one line - or "empty", if no values contained
     *
     * @return Record to String
     */

    public String toString() {
        final StringBuilder outputLine = new StringBuilder("Record: ");
        if (getColumnNames().length == 0) {
            outputLine.append("empty");
        }
        boolean first = true;
        for (String columnName : getColumnNames()) {
            if (first) {
                first = false;
            } else {
                outputLine.append(", ");
            }

            outputLine.append(columnName);
            outputLine.append(": ");
            outputLine.append(this.getValue(columnName).getValue().toString());
        }
        return outputLine.toString();
    }

    public boolean equals(Record r) {
        return toString().equals(r.toString());
    }

    /**
     * Adds value.
     * Attribute name will be case sensitive if you want to find it again using getValue() method.
     *
     * @param value is a Value object which will be added to HashTable
     */
    private void addValue(Value value) {
        values.put(value.getAttributeName(), value);
    }
}
