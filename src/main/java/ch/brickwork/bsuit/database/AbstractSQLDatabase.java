package ch.brickwork.bsuit.database;

import ch.brickwork.bsuit.util.ILog;
import ch.brickwork.bsuit.util.Log;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

/**
 * Abstract base class for various flavours of SQL databases.
 * Supports most common elements of usual SQL dialects. Methods that are certainly specific
 * to the various flavours of SQL dialects/DBMS are abstract.
 * Created by marcel on 06.11.15.
 */
public abstract class AbstractSQLDatabase {

    /**
     * log to display messages
     */
    protected ILog log;

//
// SQL LANGUAGE ELEMENTS (variables)
//

    private static final String[] RESERVED_KEYWORDS = {"ADD", "AFTER", "ALL", "ALTER", "ANALYZE", "AND", "AS", "ASC", "ATTACH", "AUTOINCREMENT", "BEFORE", "BEGIN",
            "BETWEEN", "BY", "CASCADE", "CASE", "CAST", "CHECK", "COLLATE", "COLUMN", "COMMIT", "CONFLICT", "CONSTRAINT", "CREATE", "CROSS", "CURRENT_DATE",
            "CURRENT_TIME", "CURRENT_TIMESTAMP", "DATABASE", "DEFAULT", "DEFERRABLE", "DEFERRED", "DELETE", "DESC", "DETACH", "DISTINCT", "DROP", "EACH", "ELSE",
            "END", "ESCAPE", "EXCEPT", "EXCLUSIVE", "EXISTS", "EXPLAIN", "FAIL", "FOR", "FOREIGN", "FROM", "FULL", "GLOB", "GROUP", "HAVING", "IF", "IGNORE",
            "IMMEDIATE", "IN", "INDEX", "INDEXED", "INITIALLY", "INNER", "INSERT", "INSTEAD", "INTERSECT", "INTO", "IS", "ISNULL", "JOIN", "KEY", "LEFT", "LIKE",
            "LIMIT", "MATCH", "NATURAL", "NO", "NOT", "NOTNULL", "NULL", "OF", "OFFSET", "ON", "OR", "ORDER", "OUTER", "PLAN", "PRAGMA", "PRIMARY", "QUERY",
            "RAISE", "RECURSIVE", "REFERENCES", "REGEXP", "REINDEX", "RELEASE", "RENAME", "REPLACE", "RESTRICT", "RIGHT", "ROLLBACK", "ROW", "SAVEPOINT", "SELECT",
            "SET", "TABLE", "TEMP", "TEMPORARY", "THEN", "TO", "TRANSACTION", "TRIGGER", "UNION", "UNIQUE", "UPDATE", "USING", "VACUUM", "VALUES", "VIEW",
            "VIRTUAL", "WHEN", "WHERE", "WITH", "WITHOUT"};

    private static final String DEFAULT_SORT_ASC_KEYWORD = "asc";

    private static final String DEFAULT_SORT_DESC_KEYWORD = "desc";

    private static final String[] RESERVED_CHARACTERS = {"\\W", " ", ":", ".", "-", "*", "/", "#", "(", ")"};

    private static final String DEFAULT_COLUMN_NAME_IF_COLUMN_IS_EMPTY = "empty_column_name";

    private static final String FORBIDDEN_CHARACTERS = "[\\^\\*/#\\(\\):\\s\\-]";

    private static final String DEFAULT_QUOTED_COLUMN_NAME_START_LITERAL = "\"";

    private static final String DEFAULT_QUOTED_COLUMN_NAME_END_LITERAL = "\"";

//
// SQL LANGUAGE ELEMENTS
//

    /**
     * @return gets keywords reserved for SQL / DBMS language that may not be part of a table or column name if it
     * stands isolated (e.g., "myselectname" is ok, but "select" is not
     */
    protected String[] getReservedKeywords() {
        return RESERVED_KEYWORDS;
    }

    /**
     * @return gets keyword after the SORT clause standing for ascending order; typically ASC
     */
    protected String getAscKeyword() {
        return DEFAULT_SORT_ASC_KEYWORD;
    }

    /**
     * @return gets keyword after the SORT clause standing for descending order; typically DESC
     */
    protected String getDescKeyword() {
        return DEFAULT_SORT_DESC_KEYWORD;
    }

    /**
     * @return strings or characters that may only be used if quoted as part of a string literal
     */
    protected String[] getReservedCharacters() {
        return RESERVED_CHARACTERS;
    }

