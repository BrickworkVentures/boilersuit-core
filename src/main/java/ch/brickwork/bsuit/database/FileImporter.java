package ch.brickwork.bsuit.database;

import ch.brickwork.bsuit.globals.IBoilersuitApplicationContext;
import ch.brickwork.bsuit.util.FileIOUtils;
import ch.brickwork.bsuit.util.TextUtils;
import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.input.BOMInputStream;
import org.mozilla.universalchardet.UniversalDetector;

import java.io.*;
import java.util.Iterator;
import java.util.logging.Logger;

import com.opencsv.*;

/**
 * Provides an iterator to parse text lines of a CSV file into Records. Also contains all necessary helping methods
 * (analyse delimiter, etc.).<br/><br/>
 * Delimiters are auto-recognized and can be ',' or ';'. Values can be encapsulated in what we call "brackets". This
 * can be either " or '. This is also auto-recognized.
 */
public class FileImporter implements Iterable<Record> {

    private static final Logger LOG = Logger.getLogger(FileImporter.class.getCanonicalName());

    /**
     * the following quotes would not finish
     */
    private static final String[] DEFAULT_QUOTE_LITERALS_REGEXP = {"\"\"",   // double quote "" regexp
            "\\\\\""  // escaped quote \" regexp
    };


    /**
     * the above quote literals actually mean the following values (in same order):
     */
    private static final String[] DEFAULT_QUOTE_LITERAL_TRANSLATION = {"\"", "\""};

    private static final int MAX_WARN_COUNT = 100;

    private final static String LOG_FILE_NAME = "boilersuit_import_export.log";

    private static final Record TO_BE_IGNORED_RECORD = new Record();

    private static final String[] DELIMITERS = {",", ";", "|", ":", "\t"};

    private static final double GOOD_AVERAGE_COUNT_FOR_DELIMITATOR = 1;

    private String logFilePath;

    private static final String DEFAULT_ENCODING = "US-ASCII";

    private final File file;

    private String encoding;

    private String[] columnNames;

    private String commaDelimitator = null;

    private CSVReader reader;

    private int rowCount = 0;

    private IBoilersuitApplicationContext context;

    private int warnCount = 0;

    /**
     * Current Assumption: 1. always with header; 2. only with valid header; 3.
     * comma-sep ',' or ';' names (that can be used as attribute names in the database)
     *
     * @param file     - file without path
     * @param encoding
     */
    public FileImporter(File file, String encoding, IBoilersuitApplicationContext context) {
        this.file = file;
        this.encoding = encoding;
        this.context = context;
        init();
    }


    public static boolean isToBeIgnored(Record r) {
        return r.hashCode() == TO_BE_IGNORED_RECORD.hashCode();
    }

    public String[] getColumnNames() {
        return columnNames;
    }

