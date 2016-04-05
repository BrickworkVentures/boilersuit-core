package ch.brickwork.bsuit.util;

/**
 * User: marcel
 * Date: 5/15/13
 * Time: 10:19 AM
 */
public interface ILog {

    void err(String message);

    void info(String message);

    void log(String message);

    void warn(String message);
}
