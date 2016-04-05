package ch.brickwork.bsuit.database;

/**
 * Created by marcel on 12/15/15.
 */
public interface IFileBasedDatabase {

    /**
     * gets the default file name used for the database (traditionally, BoilerSuit uses data.db for this purpose)
     */
    String getDefaultDBFileName();

    /**
     * change path of database. if the default file name exists already under the new path, the existing file
     * is openened. If not, the file from the old location is moved
     * @return true if successful
     * @TODO this is not sooooo logical of course
     */
    boolean changeDBFileDirectory(String newPath);
}
