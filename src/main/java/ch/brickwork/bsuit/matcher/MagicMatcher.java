package ch.brickwork.bsuit.matcher;

import ch.brickwork.bsuit.database.Record;
import ch.brickwork.bsuit.database.Value;
import ch.brickwork.bsuit.database.Variable;
import ch.brickwork.bsuit.globals.IBoilersuitApplicationContext;
import ch.brickwork.bsuit.interpreter.DefaultCommandInterpreter;
import ch.brickwork.bsuit.interpreter.ScriptProcessor;
import ch.brickwork.bsuit.interpreter.interpreters.MatchInterpreter;
import ch.brickwork.bsuit.interpreter.interpreters.ProcessingResult;
import ch.brickwork.bsuit.util.Partition;
import ch.brickwork.bsuit.util.Partitioning;
import ch.brickwork.bsuit.util.TextUtils;
import net.ricecode.similarity.DiceCoefficientStrategy;
import net.ricecode.similarity.JaroStrategy;
import net.ricecode.similarity.JaroWinklerStrategy;
import java.util.*;


/**
 * Used in {@link MatchInterpreter} and takes care for whole process of matching.
 */
public class MagicMatcher {

    private static final int MAX_MATCH_BUFFER_RECORDS = 5000;

    private static final int PARTITION_SIZE = 10000;

    private static final double THRESHOLD = -1;
    
    private final String leftTableAttrPrefix;

    private final Variable leftVariable;

    private final IBoilersuitApplicationContext context;

    private final MatchArguments matchArguments;

    private final String rightTableAttrPrefix;

    private final Variable rightVariable;

    private final String targetTableName;

    private final String targetVariableName;

    private final List<String> temporaryTables = new ArrayList<>();

    /**
     * left attributes name -> exact match flag name for this attribute
     */
    private Hashtable<String, String> exactAttributeNameMap;

    /**
     * temporary solution - should be introduced as a matching option! if yes, means that once an exact match
     * is found, the corresponing right entry will be removed such that it is not available for other left items
     * to match with
     */
    private boolean greedyLeft = false;

    /**
     * temporary solution - should be introduced as a matching option! if false, means that once an exact match
     * is found, the corresponing left entry will not again be checked because it is already matching exactly. If it
     * is hungry, then even if we already have an exact match, it will further look out for inexact ones
     */
    private boolean hungryLeft = false;

    private boolean initialized = false;

    private String leftKeyAttributeNameTarget;

    private List<String> leftTableColumnNamesMatchRelevant;

    /**
     * writing matches
     */
    private List<Record> matchBuffer = null;

    private Hashtable<MatchOption, String> matchOptionAttributeNameMap;

    private String matchTemporaryIdentifier;

    private String reducedLeftSetName;

    private String reducedRightSetName;

    private String rightKeyAttributeNameTarget;

    private List<String> rightTableColumnNamesMatchRelevant;

    /**
     * match options
     */
    private boolean suppressSecondBest = false;

    private double threshold = THRESHOLD;

    /**
     * Invoked by {@link MatchInterpreter}
     *
     * @param leftVariable       left variable for matching
     * @param rightVariable      right variable for matching
     * @param matchArguments     arguments of match command
     * @param targetVariableName is name of target variable
     * @param targetTableName    is name of target table (mostly the same as target variable)
     * @param context                is a instance of logger
     */
    public MagicMatcher(final Variable leftVariable, final Variable rightVariable, final MatchArguments matchArguments,
                        final String targetVariableName, final String targetTableName, final IBoilersuitApplicationContext context)
    {
        this.leftVariable = leftVariable;
        this.rightVariable = rightVariable;
        this.matchArguments = matchArguments;
        this.targetTableName = targetTableName;
        this.targetVariableName = targetVariableName;
        this.context = context;
        leftTableAttrPrefix = leftVariable.getTableName() + "_";
        rightTableAttrPrefix = rightVariable.getTableName() + "_";
        init();
    }

    /**
     * Invoked by {@link MatchInterpreter}
     * Creates the sub results which have the fuzzy and the exact matching results.
     *
     * @return ProcessingResult
     */

