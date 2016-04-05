package ch.brickwork.bsuit;

import ch.brickwork.bsuit.interpreter.interpreters.ProcessingResult;
import org.junit.AfterClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Created by marcel on 12/15/15.
 */
public class VariableInterpreterTest {
    private static BoilerSuitTestContext tc = new BoilerSuitTestContext();

    @Test
    public void testCountInterpreter() {
        // write files from resources
        tc.writeToFile("carowners.csv", tc.getResource("carowners.csv"));

        // read in files
        tc.processScript("carowners := carowners.csv;");
        ProcessingResult pr = tc.processScript("carowners;");

        pr = tc.processScript("select * from carowners;");

        assertEquals(4, tc.db().count("carowners"));
        System.out.println(tc.getTestLog().getErrLog().size());
        System.out.println(tc.getTestLog().getWarnLog().size());
        System.out.println(tc.getTestLog().getInfoLog().size());
    }

    @AfterClass
    public static void cleanUp() {
        tc.dispose();
    }
}
