package ch.brickwork.bsuit;

import ch.brickwork.bsuit.interpreter.interpreters.ProcessingResult;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Created by marcel on 4/18/16.
 */
public class NativeSQLInterpreterTest {
    BoilerSuitTestContext tc = new BoilerSuitTestContext();

    @Test
    public void dropTable() {
        tc.processScript("+tbl(a, b, c);");
        ProcessingResult pr = tc.processScript("DROP TABLE tbl");
        assertEquals("no err", true, tc.noErrors());
        assertEquals("warn", true, tc.getTestLog().isMentionedInWarnLog("You should use"));
        assertEquals("dropped", false, tc.getContext().getDatabase().existsTableOrView("tbl"));
    }
}
