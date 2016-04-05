package ch.brickwork.bsuit;

import ch.brickwork.bsuit.interpreter.interpreters.ProcessingResult;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * Created by marcel on 3/24/16.
 */
public class MatchInterpreterTest {
    private static BoilerSuitTestContext tc = new BoilerSuitTestContext();

    @BeforeClass
    public static void initFiles() {
        tc.writeToFile("customers.csv", tc.getResource("customers.csv"));
        tc.writeToFile("us-500.csv", tc.getResource("us-500.csv"));
        tc.processScript("c := customers.csv;");
        tc.processScript("u := us-500.csv;");
        tc.processScript("un := u(first_name+' '+last_name AS name, *);");
    }

    @Test
    public void testMatch() {
        ProcessingResult pr = tc.processScript("m := MATCH un(name) ON c(name) WITH THRESHOLD(0.85);");
        assertEquals("Fuzzy table exists", true, tc.getContext().getDatabase().existsTable("m_fuzzy_matches"));
        assertEquals("Count of fuzzy entries", 6, tc.getContext().getDatabase().count("m_fuzzy_matches"));
    }

    @Test
    public void testSuppressSecondBest() {
        ProcessingResult pr = tc.processScript("m := MATCH un(name) ON c(name) WITH THRESHOLD(0.80), SUPPRESSSECONDBEST;");
        assertEquals("suppress2ndbest", "1", tc.getContext().getDatabase().prepare("SELECT COUNT(*) FROM m_fuzzy_matches WHERE un_name = 'Graciela Ruta'").get(0).getFirstValueContent());
    }
}
