package ch.brickwork.bsuit.database;

import ch.brickwork.bsuit.util.ILog;
import java.util.Hashtable;
import java.util.List;

/**
 * Interface implemented by all database implementations. All database access within BoilerSuit should only
 * be against this interface to allow for compatibility across different database systems
 * <p/>
 * User: marcelcamporelli
 * Date: 27.1.2014
 * Time: 1:06 PM
 */
public interface IDatabase {



    /**
     * reads all records from table or view "tableOrViewName", starting from record on row "limitFirst", reading
     * maximum limitLength records.
     *
     * @param tableOrViewName name of table of view to read from
     * @param limitFirst      row number 0..n. Should a database implementation consider 1 as the first, this method should add an increment to cater for this. The caller can always use 0 as the first.
     * @param limitLength     number of records to be read at maximum
     *
     * @return list of all records within the given range
     */

    List<Record> getAllRecordsFromTableOrView(final String tableOrViewName, long limitFirst, long limitLength, final String sortField,
                                              Boolean sortAsc);

    /**
     * reads all records from table or view "tableOrViewName"
     *
     * @param tableOrViewName name of table of view to read from
     *
     * @return list of all records
     */

    List<Record> getAllRecordsFromTableOrView(final String tableOrViewName, final String sortField, Boolean sortAsc);

    /**
     * get column names of a table or view in the order as defined in the database, sanitized
     *
     * @param tableOrViewName name of table or view
     *
     * @return list of the column names in the correct order as in database
     */

    List<String> getSanitizedTableOrViewColumnNames(final String tableOrViewName);

    /**
     * get column names of a table or view in the order as defined in the database
     *
     * @param tableOrViewName name of table or view
     *
     * @return list of the column names in the correct order as in database
     */

    List<String> getTableOrViewColumnNames(final String tableOrViewName);

    /**
     * like getTableOrViewColumnNames, but in form of a hash, when the keys are
     * the columns names and the values are Integer objects denoting the
     * position of the columns within the result set of meta query as provided by
     * the DBMS, starting with 0.
     *
     * @return hash table where the keys are the column names as defined in the database, and values are Integer objects from 0..n depending on the position of the column as defined in the database
     */

    Hashtable<String, Integer> getTableOrViewColumnNamesHash(final String tableOrViewName);

    /**
     * tries to find the variable in the variables directory (variables table), if found, ensures that table
     * or view behind this entry also exists. If both true, returns the Variable. Returns null in any other case.
     *
     * @param variableName is variableName
     *
     * @return Variable or null
     */

    Variable getVariable(final String variableName);

    /**
     * gets list of all variables where "likeMatcher" LIKE variable name
     * @return list of all variables
     */
    List<Variable> getVariables(final String variableName);

    /**
     * get all table's names directly from the database, no matter whether they were entered as variables or not
     */
    List<String> getTableNames();

    /**
     * get all table's names directly from the database, no matter whether they were entered as variables or not
     */
    List<String> getViewNames();

    /**
     * set a log where the errors etc. can be written to
     */
    void setLog(final ILog log);


    /**
     * first of all, applies sanitizeName to each column. In addition, replaces empty column names by placeholders
     * empty_name_1 ... empty_name_n replaces duplicate names by XXX_1, XXX_2
     * (where XXX is the duplicate text) puts all to lower case
     */
    String[] cleanColumnNames(final String[] columnNames);

    /**
     * counts number of records in table or view with name "tableOrViewName"
     *
     * @param tableOrViewName name of table or view to count
     *
     * @return count of records
     * <p/>
     */
    long count(final String tableOrViewName);

    /**
     * If left table is a(a1, a2, a3) and right table is b(b1, b2, a1), then it
     * creates a new table "combinedTableName"(a_a1, a_a2, a_a3, b_b1, b_b2,
     * b_a1). If a = b, returns without doing anything. If one of the tables has
     * no attributes, also.
     *
     * @param leftTableColumnNames  "a" table column names
     * @param rightTableColumnNames "b" table column names
     * @param leftPrefix            prefix to be used for a's attributes
     * @param rightPrefix           prefix to be used for b's attributes
     * @param additionalAttributes  any additional attributes to be appended after a's and b's
     * @param combinedTableName     name of combined result table
     */
    void createCombinedTable(final List<String> leftTableColumnNames, final List<String> rightTableColumnNames,
                             final String leftPrefix, final String rightPrefix, final List<String> additionalAttributes,
                             final String combinedTableName);

