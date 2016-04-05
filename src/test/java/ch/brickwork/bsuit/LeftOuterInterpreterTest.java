package ch.brickwork.bsuit;

import ch.brickwork.bsuit.database.Record;
import org.junit.BeforeClass;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.assertEquals;

/**
 * Created by marcel on 3/31/16.
 */
public class LeftOuterInterpreterTest {
    private static BoilerSuitTestContext tc = new BoilerSuitTestContext();

    @BeforeClass
    public static void initFiles() {
        tc.writeToFile("customers.csv", tc.getResource("customers.csv"));
        tc.processScript("c1 := customers.csv;");
        tc.processScript("c2 := c1;");
        tc.processScript("UPDATE c1 SET id='1 only in c1' WHERE id='1';");
        tc.processScript("UPDATE c2 SET id='1 only in c2' WHERE id='1';");
    }

    @Test
    public void testLj() {
        tc.processScript("lj := c1(id) -> c2(id);");
        assertEquals("count", 100, tc.getContext().getDatabase().count("lj"));

        List<Record> notmatching = tc.getContext().getDatabase().prepare("SELECT c1_id, c1_name, c2_name FROM lj WHERE c1_id='1 only in c1'");
        assertEquals("count non matches", 1, notmatching.size());
        assertEquals("eq non matches", "", notmatching.get(0).getValue("c2_name").getValue());

        List<Record> matching = tc.getContext().getDatabase().prepare("SELECT c1_id, c1_name, c2_name FROM lj WHERE c1_id<>'1 only in c1'");
        assertEquals("count matches", 99, matching.size());
        assertEquals("eq matches", matching.get(0).getValue("c1_name").getValue(), matching.get(0).getValue("c2_name").getValue());
    }
}