    /**
     * Implementing the {@link Iterator} interface allows an object to be the target of
     * the "foreach" statement.
     */
    public Iterator<Record> iterator() {

        return new Iterator<Record>() {
            boolean hasNext = true;

            @Override
            public boolean hasNext() {
                return hasNext;
            }

            @Override
            public Record next() {
                String[] line = null;
                try {
                    line = reader.readNext();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (line == null) {
                    hasNext = false;
                    return TO_BE_IGNORED_RECORD;
                }

                rowCount++;

                final Record record = new Record();

                if (line.length > columnNames.length) {
                    warn("Check Line " + rowCount + "; there were too many values on that line: " + line.length + "/" + columnNames.length);
                } else {
                    if (line.length < columnNames.length) {
                        if (line.length == 1 && line[0].trim().equals("")) {
                            warn("Ignored empty line at " + rowCount);
                            appendToLogFile("Ignored empty line at " + rowCount);
                            return TO_BE_IGNORED_RECORD;
                        } else {
                            warn("Check Line " + rowCount + "; not same number of values as in header. Check log file. [" + line.length + "/" + columnNames.length + "]");
                            appendToLogFile(line.length + "/" + columnNames.length + " values only (row " + rowCount + "): " + line);
                        }
                    }


                    for (int i = 0; i < line.length; i++) {
                        record.put(columnNames[i], line[i]);
                    }
                }
                return record;
            }

            @Override
            public void remove() {
            }
        };

    }

    private void warn(String s) {
        if (warnCount < MAX_WARN_COUNT)
            context.getLog().warn(s);
        else if (warnCount == MAX_WARN_COUNT)
            context.getLog().warn("Too many warnings. If you want to see them all, check the import/export logfile in your folder");
        warnCount++;
    }

    private void initLogFile() {
        File f = new File(logFilePath);
        if (f.exists())
            f.delete();
        try {
            f.createNewFile();
        } catch (IOException e) {
            context.getLog().err("IO Problem in init" + e.getMessage() + e.getStackTrace());
            e.printStackTrace();
        }
    }

    private void appendToLogFile(String s) {
        FileIOUtils.overwriteFile(logFilePath, FileIOUtils.readCompleteFile(context.getWorkingDirectory(), logFilePath) + "\n" + s);
    }

    private InputStreamReader openStream() {
        BOMInputStream bomIn = null;
        try {
            bomIn = new BOMInputStream(new FileInputStream(file),
                    ByteOrderMark.UTF_8, ByteOrderMark.UTF_16LE, ByteOrderMark.UTF_16BE,
                    ByteOrderMark.UTF_32LE, ByteOrderMark.UTF_32BE
            );

            if(encoding == null) {
                encoding = detectEncoding();
                if (encoding == null) {
                    encoding = DEFAULT_ENCODING;
                    context.getLog().warn("Encoding could not be detected, using default: " + encoding);
                } else {
                    context.getLog().info("Detected " + encoding + " encoding");
                    if(isRareEncoding(encoding))
                        context.getLog().warn("Encoding " + encoding + " is rather rare; please check if applicable, otherwise use 'WITH encoding(...)' option");
                }
            } else {
                context.getLog().info("Using predefined " + encoding + " encoding");
            }

            return new InputStreamReader(bomIn, encoding);
        } catch (FileNotFoundException e) {
            context.getLog().err("File not found" + e.getMessage() + e.getStackTrace());
        } catch (IOException e) {
            context.getLog().err("IO Problem in init" + e.getMessage() + e.getStackTrace());
        }

        return null;
    }

    private boolean isRareEncoding(String encoding) {
        return !encoding.contains("UTF") && !encoding.contains("WINDOWS") && !encoding.contains("ASCII");
    }

    /**
     * init and reads first line
     */
    private void init() {
        logFilePath = context.getWorkingDirectory() + "/" + LOG_FILE_NAME;
        initLogFile();

        rowCount = 0;

        BufferedReader br = new BufferedReader(openStream());

        double[] sum = new double[DELIMITERS.length];
        int n = 0;
        try {
            for (int i = 0; br.ready() && i < 100; i++) {
                String l = br.readLine();
                if(!l.trim().equals("")) {
                    n++;

                    // accumulate est. variance sum for each delimitor
                    for (int delimIndex = 0; delimIndex < DELIMITERS.length; delimIndex++) {
                        int count = TextUtils.count(l, DELIMITERS[delimIndex]);
                        sum[delimIndex] += count;
                    }
                }
            }

            // calculate est. variance
            double bestScore = 0;
            int bestDelim = -1;
            int numGreaterThanThreshold = 0;
            for (int delimIndex = 0; delimIndex < DELIMITERS.length; delimIndex++) {
                double thisScore = (sum[delimIndex] / n);
                if (thisScore > bestScore) {
                    bestScore = thisScore;
                    bestDelim = delimIndex;
                }

                if(thisScore > GOOD_AVERAGE_COUNT_FOR_DELIMITATOR)
                    numGreaterThanThreshold++;

                context.getLog().log("Checking " + DELIMITERS[delimIndex] + " delimitor: found " + (sum[delimIndex] / n) + " av.");
            }

            commaDelimitator = DELIMITERS[bestDelim];
            context.getLog().log("Winning: " + DELIMITERS[bestDelim]);

            if(numGreaterThanThreshold > 1) {
                context.getLog().warn("Could not automatically detect delimitator in " + file.getName() + ", please set manually");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        reader = new CSVReader(openStream(), commaDelimitator.charAt(0));

        // read first line (header)
        readFirstLine();

        // clean column names
        if (null != columnNames) {
            columnNames = context.getDatabase().cleanColumnNames(columnNames);
        }
    }

    /**
     * read header line and init bracket and delimiter and columnNames. if -1, without limits
     */
    private void readFirstLine() {
        try {
            columnNames = reader.readNext();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String detectEncoding() throws IOException {
        byte[] buf = new byte[4096];
        FileInputStream fis = new java.io.FileInputStream(file.getAbsolutePath());
        UniversalDetector detector = new UniversalDetector(null);
        int nread;
        while ((nread = fis.read(buf)) > 0 && !detector.isDone())
            detector.handleData(buf, 0, nread);

        detector.dataEnd();
       return detector.getDetectedCharset();
    }
}
