package ch.brickwork.bsuit;

import ch.brickwork.bsuit.interpreter.interpreters.ProcessingResult;
import org.junit.AfterClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * Created by marcel on 12.08.15.
 */
public class AssertInterpreterTest {
    private static BoilerSuitTestContext tc = new BoilerSuitTestContext();

    /**
     * run all tests
     * don't remove - professional assert version is using this to test standard assert behaviour
     */
    public void testAll() {
        testCountInterpreter();
    }

    @Test
    public void testCountInterpreter() {
        // write files from resources
        tc.writeToFile("carowners.csv", tc.getResource("carowners.csv"));

        // read in files
        tc.processScript("carowners := carowners.csv;");

        tc.flush();
        String script = "ASSERT #carowners = 4 ELSE WARN";
        ProcessingResult pr = tc.processScript(script);
        assertEquals("(1a)", "true", pr.getSingleValue());
        assertEquals("(1b)", true, pr.getResultSummary().contains("PASSED"));
        assertEquals("(1c)", true, pr.getScript().contains(script));    // contains because it may add ';', which is ok
        assertEquals("(1d)", 0, tc.getTestLog().getErrLog().size());
        assertEquals("(1e)", true, tc.getTestLog().isMentionedInInfoLog("PASSED"));
        tc.flush();

        tc.flush();
        script = "ASSERT #carowners = 3 ELSE WARN";
        pr = tc.processScript(script);
        assertEquals("(2a)", "false", pr.getSingleValue());
        assertEquals("(2b)", true, pr.getResultSummary().contains("FAILED"));
        assertEquals("(2c)", true, pr.getScript().contains(script));    // contains because it may add ';', which is ok
        assertEquals("(2d)", 0, tc.getTestLog().getErrLog().size());
        assertEquals("(2e)", true, tc.getTestLog().isMentionedInInfoLog("FAILED"));
        assertEquals("(2f)", true, tc.getTestLog().isMentionedInWarnLog("FAILED"));

        tc.flush();
        script = "ASSERT #carowners = 3 ELSE STOP";
        pr = tc.processScript(script);
        assertEquals("(3a)", "false", pr.getSingleValue());
        assertEquals("(3b)", true, pr.getResultSummary().contains("FAILED"));
        assertEquals("(3c)", true, pr.getScript().contains(script));    // contains because it may add ';', which is ok
        assertEquals("(3d)", true, tc.getTestLog().isMentionedInInfoLog("FAILED"));


        tc.flush();
        script = "ASSERT SELECT COUNT(*) FROM carowners = 3 ELSE STOP";
        pr = tc.processScript(script);
        assertEquals("(4a)", "false", pr.getSingleValue());
        assertEquals("(4b)", true, pr.getResultSummary().contains("FAILED"));
        assertEquals("(4c)", true, pr.getScript().contains(script));    // contains because it may add ';', which is ok
        assertEquals("(4d)", true, tc.getTestLog().isMentionedInInfoLog("FAILED"));

        tc.flush();
        tc.processScript("co := carowners");
        script = "ASSERT #carowners = #co ELSE STOP";
        pr = tc.processScript(script);
        assertEquals("(5a)", "true", pr.getSingleValue());
        assertEquals("(5b)", true, pr.getResultSummary().contains("PASSED"));
        assertEquals("(5c)", true, pr.getScript().contains(script));    // contains because it may add ';', which is ok
        assertEquals("(5d)", 0, tc.getTestLog().getErrLog().size());
        assertEquals("(5e)", true, tc.getTestLog().isMentionedInInfoLog("PASSED"));
    }

    @AfterClass
    public static void cleanUp() {
        tc.dispose();
    }
}
