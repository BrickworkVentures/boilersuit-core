package ch.brickwork.bsuit.database;

/**
 * BoilerSuits creates its own directory of variables within the database. A variable typically is a description
 * of a table or view. Anything that is assigned in BoilerSuit gets assigned a variable. The variable holds
 * different meta data about these tables/views.
 * @author Marcel Camporelli
 */
public class Variable {

    /**
     * if the variable is just an import of a file, e.g. var := myfile.csv, this is the name of the file
     */
    private final String fileName;

    /**
     * unique id of the variable in the variable directory (depending on implementation of IDatabase; but typically
     * something like "variables"
     */
    private final long id;

    /**
     * name of the variable
     */
    private final String variableName;

    /**
     * description of content in variable
     */
    private String description;

    /**
     * Note: variableName will always be stored in small caps
     */
    public Variable(long id, String variableName, String description, String fileName)
    {
        this.id = id;
        this.variableName = variableName.toLowerCase();
        this.description = description;
        this.fileName = fileName;
    }

    /**
     * get proposed table name for the variable name "variableName". E.g., we may decide at a later point
     * to propose table name "table_x" for variable "x". Currently, it is one to one.
     */

    public static String getTableName(String variableName)
    {
        return variableName;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }


    public String getFileName()
    {
        return fileName;
    }


    public String getTableName()
    {
        return getTableName(variableName);
    }


    public String getVariableName()
    {
        return variableName;
    }

    /**
     * creates a record with id=..., variable_name=..., description=..., file_name=..., with ... as the
     * actual values for this variable
     *
     * @return Record representing the variable
     */

    public Record toRecord()
    {
        Record r = new Record();
        if (id != -1) {
            r.put("id", id);
        }
        r.put("variable_name", variableName);
        r.put("description", description);
        r.put("file_name", fileName);
        return r;
    }
}
