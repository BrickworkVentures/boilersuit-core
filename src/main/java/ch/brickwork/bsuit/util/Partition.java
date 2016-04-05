package ch.brickwork.bsuit.util;

/**
 * Part of results given from Database.
 * This class helps carrying the large sets of records.
 */
public class Partition {

    private final long firstRecord;

    private final long length;

    private final long number;

    public Partition(long number, long firstRecord, long length)
    {
        this.number = number;
        this.firstRecord = firstRecord;
        this.length = length;
    }

    public long getFirstRecord()
    {
        return firstRecord;
    }

    public long getLength()
    {
        return length;
    }

    public long getNumber()
    {
        return number;
    }
}
