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
        assertEquals("true", pr.getSingleValue());
        assertEquals(true, pr.getResultSummary().contains("PASSED"));
        assertEquals(true, pr.getScript().contains(script));    // contains because it may add ';', which is ok
        assertEquals(0, tc.getTestLog().getErrLog().size());
        assertEquals(true, tc.getTestLog().isMentionedInInfoLog("PASSED"));
        tc.flush();

        tc.flush();
        script = "ASSERT #carowners = 3 ELSE WARN";
        pr = tc.processScript(script);
        assertEquals("false", pr.getSingleValue());
        assertEquals(true, pr.getResultSummary().contains("FAILED"));
        assertEquals(true, pr.getScript().contains(script));    // contains because it may add ';', which is ok
        assertEquals(0, tc.getTestLog().getErrLog().size());
        assertEquals(true, tc.getTestLog().isMentionedInInfoLog("FAILED"));
        assertEquals(true, tc.getTestLog().isMentionedInWarnLog("FAILED"));

        tc.flush();
        script = "ASSERT #carowners = 3 ELSE STOP";
        pr = tc.processScript(script);
        assertEquals("false", pr.getSingleValue());
        assertEquals(true, pr.getResultSummary().contains("FAILED"));
        assertEquals(true, pr.getScript().contains(script));    // contains because it may add ';', which is ok
        assertEquals(true, tc.getTestLog().isMentionedInInfoLog("FAILED"));


        tc.flush();
        script = "ASSERT SELECT COUNT(*) FROM carowners = 3 ELSE STOP";
        pr = tc.processScript(script);
        assertEquals("false", pr.getSingleValue());
        assertEquals(true, pr.getResultSummary().contains("FAILED"));
        assertEquals(true, pr.getScript().contains(script));    // contains because it may add ';', which is ok
        assertEquals(true, tc.getTestLog().isMentionedInInfoLog("FAILED"));

    }

    @AfterClass
    public static void cleanUp() {
        tc.dispose();
    }
}
