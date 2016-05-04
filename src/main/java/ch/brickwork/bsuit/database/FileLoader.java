package ch.brickwork.bsuit.database;

import ch.brickwork.bsuit.globals.IBoilersuitApplicationContext;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by marcel on 19/11/15.
 */
public class FileLoader {

    private IDatabase database;

    private IBoilersuitApplicationContext context;

    private static final int PARTITION_SIZE_IMPORT = 1000;

    private static final int LOG_IMPORT_RECORDS_COUNT_AFTER = 10000;


    public FileLoader(IDatabase database, IBoilersuitApplicationContext context) {
        this.database = database;
        this.context = context;
    }

    /**
     * Loads file with name "fileName" into the database, under variable name "variableName".
     * If variable already exists, it is replaced by new variable.
     *
     * @param variableName variable to be used to store the file
     * @param desc         description to be written to the variable table
     * @param file         file
     * @param encoding
     * @return table name of the table created to store the file contents
     */
    public String loadFile(final String variableName, final String desc, final File file, String encoding) {
        final FileImporter fileImporter = new FileImporter(file, encoding, context);

        String fileName = file.getName();

        final String[] columnNames = fileImporter.getColumnNames();
        if (null != columnNames) {
            final Variable variable = database.createOrReplaceVariableAndTable(variableName, desc, fileName, columnNames, null);
            context.getLog().info("Load file \"" + fileName + "\" as variable \"" + variableName + "\"...");

            List<Record> records = new ArrayList<>();
            int i = 0;
            try {
                for (Record record : fileImporter) {
                    if(!FileImporter.isToBeIgnored(record)) {
                        i++;
                        if (i % LOG_IMPORT_RECORDS_COUNT_AFTER == 0) {
                            context.getLog().info("Load records from " + fileName + " (" + i + " lines completed)");
                        }

                        if (i % PARTITION_SIZE_IMPORT == 0) {
                            database.insert(Variable.getTableName(variableName), records);
                            records = new ArrayList<>();
                        }

                        if (record.countValues() > 0) {
                            records.add(record);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                context.getLog().err("Unknown but serious problem at row " + i);
            }

            // write remainder
            if (records.size() > 0) {
                database.insert(Variable.getTableName(variableName), records);
            }
            if (variable != null) {
                return variable.getTableName();
            }
        }
        return null;
    }
}
