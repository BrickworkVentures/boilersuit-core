package ch.brickwork.bsuit;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Created by marcel on 5/10/16.
 */
public class StatisticsInterpreterTest {
    BoilerSuitTestContext tc = new BoilerSuitTestContext();

    @Before
    public void prepare() {
        // write files from resources
        tc.writeToFile("companylist.csv", tc.getResource("companylist.csv"));
        tc.processScript("c := companylist.csv;");
    }

    @Test
    public void testUnique() {
        assertEquals(true, tc.processScript("?c(name)").getResultSummary().contains("DUPLICATES!"));
    }
}
