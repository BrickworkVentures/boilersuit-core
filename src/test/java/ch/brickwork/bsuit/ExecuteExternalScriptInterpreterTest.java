package ch.brickwork.bsuit;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * Created by marcel on 12/23/15.
 */
public class ExecuteExternalScriptInterpreterTest {
    private static BoilerSuitTestContext tc = new BoilerSuitTestContext();

    @BeforeClass
    public static void prepareFile() {
        // write files from resources
        tc.writeToFile("us-500.csv", tc.getResource("us-500.csv"));

        tc.writeToFile("testscript.bs",
                "usa := us-500.csv;               usb := usa; -- this is a comment\n" +
                "-- this is a comment\n" +
                "-usb;\n" +
                "--end"
        );
    }

    @Test
    public void testScript() {
        // with wrong syntax (no brackets)
        tc.processScript("execute(testscript.bs)");
        assertEquals(true, tc.getTestLog().isMentionedInErrLog("Please use:"));

        // with correct syntax
        tc.flush();
        tc.processScript("execute(\"testscript.bs\")");
        assertEquals(true, tc.db().existsTable("usa"));
        assertEquals(false, tc.db().existsTable("usb"));
        assertEquals(true, tc.noErrors());
    }

    @AfterClass
    public static void cleanUp() {
        tc.dispose();
    }
}