    public ProcessingResult match()
    {
        final ProcessingResult processingResult = new ProcessingResult(ProcessingResult.ResultType.COMPOSITE, "Match result");

        if (!initialized) {
            return null;
        }

        processingResult.addSubResult(exactMatch());
        processingResult.addSubResult(fuzzyMatch());

        removeTemporaryTables();

        return processingResult;
    }

    /**
     * @param tableName            is name of table which will be created for match results
     * @param additionalAttributes is a list of names for additional attributes which will be added to created table
     */
    private void createMatchTable(final String tableName, final List<String> additionalAttributes)
    {
        context.getDatabase().dropIfExistsViewOrTable(tableName);
        context.getDatabase().createOrReplaceVariable(tableName, "Matching " + leftVariable.getVariableName() + " on " + rightVariable.getVariableName(), "");

        if (leftVariable.getTableName().equalsIgnoreCase(rightVariable.getTableName())) {
            return;
        }

        context.getDatabase().createCombinedTable(leftTableColumnNamesMatchRelevant, rightTableColumnNamesMatchRelevant, leftVariable.getVariableName(),
            rightVariable.getVariableName(), additionalAttributes, tableName);
    }

    /**
     * Prepare the temporary tables used in this process (and remove them after process is finished) and take care of all of steps of exact matching.
     *
     * @return ProcessingResult of exact matching.
     */