    protected String getDefaultColumnNameIfColumnIsEmpty() {
        return DEFAULT_COLUMN_NAME_IF_COLUMN_IS_EMPTY;
    }

    protected String getForbiddenCharactersRegEx() {
        return FORBIDDEN_CHARACTERS;
    }

    protected String getQuotedColumnNameStartLiteral() { return DEFAULT_QUOTED_COLUMN_NAME_START_LITERAL; }

    protected String getQuotedColumnNameEndLiteral() { return DEFAULT_QUOTED_COLUMN_NAME_END_LITERAL; }




    /**
     * @return String concatenation operator for SQLite database
     */
    public String getConcatOperator() {
        return "||";
    }

    /**
     * returns true if expression is considered a constant expression, e.g. constant string
     * or number, false otherwise.
     *
     * @param expression
     * @return
     */
    public boolean isConstant(String expression) {
        return (expression.trim().charAt(0) == '\'' && expression.trim().charAt(expression.trim().length() - 1) == '\'') || expression.trim().matches("\\d+");
    }

    /**
     * returns true if and only if the the column or table name provided is in quotes
     * @param name
     * @return
     */
    protected boolean isQuotedName(String name){
        return name.trim().startsWith(getQuotedColumnNameStartLiteral()) &&
                name.trim().endsWith(getQuotedColumnNameEndLiteral());
    }

    /**
     * puts a column of table name into quotes (" mostly, but [ ] for MS SQL for instance)
     * @param name
     * @return
     */
    protected String quoteName(String name) {
        if(isQuotedName(name))
            return name;
        else
            return getQuotedColumnNameStartLiteral() + name + getQuotedColumnNameEndLiteral();
    }

//
// ABSTRACT METHODS AND CONSTRUCTOR
//
    protected abstract void initLogger();
    protected abstract void initDatabase();
    protected abstract void openConnection();
    public abstract List<Record> prepare(final String sql);
    public abstract List<String> getTableOrViewColumnNames(String tableOrViewName);
    public abstract Hashtable<String, Integer> getTableOrViewColumnNamesHash(String tableOrViewName);
    public abstract boolean existsTable(final String name);
    public abstract boolean existsView(final String name);
    public abstract void insert(final String tableName, final List<Record> records);
    protected abstract List<String> getAllTableNames();
    protected abstract List<String> getAllViewNames();

    public AbstractSQLDatabase(ILog log)
    {
        this.log = log;
    }


    /**
     * To enable debug level logging put it into constructor:
     * <p/>
     * com.almworks.sqlite4java.SQLite.main(new String[] {"-d"});
     */
    public AbstractSQLDatabase()
    {
        this(new Log());
    }


//
// META DATA MANAGEMENT (VARIABLES)
//

    /**
     * tries to find the variable in the variables directory (variables table), if found, ensures that table
     * or view behind this entry also exists. If both true, returns the Variable. Returns null in any other case.
     *
     * @param variableName is variableName
     * @return Variable or null
     */
    public Variable getVariable(final String variableName) {
        List<Record> result;
        result = prepare("SELECT * FROM variables WHERE variable_name='" + variableName + "'");

        if (result != null && result.size() == 1) {
            if (existsTableOrView(Variable.getTableName(variableName))) {
                return new Variable(new Long(result.get(0).getValue("id").getValue().toString()), result.get(0).getValue("variable_name").getValue().toString(),
                        result.get(0).getValue("description").getValue().toString(), result.get(0).getValue("file_name").getValue().toString());
            } else {
                log.log("Variable " + variableName + " does not have a view or table (orphan entry)");
                return null;
            }
        } else if (null != result && result.size() > 1) {
            log.err("VARIABLES tables contains more than one variable with the same name! Corrupt!");
            return null;
        }

        return null;
    }

    /**
     * gets list of all variables
     *
     * @return list of all variables
     */
    public List<Variable> getVariables(final String variableName) {
        List<Variable> list = new ArrayList<>();
        String query = "SELECT * FROM variables WHERE variable_name LIKE '" + variableName + "' ESCAPE '\\';";
        final List<Record> records = prepare(query);
        if (null != records) {
            for (Record record : records) {
                Variable v = new Variable(new Integer((String) record.getValue("id").getValue()).longValue(),
                        (String) record.getValue("variable_name").getValue(), (String) record.getValue("description").getValue(),
                        (String) record.getValue("file_name").getValue());
                list.add(v);
            }
        }

        return list;
    }



//
// CORE
//

