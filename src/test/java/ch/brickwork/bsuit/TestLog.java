package ch.brickwork.bsuit;

import ch.brickwork.bsuit.util.ILog;

import java.util.Hashtable;

/**
 * Created by marcel on 29.07.15.
 */
public class TestLog implements ILog {
    private Hashtable<String, String> errLog = new Hashtable<>();
    private Hashtable<String, String> infoLog = new Hashtable<>();
    private Hashtable<String, String> logLog = new Hashtable<>();
    private Hashtable<String, String> warnLog = new Hashtable<>();

    @Override
    public void err(String s) {
        System.out.println("ERR: " + s);
        errLog.put(s, s);
    }

    @Override
    public void info(String s) {
        System.out.println("INFO: " + s);
        infoLog.put(s, s);
    }

    @Override
    public void log(String s) {
        System.out.println("LOG: " + s);
        logLog.put(s, s);
    }

    @Override
    public void warn(String s) {
        System.out.println("WARN: " + s);
        warnLog.put(s, s);
    }

    public Hashtable<String, String> getErrLog() {
        return errLog;
    }

    public Hashtable<String, String> getInfoLog() {
        return infoLog;
    }

    public Hashtable<String, String> getLogLog() {
        return logLog;
    }

    public Hashtable<String, String> getWarnLog() {
        return warnLog;
    }

    public void flush() {
        errLog = new Hashtable<>();
        infoLog = new Hashtable<>();
        logLog = new Hashtable<>();
        warnLog = new Hashtable<>();
    }

    public boolean isMentionedInInfoLog(String s) {
        return isMentionedInLog(s, infoLog);
    }

    public boolean isMentionedInWarnLog(String s) {
        return isMentionedInLog(s, warnLog);
    }

    public boolean isMentionedInErrLog(String s) {
        return isMentionedInLog(s, errLog);
    }


    private boolean isMentionedInLog(String s, Hashtable<String, String> log) {
        for(String key : log.keySet()) {
            if(key.toLowerCase().contains(s.toLowerCase()))
                return true;
        }
        return false;
    }

    private String serializeLogTable(Hashtable h) {
        String s = "";
        for(Object key : h.keySet())
            s += key + "\n";
        return s;
    }

    public String toString() {
        return "TESTLOG " + this.hashCode() + "\n" +
                "ERRORS\n" +
                serializeLogTable(errLog) + "\n" +
                "\nWARNINGS\n" +
                serializeLogTable(warnLog) + "\n" +
                "\nINFO\n" +
                serializeLogTable(infoLog) + "\n";
    }
}