    private ProcessingResult exactMatch()
    {
        context.getLog().info("First, looking for exact matches (ignoring whitespaces, i.e. XY 123 will match X Y123 and XY123)...");

        // concatenate attribute names of left and right keys
        final String leftKeyConcatenation = TextUtils.serializeListWithDelimitator(matchArguments.getLeftAttributes(), "|| '_' ||", "replace(", ", ' ', '')");
        final String rightKeyConcatenation = TextUtils.serializeListWithDelimitator(matchArguments.getRightAttributes(), "|| '_' ||", "replace(", ", ' ', '')");

        // create view containing all attributes + the concatenated combined key (left)
        final String leftTempViewName = matchTemporaryIdentifier + "_left_all_plus_key";
        temporaryTables.add(leftTempViewName);
        final String leftTempKeyAttributeName = leftVariable.getVariableName() + "_match_key";
        context.getDatabase().dropIfExistsViewOrTable(leftTempViewName);
        final String leftAttributesConcatenationWithAlias = TextUtils.serializeListWithDelimitator(leftTableColumnNamesMatchRelevant, ", ", "",
            " as " + leftVariable.getTableName() + "_<item>");
        final String rightAttributesConcatenationWithAlias = TextUtils.serializeListWithDelimitator(rightTableColumnNamesMatchRelevant, ", ", "",
            " as " + rightVariable.getTableName() + "_<item>");
        final String leftAttributesConcatenation = TextUtils.serializeListWithDelimitator(leftTableColumnNamesMatchRelevant, ", ",
            leftVariable.getTableName() + "_", "");
        final String rightAttributesConcatenation = TextUtils.serializeListWithDelimitator(rightTableColumnNamesMatchRelevant, ", ",
            rightVariable.getTableName() + "_", "");

        String createLeftTempView = "CREATE TABLE " + leftTempViewName + " AS SELECT " + leftKeyConcatenation + " AS " + leftTempKeyAttributeName + ", "
            + leftAttributesConcatenationWithAlias + " FROM " + leftVariable.getVariableName() + " WHERE " + leftKeyConcatenation + " <> ''";
        final ProcessingResult pLeft = new DefaultCommandInterpreter(null, createLeftTempView, context).process();

        if (!(pLeft.getType() == ProcessingResult.ResultType.VIEW || pLeft.getType() == ProcessingResult.ResultType.TABLE)) {
            return new ProcessingResult(ProcessingResult.ResultType.FATAL_ERROR, "Trouble creating left hand table for exact match!");
        }
        context.getDatabase().createIndex(leftTempViewName, leftTempKeyAttributeName, leftTempViewName + "_" + leftTempKeyAttributeName + "_index");

        // create view containing all attributes + the concatenated combined key (right)
        final String rightTempViewName = matchTemporaryIdentifier + "_right_all_plus_key";
        temporaryTables.add(rightTempViewName);
        final String rightTempKeyAttributeName = rightVariable.getVariableName() + "_match_key";
        context.getDatabase().dropIfExistsViewOrTable(rightTempViewName);
        final String createRightTempView =
            "CREATE TABLE " + rightTempViewName + " AS SELECT " + rightKeyConcatenation + " AS " + rightTempKeyAttributeName + ", "
                + rightAttributesConcatenationWithAlias + " FROM " + rightVariable.getVariableName() + " WHERE " + rightKeyConcatenation + " <> ''";
        final ProcessingResult pRight = new DefaultCommandInterpreter(null, createRightTempView, context).process();
        if (!(pRight.getType() == ProcessingResult.ResultType.VIEW || pRight.getType() == ProcessingResult.ResultType.TABLE)) {
            return new ProcessingResult(ProcessingResult.ResultType.FATAL_ERROR, "Trouble creating right hand table for exact match!");
        }
        context.getDatabase().createIndex(rightTempViewName, rightTempKeyAttributeName, rightTempViewName + "_" + rightTempKeyAttributeName + "_index");

        // create inner join to see which ones are exactly matching
        final String leftAlias = matchTemporaryIdentifier + "_left_alias";
        final String rightAlias = matchTemporaryIdentifier + "_right_alias";

        final String innerJoin =
            "CREATE TABLE " + getExactMatchesTableName() + " AS SELECT " + leftAttributesConcatenation + ", " + rightAttributesConcatenation + ", " +
                leftTempKeyAttributeName + ", " + rightTempKeyAttributeName + " FROM " + leftTempViewName + " " + leftAlias + " INNER JOIN " + rightTempViewName
                + " " + rightAlias + " ON " + leftAlias + "." + leftTempKeyAttributeName + "=" + rightAlias + "." + rightTempKeyAttributeName;
        final ProcessingResult pJoin = new ScriptProcessor().processScript(getExactMatchesTableName() + ":=" + innerJoin, null, null);
        if (!(pJoin.getType() == ProcessingResult.ResultType.TABLE || pJoin.getType() == ProcessingResult.ResultType.VIEW)) {
            return new ProcessingResult(ProcessingResult.ResultType.FATAL_ERROR, "Trouble creating inner join for exact match!");
        }

        final String joinResultTable = pJoin.getResultSummary();
        context.getLog().log("Join Result in : " + joinResultTable);

        context.getLog().log("Exact matches: " + context.getDatabase().count(joinResultTable) + " of " + context.getDatabase().count(leftVariable.getTableName()));

        context.getDatabase().dropIfExistsViewOrTable(matchTemporaryIdentifier + "_reduced_left");
        context.getDatabase().dropIfExistsViewOrTable(matchTemporaryIdentifier + "_reduced_right");
        final String reducedLeftTableName = matchTemporaryIdentifier + "_reduced_left";
        temporaryTables.add(reducedLeftTableName);
        String reducedLeftSQL =
            "CREATE TABLE " + reducedLeftTableName + " AS SELECT " + leftAttributesConcatenationWithAlias + " FROM " + leftVariable.getTableName();
        if (!hungryLeft) {
            reducedLeftSQL += " WHERE " + leftKeyConcatenation + " NOT IN (SELECT " + leftTempKeyAttributeName + " FROM " + getExactMatchesTableName() + ")";
        }
        final ProcessingResult reducedLeftSet = new DefaultCommandInterpreter(null, reducedLeftSQL, context).process();
        final String reducedRightTableName = matchTemporaryIdentifier + "_reduced_right";
        temporaryTables.add(reducedRightTableName);
        String reducedRightSQL =
            "CREATE TABLE " + reducedRightTableName + " AS SELECT " + rightAttributesConcatenationWithAlias + " FROM " + rightVariable.getTableName();
        if (greedyLeft) {
            reducedRightSQL += " WHERE " + rightKeyConcatenation + " NOT IN (SELECT " + rightTempKeyAttributeName + " FROM " + getExactMatchesTableName() + ")";
        }
        final ProcessingResult reducedRightSet = new DefaultCommandInterpreter(null, reducedRightSQL, context).process();
        if (reducedLeftSet.getType() == ProcessingResult.ResultType.TABLE && reducedRightSet.getType() == ProcessingResult.ResultType.TABLE) {
            reducedLeftSetName = reducedLeftSet.getResultSummary();
            reducedRightSetName = reducedRightSet.getResultSummary();
        } else {
            reducedLeftSetName = null;
            reducedRightSetName = null;
            context.getLog().err("Error in creating reduced sets in exact matching!");
        }

        return new ProcessingResult(ProcessingResult.ResultType.VIEW, joinResultTable);
    }

