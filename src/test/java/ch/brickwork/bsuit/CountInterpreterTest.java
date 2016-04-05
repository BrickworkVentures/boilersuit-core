package ch.brickwork.bsuit;

import ch.brickwork.bsuit.interpreter.interpreters.ProcessingResult;
import org.junit.AfterClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Created by marcel on 06.08.15.
 */
public class CountInterpreterTest {
    private static BoilerSuitTestContext tc = new BoilerSuitTestContext();

    @Test
    public void testCountInterpreter() {
        // write files from resources
        tc.writeToFile("carowners.csv", tc.getResource("carowners.csv"));

        // read in files
        tc.processScript("carowners := carowners.csv;");
        ProcessingResult pr = tc.processScript("#carowners");
        assertEquals(true, pr.getSingleValue().equals("" + tc.db().count("carowners")));

        pr = tc.processScript("#carowners;");
        assertEquals("4", pr.getSingleValue());

        tc.flush();
        pr = tc.processScript("#carowners(cartype = 'none');");
        assertEquals("2", pr.getSingleValue());

        tc.flush();
        pr = tc.processScript("#carowners(cartype like 'no%');");
        assertEquals("2", pr.getSingleValue());
    }

    @AfterClass
    public static void cleanUp() {
        tc.dispose();
    }
}