    /**
     * creates database index with name "indexName" on table "tableName" and attribute "attributeName"
     *
     * @param tableName     is name of table
     * @param attributeName is name of attribute
     * @param indexName     is name of index
     */
    void createIndex(final String tableName, final String attributeName, final String indexName);

    /**
     * replaces a variable by another. if it does not yet exist, adds a it to the database, but doesn't create an underlying table
     *
     * @param variableName name of variable
     * @param desc         description of variable
     * @param fileName     file name property of the variable
     */

    Variable createOrReplaceVariable(final String variableName, final String desc, final String fileName);

    /**
     * @param variableName name of variable
     * @param desc         description of variable
     * @param fileName     name of file
     * @param columnNames  array of strings contains column names
     * @param primaryKeys  array of strings contains PK names
     *
     * @return created Variable
     */

    Variable createOrReplaceVariableAndTable(final String variableName, final String desc, final String fileName,
                                             final String[] columnNames, final String[] primaryKeys);

    /**
     * similar as createTempName, but enforcing that "useThisAsPartOfName" is contained in the name.
     * BoilerSuit will use this to increase readability of certain temporary or system-created table
     * or attribute names
     *
     * @param useThisAsPartOfName text will be used as a part of created name
     *
     * @return created name
     */

    String createTempName(final String useThisAsPartOfName);

    /**
     * creates a temporary name. the name is such that no table nor view exists
     * that has the same name. This will be used for instance to create temporary table
     * names.
     *
     * @return created name
     */

    String createTempName();

    /**
     * currently unsafely deletes all temp tables. therefore, currently not called. unsafe because if a variable
     * say d uses (select blabla from view t1) a temp table t1, t1 would be deleted. and therefore d would no longer
     * work. not so trivial since d may reference t1, but not t2. t2 should then also not be deleted, because it is
     * indirectly referenced. to sort this out correctly, probably a graph must be modeled. v2 :-)
     */
    void deleteTemporaryTables();

    /**
     * try to delete variable
     *
     * @param variableName view to be deleted
     */
    void deleteVariable(final String variableName);

    /**
     * if the table exists, drops it, otherwise does nothing
     *
     * @param tableName view to be dropped
     */
    boolean dropIfExistsTable(final String tableName);

    /**
     * if the view or table exists, drops it, otherwise does nothing
     *
     * @param viewOrTableName view or table to be dropped
     */
    boolean dropIfExistsViewOrTable(final String viewOrTableName);

    /**
     * returns true, if the columnName can be used as a column name in a SELECT statement.
     * returns true not only for defined columns of that table but also for columns that
     * are available by default in the used database system, e.g. rowid in SQLite
     *
     * @param tableName
     * @param columnName
     *
     * @return
     */
     boolean existsColumn(String tableName, String columnName);

    /**
     * checks whether a given table exists
     *
     * @param tableName name of the table
     *
     * @return true if a table with that name exists in the datbase
     */
    boolean existsTable(final String tableName);

    /**
     * checks whether table or view with name "name" exists
     *
     * @param name name of table or view to check for
     *
     * @return true, if exists
     */
    boolean existsTableOrView(final String name);

    /**
     * inserts records into table
     *
     * @param tableName table into which records are inserted
     * @param records   records to be inserted
     */
    void insert(final String tableName, final List<Record> records);

    /**
     * executes an SQL statement
     * @TODO rename into something more clear
     * @param sql
     * @return list of records, or null in case of error. if result set empty, returns empty list of records
     */
    List<Record> prepare(final String sql);

    /**
     * Rename table name;
     *
     * @param tableName    name
     * @param newTableName name
     */
    void renameTableName(final String tableName, final String newTableName);

    /**
     * removes any special characters and replaces by "_", removes any leading
     * numbers and replaces by "_" such that it can be used by the database as a
     * table, view or column or table name without conflicting with syntax. Does not check
     * on reserved keywords.
     *
     * @param name name of column or table
     *
     * @return sanitized name
     */

    String sanitizeName(String name);


    /**
     * for instance rowid for SQLite, rownum for Oracle
     * @return
     */
    String getRowIdKeyWord();
}