    /**
     * Check if two records are equal, but without case-sensitive
     *
     * @param additionalAttributes list of attributes which will be added to the results table
     */
    private void findExactMatches(List<String> additionalAttributes)
    {
        for (int i = 0; i < matchArguments.getLeftAttributes().size(); i++) {
            final String leftName = matchArguments.getLeftAttributes().get(i);
            final String rightName = matchArguments.getRightAttributes().get(i);

            // if the have the same name X, the exact match flag will be called exact_X,
            // otherwise exact_X_Y
            String exactMatchName = "match_exact_" + leftName.trim();
            if (!leftName.trim().equalsIgnoreCase(rightName.trim())) {
                exactMatchName += "_" + rightName.trim();
            }

            additionalAttributes.add(exactMatchName);
            exactAttributeNameMap.put(leftTableAttrPrefix + leftName, exactMatchName);
        }
    }

    /**
     * Insert records to the database and clear buffer.
     */
    private void flushMatchBuffer()
    {
        context.getDatabase().insert(getFuzzyMatchesTableName(), matchBuffer);

        // empty buffer
        matchBuffer = new ArrayList<>();
    }

    /**
     * @return ProcessingResult of fuzzy matching
     */

    private ProcessingResult fuzzyMatch()
    {
        long lastRecordStartedTimestamp = System.currentTimeMillis();

        final Partitioning leftPartitioning = new Partitioning(context.getDatabase().count(reducedLeftSetName), PARTITION_SIZE);
        final Partitioning rightPartitioning = new Partitioning(context.getDatabase().count(reducedRightSetName), PARTITION_SIZE);

        context.getLog().log("Left  Partition with " + leftPartitioning.countElements() + " elements");
        context.getLog().log("Right Partition with " + rightPartitioning.countElements() + " elements");


        context.getLog().info("Left set will be divided into " + (leftPartitioning.countPartitions()) + " partitions to avoid memory problems.");
        context.getLog().info("Right set will be divided into " + (rightPartitioning.countPartitions()) + " partitions to avoid memory problems.");

        for (final Partition leftP : leftPartitioning) {
            // get left partition

            // process right partitions against this left one
            for (final Partition rightP : rightPartitioning) {

                // update performance calculation
                final long msecPerRightPartition = System.currentTimeMillis() - lastRecordStartedTimestamp;
                final long msecRemaining = msecPerRightPartition * (leftPartitioning.countPartitions() * rightPartitioning.countPartitions()
                    - leftP.getNumber() * rightPartitioning.countPartitions() - rightP.getNumber());
                final int seconds = (int) (msecRemaining / 1000) % 60;
                final int minutes = (int) ((msecRemaining / (1000 * 60)) % 60);
                final int hours = (int) ((msecRemaining / (1000 * 60 * 60)) % 24);

                final String message =
                    "Remaining " + hours + ":" + minutes + ":" + seconds + " (at " + (double) (msecPerRightPartition / 1000) + " sec per right partition)";

                lastRecordStartedTimestamp = System.currentTimeMillis();

                final String statusString =
                    "[" + (leftP.getFirstRecord() + 1 + "" + (leftP.getFirstRecord() + leftP.getLength())) + " of " + leftPartitioning.countElements() + "] ("
                        + (leftP.getNumber() + 1) + "/" + leftPartitioning.countPartitions() + ")" + " vs. " + "[" + (rightP.getFirstRecord() + 1 + "" + (
                        rightP.getFirstRecord() + rightP.getLength())) + " from " + rightPartitioning.countElements() + "] (" + (rightP.getNumber() + 1) + "/"
                        + rightPartitioning.countPartitions() + "). " + message;

                context.getLog().info(statusString);

                final List<Record> left = context.getDatabase().getAllRecordsFromTableOrView(reducedLeftSetName, leftP.getFirstRecord(), leftP.getLength(), null, null);
                final List<Record> right = context.getDatabase().getAllRecordsFromTableOrView(reducedRightSetName, rightP.getFirstRecord(), rightP.getLength(), null, null);

                if (null != left && null != right) {
                    match(left, right);
                }
            }
        }
        return new ProcessingResult(ProcessingResult.ResultType.TABLE, getFuzzyMatchesTableName());
    }


