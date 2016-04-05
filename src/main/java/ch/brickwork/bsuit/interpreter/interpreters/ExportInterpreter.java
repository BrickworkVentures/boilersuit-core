package ch.brickwork.bsuit.interpreter.interpreters;

import ch.brickwork.bsuit.database.IDatabase;
import ch.brickwork.bsuit.database.Record;
import ch.brickwork.bsuit.database.Value;
import ch.brickwork.bsuit.database.Variable;
import ch.brickwork.bsuit.globals.IBoilersuitApplicationContext;
import ch.brickwork.bsuit.interpreter.util.WithClauseParser;
import ch.brickwork.bsuit.util.FileIOUtils;
import ch.brickwork.bsuit.util.Partition;
import ch.brickwork.bsuit.util.Partitioning;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * <p>
 * Exports tables into various supported file types, currently csv, xls, xlsx
 * <p/>
 * <h2>Syntax</h2>
 * variablename =: [filename [delim(delimcharacter)][quote(quotecharacter)][replacedelim(replacestring)][replacequote(replacestring)]]<br/>
 * Extensions will be interpreted as follows:
 * <ul>
 *     <li>csv - exports as csv with default options</li>
 *     <li>xls - exports as Microsoft (R) Excel 1997-2003</li>
 *     <li>xlsx - exports as Microsoft (R) Excel</li>
 * </ul>
 * <h2>Samples</h2>
 * <pre>-- as simply as it can get - exports ALL variables
 * </pre>
 * <pre>=:
 * </pre>
 * <br/>
 * <pre>existing_table_name =: file_name.xml;
 * </pre>
 * <pre>existing_table_name =: file_name.xls;
 * </pre>
 * <pre>existing_table_name =: file_name.xlsx;
 * </pre>
 * <pre>existing_table_name =: file_name.csv;
 * </pre>
 * <pre>existing_table_name =: file_name.csv with delim(";"), quote("\'"), replacedelim(","), replacequote("_");
 * </pre>
 * <h3>On the use of delim, quote, etc. (Simple)</h3>
 * <p>To export using ';' as a delimitator (instead of the default ','), you would use</p>
 * <pre>existing_table_name =: file_name.csv with delim(";")
 * </pre>
 * <p>You could also use tabs, like so:</p>
 * <pre>existing_table_name =: file_name.csv with delim("\t")
 * </pre>
 * <p>If you are in conflict with values using the desired symbols, use</p>
 * <pre>-- replace e.g. O'Brien by O\'Brien not to come in conflict with quotes '
 * </pre>
 * <pre>existing_table_name =: file_name.csv with delim(";"), quote("'"), replacequote("\\'")
 * </pre>
 * <h3>On the use of delim, quote, etc. (Advanced)</h3>
 * <ul>
 * <li>Argument of function delim() will be used as a delimitator to separate the strings in csv file.</li>
 * <li>Argument of function quote() will be used as a quote to wrap the strings in csv file.</li>
 * <li>Argument of function replacedelim() will be used for replace all of occurrences of delimitator in exported strings.</li>
 * <li>Argument of function replacequote() will be used for replace all of occurrences of quote in exported strings.</li>
 * </ul>
 * <p/>
 */
public class ExportInterpreter extends AbstractInterpreter {

    private static final String TXT_MORE_THAN_ONE = "You're trying export more than one variable to a single file!";

    private static final String TXT_EXPORTED = "Exported variables to files";
    private static final String TXT_CANT_FIND_VARIABLE = "Couldn't find variable ";
    private static final String TXT_EXPORTED_XLS = "Exported as *.xls (Excel 97-2003). If you wish to export as xlsx, you can use *.xlsx instead.";
    private static final String TXT_EXPORT_NOT_SUPPORTED = "Currently supported files are *.csv, *.xml, *xls and *.xlsx";

    private static final String DB_WILDCARD = "%";

    /**
     * default delimitator sequence
     */
    private static final String DELIMITATOR_FOR_CSV_EXPORT = ", ";

    private static final String EXPORTED_FILE_EXT = ".csv";


    /**
     * default quote sequence
     */
    private static final String QUOTE_CHARACTER_FOR_CSV_EXPORT = "\"";

