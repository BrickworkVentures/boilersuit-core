package ch.brickwork.bsuit.database;

import com.almworks.sqlite4java.*;
import ch.brickwork.bsuit.util.ILog;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

/**
 * implements the database interface for a SQLite database, using the almworks sqlite4java library
 *
 * @author Marcel Camporelli
 *         (camporelli@brickwork.ch)
 */
public class SQLiteDatabase extends AbstractSQLDatabase implements IDatabase, IFileBasedDatabase {

    public static final long SQLITE_BUSY_TIMEOUT = 1000L;

    /**
     * default file name for SQLite database
     */
    private static final String FILE_NAME = "data.db";

    private static String FILE_PATH;

    public SQLiteDatabase(String filePath, ILog log) {
        FILE_PATH = filePath;
        initLogger();
        initDatabase();
        openConnection();
        initMeta();
    }

//
// SQL ELEMENTS
//

    @Override
    public String getRowIdKeyWord() {
        return "rowid";
    }

//
// INITIATION AND CONNECTION
//

    @Override
    protected void initLogger() {
        java.util.logging.Logger.getLogger("com.almworks.sqlite4java").setLevel(java.util.logging.Level.WARNING);
    }

    @Override
    protected void initDatabase() {

    }

    /**
     * Open connection to the SQLite DB file.
     */
    @Override
    protected void openConnection() {
        final SQLiteConnection db = new SQLiteConnection(new File(getDbFilePath()));
        try {
            db.open(true);
        } catch (SQLiteException e) {
            log.err(e.getMessage() + " - " + getDbFilePath());
        } finally {
            db.dispose();
        }
    }


    /**
     * Change path to the SQLite DB file.
     */
    public void reopenConnection(String newFilePath) {
        FILE_PATH = newFilePath;
        openConnection();
    }

    @Override
    public String getDefaultDBFileName() {
        return FILE_NAME;
    }

    @Override
    public boolean changeDBFileDirectory(String newPath) {
        final FileSystem fileSystem = FileSystems.getDefault();
        final Path newFilePath = fileSystem.getPath(newPath + File.separator + FILE_NAME);
        try {
            if (!Files.exists(newFilePath))
                Files.move(fileSystem.getPath(getDbFilePath()), newFilePath, StandardCopyOption.REPLACE_EXISTING);
            FILE_PATH = newPath;
            log.info("New database file path: " + FILE_PATH);
            openConnection();
        } catch (IOException e) {
            log.warn("Something is wrong with database file path changes!");
            return false;
        }
        return true;
    }

    private String getDbFilePath() {
        return FILE_PATH + File.separator + FILE_NAME;
    }

//
// CORE
//

    /**
     * Executes an SQL statement.
     *
     * @param sql text used as SQL statement
     * @return an list of records of the result delivered by the database
     */
    @Override
    public List<Record> prepare(final String sql) {
        log.log("On " + this.getDbFilePath() + ": " + sql);

        SQLiteQueue queue = new SQLiteQueue(new File(getDbFilePath()));
        final List<Record> result = new ArrayList<>();

        final SQLiteJob<Object> job = new SQLiteJob<Object>() {
            protected Object job(SQLiteConnection connection) throws DatabaseException {
                SQLiteStatement s = null;
                try {
                    connection.setBusyTimeout(SQLITE_BUSY_TIMEOUT);
                    // this method is called from database thread and passed the connection
                    s = connection.prepare(sql);
                    while (s.step()) {
                        Record record = new Record();
                        for (int col = 0; col < s.columnCount(); col++) {
                            if (s.columnString(col) != null) {
                                record.put(s.getColumnName(col), s.columnString(col));
                            } else {
                                record.put(s.getColumnName(col), "");
                            }
                        }
                        result.add(record);
                    }
                } catch (SQLiteException e) {
                    log.err(e.getMessage());
                    throw new DatabaseException(e.getMessage());
                } finally {
                    if (null != s) {
                        s.dispose();
                    }
                }
                return null;
            }
        };

        queue.start().execute(job);

        try {
            queue.stop(true).join();
        } catch (InterruptedException e) {
            log.err("SQL executed without success: " + sql);
            return null;
        }

        //noinspection ThrowableResultOfMethodCallIgnored
        if (job.getError() != null) {
            return null;
        }

        return result;
    }