    /**
     * get all table names which are not reserved by boilersuit
     */
    public List<String> getTableNames() {
        ArrayList<String> nonReservedNames = new ArrayList<>();
        for(String name : getAllTableNames()) {
            if(!isReservedTableOrViewName(name))
                nonReservedNames.add(name);
        }
        return nonReservedNames;
    }

    /**
     * get all view names which are not reserved by boilersuit
     */
    public List<String> getViewNames() {
        ArrayList<String> nonReservedNames = new ArrayList<>();
        for(String name : getAllViewNames()) {
            if(!isReservedTableOrViewName(name))
                nonReservedNames.add(name);
        }
        return nonReservedNames;
    }

    /**
     * reads all records from table or view "tableOrViewName", starting from record on row "limitFirst", reading
     * maximum limitLength records.
     *
     * @param tableOrViewName name of table of view to read from
     * @param limitFirst      row number 0..n. Should a database implementation consider 1 as the first, this method should add an increment to cater for this. The caller can always use 0 as the first.
     * @param limitLength     number of records to be read at maximum
     * @return list of all records within the given range
     */
    public List<Record> getAllRecordsFromTableOrView(final String tableOrViewName, long limitFirst, long limitLength, final String sortField,
                                                     Boolean sortAsc) {
        String sql = "SELECT * FROM " + tableOrViewName;
        if (null != sortField) {
            sql = sql + " ORDER BY " + sortField + " " + (null == sortAsc || sortAsc ? getAscKeyword() : getDescKeyword());
        }
        sql = sql + " LIMIT " + limitFirst + "," + limitLength;
        return prepare(sql);
    }

    /**
     * reads all records from table or view "tableOrViewName"
     *
     * @param tableOrViewName name of table of view to read from
     * @return list of all records
     */
    public List<Record> getAllRecordsFromTableOrView(final String tableOrViewName, final String sortField, Boolean sortAsc) {
        String sql = "SELECT * FROM " + tableOrViewName;
        if (null != sortField) {
            sql = sql + " ORDER BY " + sortField + " " + (null == sortAsc || sortAsc ? getAscKeyword() : getDescKeyword());
        }
        return prepare(sql);
    }

    /**
     * checks whether table or view with name "name" exists
     *
     * @param name name of table or view to check for
     * @return true, if exists
     */
    public boolean existsTableOrView(final String name) {
        return existsTable(name) || existsView(name);
    }

    /**
     * inserts record into table
     *
     * @param tableName table into which record is inserted
     * @param record    record to be inserted
     */
    public void insert(final String tableName, final Record record) {
        ArrayList<Record> list = new ArrayList<>();
        list.add(record);
        insert(tableName, list);
    }

    /**
     * counts number of records in table or view with name "tableOrViewName"
     *
     * @param tableOrViewName name of table or view to count
     * @return count of records
     */
    public long count(final String tableOrViewName) {
        final List<Record> result = prepare("SELECT COUNT(*) FROM " + tableOrViewName);
        if (null != result) {
            String count = (String) result.get(0).getFirstValueContent();
            return Integer.parseInt(count);
        }
        return 0;
    }

    /**
     * creates database index with name "indexName" on table "tableName" and attribute "attributeName"
     *
     * @param tableName     is name of table
     * @param attributeName is name of attribute
     * @param indexName     is name of index
     */
    public void createIndex(final String tableName, final String attributeName, final String indexName) {
        prepare("CREATE INDEX " + indexName + " ON " + tableName + "(" + attributeName + ")");
    }

    /**
     * currently unsafely deletes all temp tables. therefore, currently not called. unsafe because if a variable
     * say d uses (select blabla from view t1) a temp table t1, t1 would be deleted. and therefore d would no longer
     * work. not so trivial since d may reference t1, but not t2. t2 should then also not be deleted, because it is
     * indirectly referenced. to sort this out correctly, probably a graph must be modeled. v2 :-)
     */
    public void deleteTemporaryTables() {
        List<String> tableAndViewNames = getAllTableNames();
        tableAndViewNames.addAll(getAllViewNames());
        for (String tableName : tableAndViewNames) {
            if (tableName.toLowerCase().startsWith("temp_")) {
                final String message = "Deleting temporary variables: " + tableName;
                log.info(message);
                dropIfExistsViewOrTable(tableName);
                deleteVariable(tableName);
            }
        }
    }

    public void deleteVariable(final String variableName) {
        prepare("DELETE FROM VARIABLES WHERE variable_name LIKE '" + variableName + "'");
    }


