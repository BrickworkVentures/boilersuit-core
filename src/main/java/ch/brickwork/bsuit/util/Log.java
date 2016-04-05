package ch.brickwork.bsuit.util;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

public class Log implements ILog {

    private static final Logger LOG = Logger.getLogger(Log.class.getCanonicalName());

    private final File logFile;

    private StringBuffer logBuffer;

    private boolean isBlackHole;

    private boolean closed = false;

    public Log() {
        this(false);
    }

    public Log(boolean isBlackHole) {
        this(null);
        this.isBlackHole = isBlackHole;
    }

    public Log(File logFile) {
        this.logFile = logFile;
        if(logFile != null)
            initLogBuffer();
    }

    public static void publishMessage(LogMessage message, ILog log) {
        switch (message.getType()) {
            case ERR:
                log.err(message.getText());
                break;
            case INFO:
                log.info(message.getText());
                break;
            case WARN:
                log.warn(message.getText());
                break;
            case LOG:
                log.log(message.getText());
                break;
        }
    }

    public void err(String string) {
        if (!isBlackHole)
            LOG.severe(string);
    }

    public void info(String string) {
        if (!isBlackHole)
            LOG.info(string);
    }

    public void log(String string) {
        if (!isBlackHole) {
            if(logFile != null) {
                logBuffer.append("\n");
                logBuffer.append(getCurrentDateAndTime());
                logBuffer.append(" ");
                logBuffer.append(string);
            }
            LOG.info(string);
        }
    }

    public void warn(String string) {
        if (!isBlackHole)
            LOG.warning(string);
    }


    private void initLogBuffer() {
        String existingLog = FileIOUtils.readCompleteFile(logFile);
        if(existingLog == null)
            existingLog = "";

        logBuffer = new StringBuffer(existingLog);
        logBuffer.append("\n\n");
    }

    private String getCurrentDateAndTime() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Date date = new Date();
        return dateFormat.format(date);
    }

    public void close() {
        FileIOUtils.overwriteFile(logFile.getAbsolutePath(), logBuffer.toString());
        closed = true;
    }

    public void finalize() {
        if(!closed)
            close();
     }
}
