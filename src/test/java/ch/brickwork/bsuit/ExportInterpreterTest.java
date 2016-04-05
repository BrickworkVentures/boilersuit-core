package ch.brickwork.bsuit;

import ch.brickwork.bsuit.interpreter.interpreters.ProcessingResult;
import ch.brickwork.bsuit.util.FileIOUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;

/**
 * Created by marcel on 12/18/15.
 */
public class ExportInterpreterTest {
    private static BoilerSuitTestContext tc = new BoilerSuitTestContext();

    @BeforeClass
    public static void prepareFile() {
        // write files from resources
        tc.writeToFile("us-500.csv", tc.getResource("us-500.csv"));
        tc.processScript("us := us-500.csv; us1 := us; us2 := us;");
    }

    @Before
    public void removeOutput() {
        tc.flush();
        FileIOUtils.deleteRuthlessly(new File("usexp.csv"));
    }

    @Test
    public void testSimpleCSVExport() {
        ProcessingResult pr = tc.processScript("us =: usexp.csv;");
        assertEquals(true, tc.noErrors());
        assertEquals(true, tc.getTestLog().isMentionedInWarnLog("replaced by"));
        String s = tc.readCompleteFile("usexp.csv");
        assertEquals(true, s.startsWith("\"first_name\", \"last_name\", \"company_name\", \"address\", \"city\", \"county\", \"state\", \"zip\", \"phone1\", \"phone2\", \"email\", \"web\""));
    }

    @Test
    public void testCSVQuoteExport() {
        ProcessingResult pr = tc.processScript("us =: usexp.csv WITH quote(\"'\");");
        assertEquals(true, tc.noErrors());
        assertEquals(true, tc.getTestLog().isMentionedInWarnLog("replaced by"));
        String s = tc.readCompleteFile("usexp.csv");
        assertEquals(true, s.startsWith("'first_name', 'last_name', 'company_name', 'address', 'city', 'county', 'state', 'zip', 'phone1', 'phone2', 'email', 'web'"));
    }

    @Test
    public void testCSVDelimAndQuoteWithReplacementExport() {
        tc.processScript("UPDATE us SET first_name='Jam' || CAST(X'09' AS TEXT) || 'es', last_name='O''Butt' WHERE first_name='James' AND last_name='Butt'");
        tc.processScript("us =: usexp.csv WITH quote(\"'\"), delim(\"\\t\"), replacequote(\"\\\\'\"), replacedelim(\"<tab>\");");
        assertEquals(true, tc.noErrors());
        assertEquals(true, tc.getTestLog().isMentionedInWarnLog("replaced by"));
        String s = tc.readCompleteFile("usexp.csv");
        assertEquals(true, s.startsWith("'first_name'\t'last_name'\t'company_name'\t'address'\t'city'\t'county'\t'state'\t'zip'\t'phone1'\t'phone2'\t'email'\t'web'\n'Jam<tab>es'\t'O\\'Butt'"));
    }

    @Test
    public void testExportAll() {
        tc.processScript("=:");
        assertEquals(6, FileIOUtils.getFiles(tc.getContext().getWorkingDirectory(), "*.csv").length);
    }

    @AfterClass
    public static void cleanUp() {
        tc.dispose();
    }
}
