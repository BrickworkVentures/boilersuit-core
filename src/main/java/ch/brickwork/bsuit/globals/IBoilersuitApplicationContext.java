package ch.brickwork.bsuit.globals;

import ch.brickwork.bsuit.database.IDatabase;
import ch.brickwork.bsuit.util.ILog;

/**
 * Created by marcel on 12/22/15.
 */
public interface IBoilersuitApplicationContext {

    String getWorkingDirectory();

    void setWorkingDirectory(String path);

    boolean changeDBFileDirectory(String path);

    IDatabase getDatabase();

    void setDatabase(IDatabase database);

    ILog getLog();

    void setLog(ILog log);
}
