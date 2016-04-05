package ch.brickwork.bsuit;

import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * Created by marcel on 3/31/16.
 */
public class FrequencyDifferenceInterpreterTest {
    private static BoilerSuitTestContext tc = new BoilerSuitTestContext();

    @BeforeClass
    public static void initFiles() {
        tc.writeToFile("customers.csv", tc.getResource("customers.csv"));
        tc.processScript("c1 := customers.csv;");
        tc.processScript("c2 := c1;");
        tc.processScript("update c1 set zip='5000' where zip like '5%';");
        tc.processScript("update c2 set zip='6000' where zip like '6%';");
    }

    @Test
    public void testLj() {
        tc.processScript("fd := c1.zip ./. c2.zip;");

        tc.processScript("x := SELECT * FROM fd WHERE c2_count < 1;");
        assertEquals("count2", 8, tc.getContext().getDatabase().count("x"));

        tc.processScript("y := SELECT * FROM fd WHERE c1_count < 1;");
        assertEquals("count1", 9, tc.getContext().getDatabase().count("y"));
    }
}
