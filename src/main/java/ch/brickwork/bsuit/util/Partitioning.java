package ch.brickwork.bsuit.util;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * User: marcel
 * Date: 5/21/13
 * Time: 9:18 AM
 */
public class Partitioning implements Iterable<Partition> {

    private final List<Partition> partitions;

    /**
     * For a set of elementCount elements (e.g., records), creates a list of partitions incl. one residual partition
     * at the end, such that none of the partitions is larger than partitionSize.   <br/>
     * <br/>
     * The first number within the first partition will be 0
     *
     * @param elementCount  size of elements
     * @param partitionSize size of partition
     */
    public Partitioning(final long elementCount, final int partitionSize)
    {
        // number of partitions needed excl. residual partition (i.e., =0 if there is all in one partition)
        long numberOfPartitionsOrZero = elementCount / partitionSize;

        partitions = new ArrayList<>();
        for (int partition = 0; partition < numberOfPartitionsOrZero; partition++) {
            partitions.add(new Partition(partition, partition * partitionSize, partitionSize));
        }
        long lastRecordInPartition = numberOfPartitionsOrZero * partitionSize - 1;
        if (lastRecordInPartition < elementCount - 1) {
            partitions.add(new Partition(numberOfPartitionsOrZero, lastRecordInPartition + 1, elementCount - lastRecordInPartition - 1));
        }
    }

    /**
     * @return count number of elements of all partitions contained in this partitioning
     */
    public int countElements()
    {
        int sum = 0;
        for (Partition p : partitions) {
            sum += p.getLength();
        }
        return sum;
    }

    /**
     * @return number of partitions within this partitioning
     */
    public int countPartitions()
    {
        return partitions.size();
    }

    @Override

    public Iterator<Partition> iterator()
    {
        return partitions.iterator();
    }
}