    /**
     * default replace delimitator sequence
     */
    private static final String REPLACE_DELIMITATOR_DEFAULT = "[?]";

    /**
     * default replace quote sequence
     */
    private static final String REPLACE_QUOTE_DEFAULT = "[?]";

    /**
     * in case of XML output, if the value has less than SAME_LINE_XML_LIMIT characters,
     * no new line is used to write it
     */
    private static final int SAME_LINE_XML_LIMIT = 60;

    /**
     * in case of XML output, the first line to be written in XML file
     */
    private static final String XML_DEF_LINE = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

    /**
     * number of lines that are written on =: file output at once. To avoid memory problems, files are written in
     * partitions of size FILE_WRITE_PARTITION_SIZE (+smaller residual partition at the end)
     */
    private static final int FILE_WRITE_PARTITION_SIZE = 1000;


    private final IDatabase database = context.getDatabase();

    /**
     * the target delimitator
     */
    private String delimitator = DELIMITATOR_FOR_CSV_EXPORT;

    /**
     * the target quote sequence
     */
    private String quote = QUOTE_CHARACTER_FOR_CSV_EXPORT;

    /**
     * if the delimitator sequence happens to be used as part of the value (e.g., if the delimitator is a comma, and
     * a value actually contains a comma), then all the occurences in the value will be replaced with that alternative
     * sequence. For instance, if comma is used as delimitator, semicolon may be used as delimitator replacement
     */
    private String replaceDelimitator = REPLACE_DELIMITATOR_DEFAULT;

    /**
     * same as replaceDelimitator, but for quote
     */
    private String replaceQuote = REPLACE_QUOTE_DEFAULT;
    
    private int delimitatorReplacementCount = 0;
    
    private int quoteReplacementCount = 0;

    private enum SupportedFileExt {
        XLSX,
        XLS,
        XML,
        CSV
    }

    /**
     * stores target variable and command text (both are automatically trimmed) for interpreters
     *
     * @param targetVariable     is target variable
     * @param command            is command text
     * @param context                is Logger instance
     */
    public ExportInterpreter(final Variable targetVariable, final String command, final IBoilersuitApplicationContext context)
    {
        super(targetVariable, command, context);
    }

    @Override
    public boolean needsTargetVariable() { return false; }


    /**
     * Process the command, try to find WITH clause if exists and extract functions like quote() or delim().
     * Next export each variable that match to given name.
     *
     * @return processing result contains the list of exported variables
     */
    @Override

