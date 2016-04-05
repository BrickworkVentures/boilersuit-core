package ch.brickwork.bsuit.matcher;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Set of exact or non exact match result.
 * Used in MagicMatcher to prepare the match tables.
 */
class MatchSet implements Iterable<Match> {

    private final List<Match> matches = new ArrayList<>();

    /**
     * @return the best non exact match
     */
    public Match getBestNonExactMatch()
    {
        Match bestMatch = null;
        for (Match m : matches) {
            if (bestMatch == null) {
                bestMatch = m;
            } else if (bestMatchMetric(m) > bestMatchMetric(bestMatch)) {
                bestMatch = m;
            }
        }
        return bestMatch;
    }

    /**
     * the function used to sort matches according to their quality in getBestMatch
     * @param m
     * @return
     */
    private double bestMatchMetric(Match m) {
        return (m.getDiceScore() + m.getJaroScore() + m.getJaroWinklerScore()) / 3;
    }

    /**
     * @return a list of matches where the match is equal
     */

    public List<Match> getExactMatches()
    {
        List<Match> exactMatches = new ArrayList<>();
        for (Match m : matches) {
            if (m.isExact()) {
                exactMatches.add(m);
            }
        }
        return exactMatches;
    }

    /**
     * @return a list of matches where the match is NOT exact and at least one
     * of the scores is higher (strictly higher) than "generalThreshold". If
     * none like this are found, an empty list is returned.
     */

    public List<Match> getNonExactMatches(final double generalThreshold)
    {
        final List<Match> nonExactMatches = new ArrayList<>();
        for (final Match m : matches) {
            if (!m.isExact() && (m.getDiceScore() > generalThreshold || m.getJaroScore() > generalThreshold || m.getJaroWinklerScore() > generalThreshold)) {
                nonExactMatches.add(m);
            }
        }
        return nonExactMatches;
    }

    public void addMatch(final Match match)
    {
        matches.add(match);
    }

    @Override

    public Iterator<Match> iterator()
    {
        return matches.iterator();
    }
}