    private String getExactMatchesTableName()
    {
        return targetTableName + "_exact_matches";
    }


    private String getFuzzyMatchesTableName()
    {
        return targetTableName + "_fuzzy_matches";
    }

    private void init()
    {
        threshold = THRESHOLD;
        matchTemporaryIdentifier = targetTableName;
        initialized = prepareResultsTable();
    }

    /**
     * Prepare (calculate the values for different matching strategies) records for fuzzy matching table
     * and call a function that will take care of saving them to a right table.
     *
     * @param allLeft  all records from left table
     * @param allRight all records from right table
     */
    private void match(final List<Record> allLeft, final List<Record> allRight)
    {
        if (!initialized) {
            return;
        }

        for (final Record leftRecord : allLeft) {

            final MatchSet matches = new MatchSet();

            for (final Record rightRecord : allRight) {
                matchRightRecord(leftRecord, matches, rightRecord);
            }

            boolean hadExactMatches = false;
            for (final Match exactMatch : matches.getExactMatches()) {
                hadExactMatches = true;
                writeMatchTable(exactMatch);
            }

            if (!hadExactMatches) {
                if (suppressSecondBest) {
                    // write best, if any:
                    if (matches.getBestNonExactMatch() != null) {
                        writeMatchTable(matches.getBestNonExactMatch());
                    }
                } else {
                    // write all
                    for (final Match nonExactMatch : matches.getNonExactMatches(threshold)) {
                        // record as match (exact matches are all reported, inexact
                        // matches
                        writeMatchTable(nonExactMatch);
                    }
                }
            }
        }

        // write any residual matches out of buffer by flushing:
        if (matchBuffer != null) {
            flushMatchBuffer();
        }
    }