    /**
     * if the table exists, drop it, otherwise do nothing
     *
     * @param tableName view to be dropped
     */
    public boolean dropIfExistsTable(final String tableName) {
        deleteVariable(tableName);
        if (existsTable(tableName)) {
            prepare("DROP TABLE " + tableName);
            return true;
        } else {
            return false;
        }
    }

    /**
     * if the view exists, drop it, otherwise do nothing
     *
     * @param viewName view to be dropped
     */
    public boolean dropIfExistsView(final String viewName) {
        deleteVariable(viewName);
        if(existsTableOrView(viewName) && !existsTable(viewName)) {
            prepare("DROP VIEW " + viewName);
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * if the view or table exists, drop it, otherwise do nothing
     *
     * @param viewOrTableName view or table to be dropped
     */
    public boolean dropIfExistsViewOrTable(final String viewOrTableName) {
        return dropIfExistsView(viewOrTableName) || dropIfExistsTable(viewOrTableName);
    }

    /**
     * returns true, if the columnName can be used as a column name in a SELECT statement.
     * returns true not only for defined columns of that table but also for columns that
     * are available by default in the used database system, e.g. rowid in SQLite
     *
     * @param tableName
     * @param columnName
     * @return
     */
    public boolean existsColumn(String tableName, String columnName) {
        return getTableOrViewColumnNamesHash(tableName).get(sanitizeName(columnName)) != null || columnName.equalsIgnoreCase("rowid");
    }

    /**
     * Rename table name.
     *
     * @param tableName    from name
     * @param newTableName to name
     */
    public void renameTableName(String tableName, String newTableName) {
        prepare("ALTER TABLE " + tableName + " RENAME TO " + newTableName);
    }


    protected String createInsertStatement(final String tableName, final Record record) {
        return createInsertStatement(tableName, record, false);
    }


    /**
     * @param tableName
     * @param record
     * @param placeHolders
     * @return
     */
    protected String createInsertStatement(final String tableName, final Record record, boolean placeHolders) {
        final StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(tableName);
        sql.append("(");

        final StringBuilder attributeString = new StringBuilder();
        final StringBuilder valuesString = new StringBuilder();
        boolean first = true;
        for (Value v : record) {
            if (first) {
                first = false;
            } else {
                attributeString.append(", ");
                valuesString.append(", ");
            }
            attributeString.append(sanitizeName(v.getAttributeName()));
            valuesString.append("'");
            valuesString.append(placeHolders ? '?' : literalizeQuotes(v.getValue().toString()));
            valuesString.append("'");
        }

        sql.append(attributeString);
        sql.append(") VALUES (");
        sql.append(valuesString);
        sql.append(");");

        return sql.toString();
    }

//
// UTIL
//
    /**
     * set a log where the errors etc. can be written to
     */
    public void setLog(ILog log) {
        this.log = log;
    }

    /**
     * get column names of a table or view in the order as defined in the database, sanitized
     *
     * @param tableOrViewName name of table or view
     * @return list of the column names in the correct order as in database
     */

    public List<String> getSanitizedTableOrViewColumnNames(final String tableOrViewName) {
        final List<String> sanitizedList = new ArrayList<>();
        for (final String s : getTableOrViewColumnNames(tableOrViewName)) {
            sanitizedList.add(sanitizeName(s));
        }
        return sanitizedList;
    }

    /**
     * first of all, applies sanitizeName to each column. In addition, replaces empty column names by placeholders
     * empty_name_1 ... empty_name_n replaces duplicate names by XXX_1, XXX_2
     * (where XXX is the duplicate text) puts all to lower case
     */
    public String[] cleanColumnNames(final String[] columnNames) {
        int emptyHeaderCount = 1;

        for (int i = 0; i < columnNames.length; i++) {
            columnNames[i] = sanitizeName(columnNames[i]);

            // assign placeholder if empty
            if (columnNames[i].length() == 0) {
                columnNames[i] = "EMPTY_HEADER_" + emptyHeaderCount;
                emptyHeaderCount++;
            }
        }

        for (int i = 0; i < columnNames.length; i++) {
            // duplicates
            int duplicateCount = 2;

            for (int j = i + 1; j < columnNames.length; j++) {
                // if equal column names, add number to it:
                if (columnNames[i].equals(columnNames[j])) {
                    // if already encapsulated in brackets due to sanitization, then first take away
                    // sanitizing brackets:
                    String unsanitizedName = columnNames[i];
                    if (columnNames[i].charAt(0) == '"' && columnNames[i].charAt(columnNames[i].length() - 1) == '"') {
                        unsanitizedName = columnNames[i].substring(1, columnNames[i].length() - 1);
                    }
                    columnNames[j] = sanitizeName(unsanitizedName + "_" + duplicateCount);
                }
            }
        }

        return columnNames;
    }

    /**
     * removes any special characters and replaces by "_", removes any leading
     * numbers and replaces by "_" such that it can be used by the database as a
     * table, view or column name without conflicting with syntax. Does not check
     * on reserved keywords.
     *
     * @param colName raw column name
     * @return sanitized name
     */

    public String sanitizeName(String colName) {
        boolean quoted = isQuotedName(colName);

        // trim
        colName = colName.trim();

        // replace double spaces by single spaces
        if (!quoted && colName.contains("  ")) {
            colName = colName.replace("  ", " ");
        }

        // if empty column name, invent one
        if (colName.length() == 0) {
            return getDefaultColumnNameIfColumnIsEmpty();
        }

        // if starts with number, quote
        if (Character.isDigit(colName.charAt(0))) {
            colName = quoteName(colName);
            quoted = true;
        }

        // replace forbidden characters
        if(!quoted)
            colName = replaceForbiddenCharacters(colName);
        else
            // replace them only within the quote (quote itself is also forbidden, but we do
            // not want to remove the quotes!
            colName = quoteName(replaceForbiddenCharacters(colName.substring(1, colName.length() - 1)));


        // handle delicate characters and quote if necessary
        for (String bad : getReservedCharacters()) {
            if (colName.contains(bad)) {
                colName = quoteName(colName);
                quoted = true;
                break;
            }
        }

        // detect keywords and quote if necessary
        if (!quoted) {
            for (String keyword : getReservedKeywords()) {
                if (colName.equalsIgnoreCase(keyword)) {
                    colName = quoteName(colName);
                    quoted = true;
                    break;
                }
            }
        }

        return colName;
    }

    /**
     * replaces forbidden characters by '_' and ensures that there are not trailing '_' in the result
     * @param name
     * @return
     */
    private String replaceForbiddenCharacters(String name) {
        String s = name.replaceAll(getForbiddenCharactersRegEx(), "_").trim();
        while(s.charAt(s.length() - 1) == '_')
            s = s.substring(0, s.length() - 1);
        while(s.charAt(0) == '_')
            s = s.substring(1);
        return s;
    }

    /**
     * replaces ' by '' such that insert statement of strings is not violating
     * syntax
     *
     * @return literalizeQuotes text
     */
    private String literalizeQuotes(String string) {
        return string.replace("'", "''");
    }


    /**
     * if left table is a(a1, a2, a3) and right table is b(b1, b2, a1), then it
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
    public void createCombinedTable(final List<String> leftTableColumnNames, final List<String> rightTableColumnNames,
                                    final String leftPrefix, final String rightPrefix, final List<String> additionalAttributes,
                                    final String combinedTableName) {

        final StringBuilder sql = new StringBuilder("CREATE TABLE ");
        sql.append(combinedTableName.toLowerCase());
        sql.append("(");

        boolean firstLeft = true;

        for (String colName : leftTableColumnNames) {
            if (firstLeft) {
                firstLeft = false;
            } else {
                sql.append(",");
            }

            sql.append(leftPrefix);
            sql.append("_");
            sql.append(sanitizeName(colName));
            sql.append(" text");
        }
        if (firstLeft) {
            return;
        }

        boolean firstRight = true;
        for (String colName : rightTableColumnNames) {
            if (firstRight) {
                firstRight = false;
            }

            sql.append(", ");
            sql.append(rightPrefix);
            sql.append("_");
            sql.append(sanitizeName(colName));
            sql.append(" text");
        }
        if (firstRight) {
            return;
        }

        for (String colName : additionalAttributes) {
            sql.append(", ");
            sql.append(sanitizeName(colName));
            sql.append(" text");
        }

        sql.append(")");

        prepare(sql.toString());
    }

    /**
     * creates a temporary name. the name is such that no table nor view exists
     * that has the same name. This will be used for instance to create temporary table
     * names.
     *
     * @return created name
     */
    public String createTempName() {
        return createTempName(null);
    }

    /**
     * similar as createTempName, but enforcing that "useThisAsPartOfName" is contained in the name.
     * BoilerSuit will use this to increase readability of certain temporary or system-created table
     * or attribute names
     *
     * @param useThisAsPartOfName might be null
     * @return created name
     */
    public String createTempName(String useThisAsPartOfName) {
        String tempName;
        do {
            if (useThisAsPartOfName != null) {
                useThisAsPartOfName = "_" + useThisAsPartOfName;
            } else {
                useThisAsPartOfName = "";
            }
            tempName = "temp_" + (int) (Math.random() * 10000) + useThisAsPartOfName;
        } while (existsView(tempName) || existsTable(tempName));

        return tempName;
    }


//
// LEGACY AND TEMPORARY
//



    /**
     * replaces a variable by another. if it does not yet exist, adds a it to the database, but doesn't create an underlying table
     *
     * @param variableName name of variable
     * @param desc         description of variable
     * @param fileName     file name property of the variable
     */
    public Variable createOrReplaceVariable(final String variableName, final String desc, final String fileName) {
        final Variable tV = new Variable(-1, variableName, null != desc ? desc : "", null != fileName ? fileName : "");

       dropIfExistsViewOrTable(variableName);

        // delete (if exists) and add variable to variables table
        prepare("DELETE FROM VARIABLES WHERE variable_name='" + tV.getVariableName() + "'");
        addVariable(tV);
        return tV;
    }

    /**
     * @param tableName   name of table
     * @param desc        description of variable
     * @param fileName    name of file
     * @param columnNames array of strings contains column names
     * @param primaryKeys array of strings contains PK names
     * @return created Variable
     */
    public Variable createOrReplaceVariableAndTable(final String tableName, final String desc, final String fileName,
                                                    String[] columnNames, String[] primaryKeys) {
        columnNames = cleanColumnNames(columnNames);
        if (existsTable(tableName)) {
            // delete data (if already exists)
            prepare("DROP TABLE " + tableName);
        }

        if (existsView(tableName)) {
            prepare("DROP VIEW " + tableName);
        }

        final Variable tV = createOrReplaceVariable(tableName, desc, fileName);
        final StringBuilder sbuf = new StringBuilder();
        sbuf.append("create table ");
        sbuf.append(tV.getTableName());
        sbuf.append(" (");
        boolean first = true;
        for (String colName : columnNames) {
            if (first) {
                first = false;
            } else {
                sbuf.append(", ");
            }
            sbuf.append(sanitizeName(colName));
            sbuf.append(" VARCHAR(1024)");
        }

        if (primaryKeys != null && primaryKeys.length > 0) {
            final String primaryKeySyntax = "PRIMARY KEY";
            final StringBuilder primaryKey = new StringBuilder();
            primaryKey.append(primaryKeySyntax);
            primaryKey.append("(");
            primaryKeys = cleanColumnNames(primaryKeys);
            first = true;
            for (String pkName : primaryKeys) {
                if (first) {
                    first = false;
                } else {
                    primaryKey.append(", ");
                }
                primaryKey.append(pkName);
            }
            primaryKey.append(")");

            sbuf.append(", ");
            sbuf.append(primaryKey);
        }

        sbuf.append(")");

        final String sql = sbuf.toString();
        prepare(sql);
        tV.setDescription(sql);
        return tV;
    }

    /**
     * Adds a variable to the database, but doesn't create an underlying table.
     *
     * @param variable instance of Variable which will be added to the DB
     */
    private void addVariable(final Variable variable) {
        insert("variables", variable.toRecord());
    }

    /**
     * init boilersuit internal table ("variable") model
     */
    protected void initMeta() {
        if (!existsTable("variables")) {
            prepare("create table variables (id integer primary key, variable_name text, description blob, " + "file_name text)");
            prepare("create index variable_name_index on variables (variable_name)");
        }

        String[] warningTableCols = {"TS", "tableaffected", "record", "action", "problem"};
        String[] warningTablePKCols = {};
        createOrReplaceVariableAndTable("warnings", "Warnings", "", warningTableCols, warningTablePKCols);
    }

    /**
     * init boilersuit internal table ("variable") model
     */
    protected void initVariables() {
        if (!existsTable("variables")) {
            prepare("create table variables (id integer primary key, variable_name text, description blob, " + "file_name text)");
            prepare("create index variable_name_index on variables (variable_name)");
        }
    }

    private boolean isReservedTableOrViewName(String name) {
        return name.equalsIgnoreCase("variables") || name.equalsIgnoreCase("warnings");
    }
}
