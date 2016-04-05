package ch.brickwork.bsuit;


import ch.brickwork.bsuit.database.IDatabase;
import ch.brickwork.bsuit.database.IFileBasedDatabase;
import ch.brickwork.bsuit.database.SQLiteDatabase;
import ch.brickwork.bsuit.globals.BoilerSuitGlobals;
import ch.brickwork.bsuit.globals.IBoilersuitApplicationContext;
import ch.brickwork.bsuit.interpreter.DefaultCommandInterpreterFactory;
import ch.brickwork.bsuit.interpreter.ScriptProcessor;
import ch.brickwork.bsuit.interpreter.interpreters.ProcessingResult;
import ch.brickwork.bsuit.interpreter.util.ICommandInterpreterFactory;
import ch.brickwork.bsuit.util.FileIOUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Scanner;

/**
 * Created by marcel on 29.07.15.
 */
public class BoilerSuitTestContext {
    private final File dir;
    private final TestLog testLog;
    private IDatabase db;

    private final IBoilersuitApplicationContext context;

    private final ICommandInterpreterFactory commandInterpreterFactory;

    /**
     * test using default database
     */
    public BoilerSuitTestContext(ICommandInterpreterFactory commandInterpreterFactory, IBoilersuitApplicationContext context) {
        this.context = context;

        System.out.println(commandInterpreterFactory.createCommandInterpreter(null, "", context, "").getCoreVersion());

        this.commandInterpreterFactory = commandInterpreterFactory;

        final String fileName = "boilersuittests" + Math.random() * 1E7;
        dir = new File(fileName);
        if(dir.exists()) {
            for(File f : dir.listFiles())
                f.delete();
            dir.delete();
        }
        dir.mkdir();
        context.setWorkingDirectory(dir.getAbsolutePath());

        context.setLog(testLog = new TestLog());

        context.setDatabase(db = new SQLiteDatabase(dir.getAbsolutePath(), context.getLog()));

        if(db instanceof  IFileBasedDatabase)
            ((IFileBasedDatabase) db).changeDBFileDirectory(dir.getAbsolutePath());
    }

    public BoilerSuitTestContext() {
        this(new DefaultCommandInterpreterFactory(), BoilerSuitGlobals.getApplicationContext());
    }

    public IDatabase db() {
        return db;
    }

    public ProcessingResult processScript(String script) {
        return new ScriptProcessor(commandInterpreterFactory, context).processScript(script, null, null);
    }

    /**
     * creates a file within the temporary folder dedicated to testing
     * @param name
     * @return
     */
    public File createFile(String name) {
        File f = new File(dir, name);
        if(f.exists()) f.delete();
        try {
            f.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return f;
    }

    /**
     * creates or replaces a file within temporary folder, and writes text to it
     * @return
     */
    public void writeToFile(String fileName, String text) {
        File f = createFile(fileName);
        FileIOUtils.overwriteFile(f.getAbsolutePath(), text);
    }

    public TestLog getTestLog() {
        return testLog;
    }

    public void flush() {
        testLog.flush();
    }

    public String getResource(String fileName) {
        StringBuilder result = new StringBuilder("");

        // Get file from resources folder
        ClassLoader classLoader = getClass().getClassLoader();

        BufferedReader br = new BufferedReader(new InputStreamReader(classLoader.getResourceAsStream(fileName)));

        try (Scanner scanner = new Scanner(br)) {

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                result.append(line).append("\n");
            }

            scanner.close();
        }

        return result.toString();
    }


    public boolean noErrors() {
        return testLog.getErrLog().keySet().size() == 0;
    }

    public boolean noWarnings() {
        return testLog.getWarnLog().keySet().size() == 0;
    }

    public boolean noErrorsOrWarnings() {
        return noErrors() && noWarnings();
    }



    /**
     * reads file in working directory
     */
    public String readCompleteFile(String fileName) {
        return FileIOUtils.readCompleteFile(context.getWorkingDirectory(), fileName);
    }

    public void dispose() {
        System.out.println("delete " + dir.getAbsolutePath());
        FileIOUtils.deleteRuthlessly(new File(dir.getAbsolutePath()));
    }

    public IBoilersuitApplicationContext getContext() {
        return context;
    }
}
