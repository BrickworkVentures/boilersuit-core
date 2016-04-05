package ch.brickwork.bsuit;

import ch.brickwork.bsuit.database.Record;
import ch.brickwork.bsuit.interpreter.interpreters.ProcessingResult;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.assertEquals;

/**
 * Created by marcel on 3/17/16.
 */
public class TableExpressionInterpreterTest {
    private static BoilerSuitTestContext tc = new BoilerSuitTestContext();

    @BeforeClass
    public static void prepareFiles() {
        // write files from resources
        tc.writeToFile("customers.csv", tc.getResource("customers.csv"));

        // read in files
        tc.processScript("customers := customers.csv;");
    }

    @Test
    public void shortHandSelect() {
        ProcessingResult prNative = tc.processScript("x := SELECT id, email FROM customers WHERE email LIKE '%.co.uk'");
        ProcessingResult prShortHand = tc.processScript("customers(id, email{LIKE %co.uk} AS email);");
        assertEquals(true, sameValues(prNative, prShortHand));
    }

    private boolean sameValues(ProcessingResult p, ProcessingResult q) {
        if((p.getType().equals(ProcessingResult.ResultType.TABLE) || p.getType().equals(ProcessingResult.ResultType.VIEW))
                && (q.getType().equals(ProcessingResult.ResultType.TABLE) || q.getType().equals(ProcessingResult.ResultType.VIEW))) {
            List<Record> pRecs = tc.getContext().getDatabase().getAllRecordsFromTableOrView(p.getResultSummary(), null, null);
            List<Record> qRecs = tc.getContext().getDatabase().getAllRecordsFromTableOrView(q.getResultSummary(), null, null);
            if(pRecs.size() != qRecs.size()) {
                System.out.println("TEST: " + p.getResultSummary() + " has " + pRecs.size() + " items, but " + q.getResultSummary() + " only " + qRecs.size());
                return false;
            }
            else {
                for (int i = 0; i < pRecs.size(); i++) {
                    if (!pRecs.get(i).equals(qRecs.get(i))) {
                        System.out.println("TEST: " + pRecs.get(i) + " <> " + qRecs.get(i));
                        return false;
                    }
                }
            }
        }
        // unsupported types
        else {
            System.out.println("TEST: Unsupported Types " + p.getType() + ", " + q.getType() + "  in TableExpressionInterpreterTest.sameValues");
            return false;
        }

        return true;
    }

    @AfterClass
    public static void cleanUp() {
        tc.dispose();
    }
}