    /**
     * get column names of a table or view in the order as defined in the database
     *
     * @param tableOrViewName name of table or view
     * @return list of the column names in the correct order as in database
     */
    @Override
    public List<String> getTableOrViewColumnNames(String tableOrViewName) {
        List<String> columns = new ArrayList<>();

        final List<Record> result = prepare("PRAGMA table_info(" + tableOrViewName + ");");
        if (null != result) {
            for (Record record : result) {
                columns.add(record.getValue("name").getValue().toString());
            }
        }
        return columns;
    }

    /**
     * like getTableOrViewColumnNames, but in form of a hash, when the keys are
     * the columns names and the values are Integer objects denoting the
     * position of the columns within the result set of the PRAGMA table_info
     * call, starting with 0.
     * @return hash table where the keys are the column names as defined in the database, and values are Integer objects from 0..n depending on the position of the column as defined in the database
     */
    @Override
    public Hashtable<String, Integer> getTableOrViewColumnNamesHash(String tableOrViewName) {
        Hashtable<String, Integer> columns = new Hashtable<>();

        List<Record> result = prepare("PRAGMA table_info(" + tableOrViewName + ");");
        int i = 0;
        if (result != null) {
            for (Record record : result) {
                columns.put(record.getValue("name").getValue().toString(), i);
                i++;
            }
            return columns;
        }
        return null;
    }

    /**
     * Checks existence of a table with a specific name
     * @param tableName table name to be checked
     * @return returns true if the table with the name exists, false otherwise (also in
     * case of exception)
     */
    @Override
    public boolean existsTable(final String tableName) {
        List<Record> result = prepare("SELECT * FROM sqlite_master WHERE type='table' AND name='" + tableName.toLowerCase() + "'");
        return result != null && result.size() > 0;
    }

    /**
     * Checks existence of a view with a specific name
     * @param viewName table name to be checked
     * @return returns true if the view with the name exists, false otherwise (also in
     * case of exception)
     */
    @Override
    public boolean existsView(final String viewName) {
        List<Record> result = prepare("SELECT * FROM sqlite_master WHERE type='view' AND name='" + viewName.toLowerCase() + "'");
        return result != null && result.size() > 0;
    }

    /**
     * inserts records into table
     * @param tableName table into which records are inserted
     * @param records   records to be inserted
     */
    @Override
    public void insert(final String tableName, final List<Record> records) {
        if (records.size() == 0) {
            return;
        }

        final SQLiteQueue queue = new SQLiteQueue(new File(getDbFilePath()));
        queue.start().execute(new SQLiteJob<Object>() {
            protected Object job(SQLiteConnection connection) throws DatabaseException {
                SQLiteStatement s = null;
                try {
                    connection.setBusyTimeout(SQLITE_BUSY_TIMEOUT);
                    // this method is called from database thread and passed the connection
                    connection.exec("BEGIN");
                    for (Record record : records) {
                        s = connection.prepare(createInsertStatement(tableName, record));
                        s.step();
                    }

                    connection.exec("COMMIT");
                } catch (SQLiteException e) {
                    throw new DatabaseException(e.getMessage());
                } finally {
                    if (null != s) {
                        s.dispose();
                    }
                }
                return null;
            }
        }).complete();
        try {
            queue.stop(true).join();
        } catch (InterruptedException e) {
            log.err(e.getMessage());
        }
    }

    /**
     * get all table's names directly from the database, no matter whether they were entered as variables or not
     */
    @Override
    protected List<String> getAllTableNames() {
        return getMasterTableEntries("table");
    }

    /**
     * get all table's names directly from the database, no matter whether they were entered as variables or not
     */
    @Override
    protected List<String> getAllViewNames() {
        return getMasterTableEntries("view");
    }


    private List<String> getMasterTableEntries(String type) {
        List<Record> recs = prepare("SELECT name FROM sqlite_master WHERE type='" + type + "'");
        ArrayList<String> names = new ArrayList<>();
        for (Record r : recs) {
            names.add(r.getValue("name").getValue().toString());
        }
        return names;
    }
}