    public ProcessingResult process()
    {
        // interpret options, if any
        // SYNTAX: myvariable =: myfile.ext WITH delim('), quote(), replaceDelim(//), replaceQuote(*);
        int indexOfWITH = command.toLowerCase().indexOf(" with ");
        int secondIndexOfWITH = command.toLowerCase().indexOf(" with ", indexOfWITH + 1);
        if (indexOfWITH != -1) {
            if (!parseWithParameters(indexOfWITH, secondIndexOfWITH)) {
                return new ProcessingResult(ProcessingResult.ResultType.SYNTAX_ERROR,
                    "SYNTAX: myvariable =: myfile.ext WITH delim('), quote(), replaceDelim(//), replaceQuote(*) (replaceXX: optional)");
            }
            // cut off parameters for further processing
            command = command.substring(0, Math.max(indexOfWITH, secondIndexOfWITH));
        }

        final String[] tokens = command.split("=:");
        final int tokensLentght = tokens.length;
        String varName = tokensLentght > 0 ? tokens[0].trim() : DB_WILDCARD;
        if (varName.isEmpty()) {
            varName = DB_WILDCARD;
        }
        String fileName = tokensLentght > 1 ? tokens[1].trim() : "";
        /**
         * We need to escape underscore character and change our wildcard character to db wildcard character
         */
        varName = varName.replaceAll("\\*", DB_WILDCARD);
        varName = varName.replaceAll("_", "\\\\_");
        final List<Variable> variables = context.getDatabase().getVariables(varName);
        if (variables.isEmpty()) {
            return new ProcessingResult(ProcessingResult.ResultType.MESSAGE, TXT_CANT_FIND_VARIABLE + varName.replaceAll("\\\\_", "_"));
        }

        final int variablesLength = variables.size();
        // more than one variables and fileName is given
        if (variablesLength > 1 && !fileName.isEmpty()) {
            context.getLog().err("Syntax error. Check manual.");
            return new ProcessingResult(ProcessingResult.ResultType.SYNTAX_ERROR, TXT_MORE_THAN_ONE);
        }

        final ProcessingResult pr = new ProcessingResult(ProcessingResult.ResultType.COMPOSITE, TXT_EXPORTED);

        //Prepare sub results for each exporting table
        for (final Variable v : variables) {
            if (variablesLength != 1 || fileName.isEmpty()) {
                fileName = v.getVariableName();
                if (!fileName.endsWith(EXPORTED_FILE_EXT)) {
                    fileName = fileName + EXPORTED_FILE_EXT;
                }
            }

            if (!FileIOUtils.checkPath(context.getWorkingDirectory() + File.separator + fileName)) {
                final String exceptionMessage = "You have no write and read access to file or directory: '" + context.getWorkingDirectory() + File.separator + fileName + "' or path doesn't exists!";
                return new ProcessingResult(ProcessingResult.ResultType.FATAL_ERROR, exceptionMessage, command);
            }

            pr.addSubResult(exportVariable(v.getVariableName(), fileName));
            
            if(delimitatorReplacementCount > 0) 
                context.getLog().warn("Delimitator character " + delimitator + " is used within values and was replaced by " + REPLACE_DELIMITATOR_DEFAULT + " " + delimitatorReplacementCount + " times. Use replacedelim(...) parameter to improve.");
            
            if(quoteReplacementCount > 0)
                context.getLog().warn("Quote character " + quote + " is used within values and was replaced by " + REPLACE_QUOTE_DEFAULT + " " + quoteReplacementCount + " times. Use replacequote(...) parameter to improve.");
        }

        return pr;
    }

    @Override
    public boolean understands()
    {
        return command.contains("=:");
    }

    /**
     * Uses existing table, converts it to CSV format and saves in file.
     *
     * @param tableOrViewName table name which will be exported
     * @param fileName        file name which will be created
     * @param partitions      part of data from database
     */
    private void exportToCsv(final String tableOrViewName, final String fileName, final Partitioning partitions)
    {
        try {
            // open or create file and writer

            final FileWriter fw = new FileWriter(context.getWorkingDirectory() + File.separator + fileName);
            final StringBuilder fileContent = new StringBuilder();

            // write
            boolean firstLine = true;

            for (final Partition partition : partitions) {
                context.getLog().info("Reading result for partition...");
                final List<Record> recordsInPartition = database.getAllRecordsFromTableOrView(tableOrViewName, partition.getFirstRecord(),
                    partition.getLength(), null, null);
                if (null != recordsInPartition) {
                    for (final Record record : recordsInPartition) {
                        // first, write header line
                        if (firstLine) {
                            boolean firstAttribute = true;
                            StringBuilder header = new StringBuilder();
                            for (Value v : record) {
                                if (firstAttribute) {
                                    firstAttribute = false;
                                } else {
                                    header.append(delimitator);
                                }

                                header.append(quote);
                                header.append(preProcessValue(v.getAttributeName()));
                                header.append(quote);
                            }
                            fileContent.append(header);
                            fileContent.append("\n");

                            firstLine = false;
                        }

                        // then, write records
                        boolean firstAttribute = true;
                        final StringBuilder line = new StringBuilder();
                        for (final Value v : record) {
                            if (firstAttribute) {
                                firstAttribute = false;
                            } else {
                                line.append(delimitator);
                            }
                            line.append(quote);
                            line.append(preProcessValue(v.getValue().toString()));
                            line.append(quote);
                        }
                        fileContent.append(line);
                        fileContent.append("\n");
                    }
                }
            }

            // write to file
            fw.append(fileContent.toString());

            fw.flush();
            fw.close();
        } catch (IOException e) {
            context.getLog().err(e.getMessage());
        }
    }