    /**
     * Uses different strategies to compare two records and calculate the match score
     *
     * @param leftRecord  record from the left table
     * @param matches     set of non exact matches
     * @param rightRecord record from the right table
     */
    private void matchRightRecord(Record leftRecord, MatchSet matches, Record rightRecord)
    {
        final Match thisMatch = new Match(leftRecord, rightRecord);
        matches.addMatch(thisMatch);

        // exact match
        boolean first = true;
        final StringBuilder leftCombinedKey = new StringBuilder();
        final StringBuilder rightCombinedKey = new StringBuilder();
        for (int i = 0; i < matchArguments.getLeftAttributes().size(); i++) {

            final String checkLeftAttribute = leftTableAttrPrefix + matchArguments.getLeftAttributes().get(i);

            // if same number of left and right attributes, test exact match:
            if (matchArguments.getLeftAttributes().size() == matchArguments.getRightAttributes().size()) {

                String againstRightAttribute = rightTableAttrPrefix + matchArguments.getRightAttributes().get(i);
                thisMatch.setPartialExactMatch(exactAttributeNameMap.get(checkLeftAttribute),
                    ((String) leftRecord.getValue(checkLeftAttribute).getValue()).trim()
                        .equals(((String) rightRecord.getValue(againstRightAttribute).getValue()).trim()));
            }

            if (first) {
                first = false;
            } else {
                leftCombinedKey.append("*");
            }

            leftCombinedKey.append(((String) leftRecord.getValue(checkLeftAttribute).getValue()).trim());
        }

        first = true;
        for (int i = 0; i < matchArguments.getRightAttributes().size(); i++) {
            final String againstRightAttribute = rightTableAttrPrefix + matchArguments.getRightAttributes().get(i);
            rightCombinedKey.append(((String) rightRecord.getValue(againstRightAttribute).getValue()).trim());
            rightCombinedKey.append("*");
            if (first) {
                first = false;
            } else {
                leftCombinedKey.append("*");
                rightCombinedKey.append("*");
            }
        }

        thisMatch.setLeftKey(leftCombinedKey.toString());
        thisMatch.setRightKey(rightCombinedKey.toString());

        if (!thisMatch.isExact()) {
            // check imprecise matches
            final JaroStrategy jaro = new JaroStrategy();
            final JaroWinklerStrategy jaroWinkler = new JaroWinklerStrategy();
            final DiceCoefficientStrategy diceCo = new DiceCoefficientStrategy();

            // left key
            final StringBuilder leftKey = new StringBuilder();
            first = true;
            for (String leftKeyComponent : matchArguments.getLeftAttributes()) {
                if (first) {
                    first = false;
                } else {
                    leftKey.append("*");
                }

                leftKey.append(leftRecord.getValue(leftTableAttrPrefix + leftKeyComponent).getValue());
            }

            // right key
            final StringBuilder rightKey = new StringBuilder();
            first = true;
            for (String rightKeyComponent : matchArguments.getRightAttributes()) {
                if (first) {
                    first = false;
                } else {
                    rightKey.append("*");
                }

                rightKey.append(rightRecord.getValue(rightTableAttrPrefix + rightKeyComponent).getValue());
            }

            thisMatch.setJaroScore(jaro.score(leftKey.toString(), rightKey.toString()));

            thisMatch.setJaroWinklerScore(jaroWinkler.score(leftKey.toString(), rightKey.toString()));

            thisMatch.setDiceScore(diceCo.score(leftKey.toString(), rightKey.toString()));

            // process WITH clauses
            for (final MatchOption matchOption : matchArguments.getOptions()) {

                final Hashtable<String, Object> arguments = matchOption.getArguments();
                    // LEGASTODETECT
                    if (matchOption.getType() == MatchOption.OPT_LEGASTODETECT) {
                        if(arguments == null) {
                            context.getLog().warn("LEGASTODETECT without arguments!");
                            return;
                        }

                        final Iterator<String> it = arguments.keySet().iterator();
                        final String firstSwapAttribute = it.next();
                        final String secondSwapAttribute = it.next();

                        // generate alternative left key, where the
                        // legasthenic keys are swapped:
                        final StringBuilder alternativeLeftKey = new StringBuilder();
                        first = true;
                        for (final String leftKeyComponent : matchArguments.getLeftAttributes()) {
                            if (first) {
                                first = false;
                            } else {
                                alternativeLeftKey.append("*");
                            }

                            if (leftKeyComponent.equals(firstSwapAttribute)) {
                                alternativeLeftKey.append(leftRecord.getValue(secondSwapAttribute).getValue());
                            } else if (leftKeyComponent.equals(secondSwapAttribute)) {
                                alternativeLeftKey.append(leftRecord.getValue(firstSwapAttribute).getValue());
                            } else {
                                alternativeLeftKey.append(leftRecord.getValue(leftKeyComponent).getValue());
                            }
                        }

                        if (alternativeLeftKey.toString().trim().equals(rightKey.toString().trim())) {
                            thisMatch.addLegasthenicMatch(this.matchOptionAttributeNameMap.get(matchOption));
                        }
                    }

                    // SUPPRESSSECONDBESTMATCHES
                    if (matchOption.getType() == MatchOption.OPT_SUPPRESSSECONDBEST) {
                        suppressSecondBest = true;
                    }

                    // THRESHOLD
                    if (matchOption.getType() == MatchOption.OPT_THRESHOLD) {
                        if(arguments == null) {
                            context.getLog().warn("THRESHOLD without arguments!");
                            return;
                        }

                        final Object thresholdObj = arguments.get("threshold");
                        if (null != thresholdObj) {
                            threshold = new Double(thresholdObj.toString());
                        }
                    }

            }
        }
    }

    /**
     * @param additionalAttributes list of attributes which will be added to the match results table
     * @param matchOption          object contains additional matching options e.g. THRESHOLD(0.9)
     */
    private void prepareLegastAttributes(List<String> additionalAttributes, MatchOption matchOption)
    {
        if (matchOption.getType() == MatchOption.OPT_LEGASTODETECT) {
            final StringBuilder legastResultAttributeName = new StringBuilder("swap_");
            boolean first = true;
            final Set<String> argumentKeys = matchOption.getArgumentKeys();
            if (null != argumentKeys) {
                for (String argumentKey : argumentKeys) {
                    if (first) {
                        first = false;
                    } else {
                        legastResultAttributeName.append("_");
                    }

                    legastResultAttributeName.append(argumentKey);
                }
            }
            legastResultAttributeName.append("_to_match");
            additionalAttributes.add(legastResultAttributeName.toString());
            matchOptionAttributeNameMap.put(matchOption, legastResultAttributeName.toString());
        }
    }

