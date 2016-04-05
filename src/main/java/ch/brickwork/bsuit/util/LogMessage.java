package ch.brickwork.bsuit.util;

/**
 * User: marcel
 * Date: 5/15/13
 * Time: 12:29 PM
 */
public class LogMessage {

    private final String text;

    private final MessageType type;

    public LogMessage(MessageType type, String text) {
        this.type = type;
        this.text = text;
    }


    public String getText()
    {
        return text;
    }

    public MessageType getType()
    {
        return type;
    }

    public enum MessageType {
        ERR,
        INFO,
        WARN,
        LOG
    }
}