    /**
     * Uses existing table, converts it to XLS format and saves in file.
     *
     * @param tableOrViewName table name which will be exported
     * @param fileName        file name which will be created
     * @param partitions      part of data from database
     * @param workbook        Workbook object, determines which version of Excel format will be used (XSSFWorkbook object for XLSX and HSSFWorkbook for XLS)
     */
    private void exportToXLS(final String tableOrViewName, final String fileName, final Partitioning partitions,
                             final Workbook workbook)
    {
        final Sheet sheet = workbook.createSheet(tableOrViewName);

        for (final Partition partition : partitions) {
            context.getLog().info("Reading result for partition...");
            final List<Record> recordsInPartition = database.getAllRecordsFromTableOrView(tableOrViewName, partition.getFirstRecord(), partition.getLength(),
                null, null);

            int rowNum = 0;
            boolean first = true;

            if (null != recordsInPartition) {
                for (final Record record : recordsInPartition) {
                    context.getLog().info("...done. Write records " + partition.getFirstRecord() + "" + (partition.getFirstRecord() + partition.getLength() - 1));
                    Row row = sheet.createRow(rowNum++);
                    int cellNum = 0;
                    if (first) {
                        for (final String columnName : record.getColumnNames()) {
                            final Cell cell = row.createCell(cellNum++);
                            cell.setCellValue(columnName);
                        }
                        cellNum = 0;
                        row = sheet.createRow(rowNum++);
                        first = false;
                    }
                    for (final Value v : record) {
                        final Cell cell = row.createCell(cellNum++);
                        cell.setCellValue(v.getValue().toString());
                    }
                }
            }
        }

        final FileOutputStream out;
        try {
            out = new FileOutputStream(new File(context.getWorkingDirectory() + File.separator + fileName));
            workbook.write(out);
            out.close();
        } catch (IOException e) {
            context.getLog().err(e.getMessage());
        }
    }

    /**
     * Uses existing table, converts it to xml format and saves in file.
     *
     * @param tableOrViewName table name which will be exported
     * @param fileName        file name which will be created
     * @param partitions      part of data from database
     */
    private void exportToXML(final String tableOrViewName, final String fileName, final Partitioning partitions)
    {

        final FileWriter fw;
        try {
            fw = new FileWriter(context.getWorkingDirectory() + File.separator + fileName);

            final StringBuilder fileContent = new StringBuilder();
            fileContent.append(XML_DEF_LINE);
            fileContent.append("\n<DocumentElement>");
            for (final Partition partition : partitions) {
                final List<Record> recordsInPartition = database.getAllRecordsFromTableOrView(tableOrViewName, partition.getFirstRecord(),
                    partition.getLength(), null, null);
                if (null != recordsInPartition) {
                    for (final Record record : recordsInPartition) {
                        context.getLog().info("...done. Write records " + partition.getFirstRecord() + "" + (partition.getFirstRecord() + partition.getLength() - 1));

                        fileContent.append("\n<");
                        fileContent.append(tableOrViewName);
                        fileContent.append(">");

                        final StringBuilder line = new StringBuilder();
                        for (Value v : record) {
                            line.append("\n\t<");
                            line.append(v.getAttributeName());
                            line.append(">");
                            final String escapedXmlValue = StringEscapeUtils.escapeXml(v.getValue().toString());
                            if (v.getValue().toString().length() < SAME_LINE_XML_LIMIT) {
                                line.append(escapedXmlValue);
                                line.append("</");
                                line.append(v.getAttributeName());
                                line.append(">");
                            } else {
                                line.append("\n\t\t");
                                line.append(escapedXmlValue);
                                line.append("\n\t");
                                line.append("</");
                                line.append(v.getAttributeName());
                                line.append(">");
                            }
                        }
                        fileContent.append(line);

                        fileContent.append("\n</");
                        fileContent.append(tableOrViewName);
                        fileContent.append(">");
                    }
                }
            }
            fileContent.append("\n</DocumentElement>");

            // write to file
            fw.append(fileContent.toString());
            fw.flush();
            fw.close();
        } catch (IOException e) {
            context.getLog().err(e.getMessage());
        }
    }

    /**
     * Exports variable 'tableOrViewName' to file with name 'fileName'
     *
     * @param tableOrViewName is name of table or view
     * @param fileName        is name of file
     *
     * @return processing result with message whether exported successfully or not
     */