    /**
     * @param additionalAttributes list of attributes which will be added to the results table
     */
    private void prepareMatchKeys(List<String> additionalAttributes)
    {
        String leftKeyAttributeNameSource;
        String rightKeyAttributeNameSource;
        if (matchArguments.getLeftAttributes().size() > 1) {
            leftKeyAttributeNameTarget = leftVariable.getVariableName() + "_key";
            rightKeyAttributeNameTarget = rightVariable.getVariableName() + "_key";
            leftKeyAttributeNameSource = null;
            rightKeyAttributeNameSource = null;
        } else {
            leftKeyAttributeNameSource = matchArguments.getLeftAttributes().get(0);
            rightKeyAttributeNameSource = matchArguments.getRightAttributes().get(0);
            leftKeyAttributeNameTarget = leftVariable.getVariableName() + "_" + leftKeyAttributeNameSource;
            rightKeyAttributeNameTarget = rightVariable.getVariableName() + "_" + rightKeyAttributeNameSource;
        }
        final Hashtable<String, Integer> leftColumnNamesHash = context.getDatabase().getTableOrViewColumnNamesHash(leftVariable.getTableName());
        if (null != leftColumnNamesHash && ((leftKeyAttributeNameSource != null && leftColumnNamesHash.get(leftKeyAttributeNameSource) == null) || (
            leftKeyAttributeNameSource == null && leftColumnNamesHash.get("key") == null))) {
            additionalAttributes.add(leftKeyAttributeNameTarget);
        } else {
            context.getLog().warn("Overwriting existing attribute " + leftKeyAttributeNameTarget);
        }
        final Hashtable<String, Integer> rightColumnNamesHash = context.getDatabase().getTableOrViewColumnNamesHash(rightVariable.getTableName());
        if (null != rightColumnNamesHash && ((rightKeyAttributeNameSource != null && rightColumnNamesHash.get(rightKeyAttributeNameSource) == null) || (
            rightKeyAttributeNameSource == null && rightColumnNamesHash.get("key") == null))) {
            additionalAttributes.add(rightKeyAttributeNameTarget);
        } else {
            context.getLog().warn("Overwriting existing attribute " + rightKeyAttributeNameTarget);
        }
    }

    /**
     * Prepare the table contains fuzzy matching.
     *
     * @return true if results table was created
     */
    private boolean prepareResultsTable()
    {
        exactAttributeNameMap = new Hashtable<>();
        matchOptionAttributeNameMap = new Hashtable<>();

        /**
         * Create matching keys (if they are created based on more than one
         * component, then
         * there are additional; if the user indicates one specific attribute
         * set to be
         * used as match attributes, they are not additional (then they already
         * exist as normal attributes)
         */
        final List<String> additionalAttributes = new ArrayList<>();

        leftTableColumnNamesMatchRelevant = context.getDatabase().getSanitizedTableOrViewColumnNames(leftVariable.getTableName());
        removeNonRelevantAttributes(leftTableColumnNamesMatchRelevant, true);
        rightTableColumnNamesMatchRelevant = context.getDatabase().getSanitizedTableOrViewColumnNames(rightVariable.getTableName());
        removeNonRelevantAttributes(rightTableColumnNamesMatchRelevant, false);

        if (null == leftTableColumnNamesMatchRelevant || null == rightTableColumnNamesMatchRelevant) {
            context.getLog().err("Left or right column names are empty!");
            return false;
        }

        // create standard match attributes
        additionalAttributes.add("match_exact");
        additionalAttributes.add("match_dice");
        additionalAttributes.add("match_jaro");
        additionalAttributes.add("match_jarowinkler");

        // if same number of arguments on left and right side, track exact matches
        if (matchArguments.getLeftAttributes().size() == matchArguments.getRightAttributes().size()) {
            findExactMatches(additionalAttributes);
        }

        // additional match attributes depending on the options
        for (final MatchOption matchOption : matchArguments.getOptions()) {
            prepareLegastAttributes(additionalAttributes, matchOption);
        }

        if (matchArguments.getRightAttributes().size() == matchArguments.getLeftAttributes().size()) {
            prepareMatchKeys(additionalAttributes);
        }

        // create tables
        createMatchTable(getFuzzyMatchesTableName(), additionalAttributes);

        return true;
    }

