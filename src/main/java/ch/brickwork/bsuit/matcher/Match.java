package ch.brickwork.bsuit.matcher;

import ch.brickwork.bsuit.database.Record;


import java.util.Hashtable;
import java.util.Set;

/**
 * Class used for carrying the records used in match processing.
 * Left record is compared with the right.
 */
public class Match {

    private final Record leftRecord;

    private final Hashtable<String, Boolean> legasthenicMatches = new Hashtable<>();

    private final Hashtable<String, Boolean> partialExactMatches = new Hashtable<>();

    private final Record rightRecord;

    private double diceScore = 0;

    private double jaroScore = 0;

    private double jaroWinklerScore = 0;

    private String leftKey;

    private String rightKey;

    /**
     * @param leftRecord  is a record from left table
     * @param rightRecord is a record from right table
     */
    public Match(final Record leftRecord, final Record rightRecord)
    {
        this.leftRecord = leftRecord;
        this.rightRecord = rightRecord;
    }

    public double getDiceScore()
    {
        return diceScore;
    }

    public void setDiceScore(double score)
    {
        diceScore = score;
    }

    public double getJaroScore()
    {
        return jaroScore;
    }

    public void setJaroScore(double score)
    {
        jaroScore = score;
    }

    public double getJaroWinklerScore()
    {
        return jaroWinklerScore;
    }

    public void setJaroWinklerScore(double score)
    {
        jaroWinklerScore = score;
    }

    public String getLeftKey()
    {
        return leftKey;
    }

    public void setLeftKey(final String leftKey)
    {
        this.leftKey = leftKey;
    }

    public Record getLeftRecord()
    {
        return leftRecord;
    }

    public Hashtable<String, Boolean> getLegasthenicMatches()
    {
        return legasthenicMatches;
    }

    public Set<String> getPartialExactKeys()
    {
        return partialExactMatches.keySet();
    }

    public String getRightKey()
    {
        return rightKey;
    }

    public void setRightKey(final String rightKey)
    {
        this.rightKey = rightKey;
    }

    public Record getRightRecord()
    {
        return rightRecord;
    }

    public void setPartialExactMatch(final String key, boolean match)
    {
        partialExactMatches.put(key, match);
    }

    public void addLegasthenicMatch(String string)
    {
        legasthenicMatches.put(string, true);
    }

    public boolean isExact()
    {
        for (final String key : partialExactMatches.keySet()) {
            if (!partialExactMatches.get(key)) {
                return false;
            }
        }

        return true;
    }

    public boolean isPartialExactMatch(final String key)
    {
        return partialExactMatches.get(key);
    }
}