    private ProcessingResult exportVariable(String tableOrViewName, final String fileName)
    {
        if (!database.existsTableOrView(tableOrViewName)) {
            tableOrViewName = Variable.getTableName(tableOrViewName);
            if (!database.existsTableOrView(tableOrViewName)) {
                context.getLog().err(command + " does not exist!");
                return new ProcessingResult(ProcessingResult.ResultType.SYNTAX_ERROR, tableOrViewName + " does not exist!");
            }
        }

        // where is the last point in the filename (before last extension begins)
        int pointPos = -1;
        while (fileName.indexOf(".", pointPos + 1) > pointPos) {
            pointPos = fileName.indexOf(".", pointPos + 1);
        }

        SupportedFileExt fileExt;

        final String fileExtStr = fileName.substring(pointPos + 1).toUpperCase();

        if (-1 == pointPos) {
            fileExt = SupportedFileExt.CSV;
        } else {
            try {
                fileExt = SupportedFileExt.valueOf(fileExtStr);
            } catch (IllegalArgumentException e) {
                return new ProcessingResult(ProcessingResult.ResultType.MESSAGE, TXT_EXPORT_NOT_SUPPORTED);
            }
        }

        final Partitioning partitions = new Partitioning(database.count(tableOrViewName), FILE_WRITE_PARTITION_SIZE);
        if (SupportedFileExt.CSV.equals(fileExt)) {
            exportToCsv(tableOrViewName, fileName, partitions);
        } else if (SupportedFileExt.XML.equals(fileExt)) {
            exportToXML(tableOrViewName, fileName, partitions);
        } else if (SupportedFileExt.XLSX.equals(fileExt)) {
            exportToXLS(tableOrViewName, fileName, partitions, new XSSFWorkbook());
        } else if (SupportedFileExt.XLS.equals(fileExt)) {
            exportToXLS(tableOrViewName, fileName, partitions, new HSSFWorkbook());
        }

         String message;
        if (fileName.endsWith(".xls")) {
            message = TXT_EXPORTED_XLS;
        } else {
            message = TXT_EXPORTED;
        }
        return new ProcessingResult(ProcessingResult.ResultType.MESSAGE, message, script);
    }

    /**
     * @param indexOfWITH       index of 'WITH' clause find in command string
     * @param secondIndexOfWITH index of second occurs of 'WITH' clause find in command string
     *
     * @return true if command contains delim() or quote() functions
     */
    private boolean parseWithParameters(int indexOfWITH, int secondIndexOfWITH)
    {
        WithClauseParser wcp = new WithClauseParser(command.substring(Math.max(indexOfWITH, secondIndexOfWITH)));
        boolean delimOrQuoteSet = false;
        if (wcp.parse()) {
            final List<String> delimList = wcp.getArgumentsIgnoreCase("delim");
            if (null != delimList && !delimList.isEmpty()) {
                delimitator = delimList.get(0);
                delimOrQuoteSet = true;
            }
            final List<String> quoteList = wcp.getArgumentsIgnoreCase("quote");
            if (null != quoteList && !quoteList.isEmpty()) {
                quote = quoteList.get(0);
                delimOrQuoteSet = true;
            }
            final List<String> replacedelimList = wcp.getArgumentsIgnoreCase("replacedelim");
            if (null != replacedelimList && !replacedelimList.isEmpty()) {
                replaceDelimitator = replacedelimList.get(0);
            }
            final List<String> replacequoteList = wcp.getArgumentsIgnoreCase("replacequote");
            if (null != replacequoteList && !replacequoteList.isEmpty()) {
                replaceQuote = replacequoteList.get(0);
            }
        }
        return delimOrQuoteSet;
    }

    /**
     * @param rawValue exporting value
     * @return exporting value after replaced the characters of used delimitator or quote
     */
    private String preProcessValue(final String rawValue)
    {
        String value = rawValue;
        if (!delimitator.equals("")) {
            if(value.contains(delimitator)) {
                value = value.replace(delimitator, replaceDelimitator);
                delimitatorReplacementCount++;
            }
        }
        if (!quote.equals("")) {
            if(value.contains(quote)) {
                value = value.replace(quote, replaceQuote);
                quoteReplacementCount++;
            }
        }
        return value;
    }
}
