package ch.brickwork.bsuit.interpreter.util;

import ch.brickwork.bsuit.interpreter.interpreters.IInterpreter;
import java.util.List;

/**
 * Created by marcel on 06.08.15.
 */
public interface ICommandInterpreter extends IInterpreter {
    void setInterpreters(List<IInterpreter> interpreters);

    String getCoreVersion();
}
