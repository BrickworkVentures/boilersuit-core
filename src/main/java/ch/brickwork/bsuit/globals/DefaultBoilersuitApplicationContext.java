package ch.brickwork.bsuit.globals;

import ch.brickwork.bsuit.database.IDatabase;
import ch.brickwork.bsuit.database.IFileBasedDatabase;
import ch.brickwork.bsuit.database.SQLiteDatabase;
import ch.brickwork.bsuit.util.ILog;
import ch.brickwork.bsuit.util.Log;

/**
 * Created by marcel on 12/22/15.
 */
public class DefaultBoilersuitApplicationContext implements IBoilersuitApplicationContext {
    public   String defaultWorkingDirectory;

    private  IDatabase database;

    public  ILog log;
    public static String workingDirectory;

    public DefaultBoilersuitApplicationContext() {
        setLog(new Log());
        workingDirectory = defaultWorkingDirectory = ".";
        setDatabase(new SQLiteDatabase(workingDirectory, log));
    }

    @Override
    public String getWorkingDirectory() {
        return workingDirectory;
    }

    @Override
    public void setWorkingDirectory(String path) {
        workingDirectory = path;
    }

    public boolean changeDBFileDirectory(String path) {
        if(getDatabase() instanceof IFileBasedDatabase) {
            return ((IFileBasedDatabase) getDatabase()).changeDBFileDirectory(path);
        }
        else return false;
    }

    @Override
    public IDatabase getDatabase() {
        return database;
    }

    @Override
    public void setDatabase(IDatabase database) {
        this.database = database;
    }

    @Override
    public ILog getLog() {
        return log;
    }

    @Override
    public void setLog(ILog log) {
        this.log = log;
    }
}