    /**
     * Method remove attributes non relevant for match for given List of attribute names.
     *
     * @param tableColumnNames table attributes
     *
     * @return attributes non relevant for match
     */

    private List<String> removeNonRelevantAttributes(final List<String> tableColumnNames, final boolean left)
    {
        final Iterator<String> iterator = tableColumnNames.iterator();
        final List<String> nonRelevantAttributes = new ArrayList<>();
        final MatchOption displayLeftOption = matchArguments.getOption(MatchOption.OPT_DISPLAYLEFT);
        final MatchOption displayRightOption = matchArguments.getOption(MatchOption.OPT_DISPLAYRIGHT);
        while (iterator.hasNext()) {
            String columnName = iterator.next();

            // assume this is not relevant
            boolean isRelevant = false;

            // try to falsify above hypothesis by making isRelevant true
            isRelevant |= left && matchArguments.getLeftAttributes().contains(columnName);
            isRelevant |= !left && matchArguments.getRightAttributes().contains(columnName);
            isRelevant |= left && null != displayLeftOption && null != displayLeftOption.getArguments().get(columnName);
            isRelevant |= left && null != displayLeftOption && null != displayLeftOption.getArguments().get("*");
            isRelevant |= !left && null != displayRightOption && null != displayRightOption.getArguments().get(columnName);
            isRelevant |= !left && null != displayRightOption && null != displayRightOption.getArguments().get("*");

            if (!isRelevant) {
                nonRelevantAttributes.add(columnName);
                iterator.remove();
            }
        }
        return nonRelevantAttributes;
    }

    /**
     * Remove the tables which are not used after processing is finished and not contain any important information.
     */
    private void removeTemporaryTables()
    {
        for (String tableName : temporaryTables) {
            context.getDatabase().dropIfExistsViewOrTable(tableName);
        }
    }

    /**
     * Write the match records to match tables creating during whole process.
     *
     * @param match records
     */
    private void writeMatchTable(final Match match)
    {
        final Record record = new Record();

        for (final Value leftValue : match.getLeftRecord()) {
            final String newKey = context.getDatabase().sanitizeName(leftValue.getAttributeName());
            record.put(newKey, leftValue.getValue());
        }
        for (final Value rightValue : match.getRightRecord()) {
            final String newKey = context.getDatabase().sanitizeName(rightValue.getAttributeName());
            record.put(newKey, rightValue.getValue());
        }

        // match keys (if not there already)
        if (!record.hasAttribute(leftKeyAttributeNameTarget)) {
            record.put(leftKeyAttributeNameTarget, match.getLeftKey());
        }

        if (!record.hasAttribute(rightKeyAttributeNameTarget)) {
            record.put(rightKeyAttributeNameTarget, match.getRightKey());
        }

        // partial exact matches
        for (final String partialExactKey : match.getPartialExactKeys()) {
            record.put(partialExactKey, match.isPartialExactMatch(partialExactKey) ? "yes" : "no");
        }

        // inexact matches
        record.put("match_exact", match.isExact() ? "yes" : "no");
        record.put("match_jaro", match.getJaroScore());
        record.put("match_jarowinkler", match.getJaroWinklerScore());
        record.put("match_dice", match.getDiceScore());

        // legasthenic
        for (final MatchOption mo : matchArguments.getOptions()) {
            if (mo.getType() == MatchOption.OPT_LEGASTODETECT) {
                final String legaAttr = matchOptionAttributeNameMap.get(mo);
                if (match.getLegasthenicMatches().get(legaAttr) != null) {
                    // redundant but safer if decided to do the Boolean hash
                    // better in match in future (currently, we only have TRUE
                    // entries there)
                    if (match.getLegasthenicMatches().get(legaAttr)) {
                        record.put(legaAttr, "yes");
                    }
                } else {
                    record.put(legaAttr, "no");
                }
            }
        }

        // add record to buffer (init if necessary)
        if (matchBuffer == null) {
            matchBuffer = new ArrayList<>();
        }
        matchBuffer.add(record);

        // empty buffer from time to time
        if (matchBuffer.size() > MAX_MATCH_BUFFER_RECORDS) {
            flushMatchBuffer();
        }
    }
}
