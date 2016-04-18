package ch.brickwork.bsuit;

import ch.brickwork.bsuit.interpreter.interpreters.ProcessingResult;
import org.junit.AfterClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * Created by marcel on 12/16/15.
 */
public class TableModficationInterpreterTest {
    private static BoilerSuitTestContext tc = new BoilerSuitTestContext();

    @Test
    public void testCreate() {
        tc.processScript("+newtable(*pk, a, b, c);");
        ProcessingResult pr = tc.processScript("#newtable;");
        assertEquals("0", pr.getSingleValue());
        assertEquals(true, tc.db().existsTable("newtable"));
    }

    @Test
    public void testDelete() {
        int tablesAndViewsCount = tc.db().getTableNames().size() + tc.db().getViewNames().size();
        int variablesCount = tc.db().getVariables("%").size();
        ProcessingResult pr = tc.processScript("-newtable;");
        assertEquals(variablesCount - 1, tc.db().getVariables("%").size());
        pr = tc.processScript("#newtable;");
        assertEquals(null, pr.getSingleValue());
        assertEquals(false, tc.db().existsTable("newtable"));
        assertEquals(tablesAndViewsCount - 1, tc.db().getTableNames().size() + tc.db().getViewNames().size());
    }

    @Test
    public void testWildcardsDelete() {
        tc.processScript("+newtable(!pk, a, b, c);");
        tc.processScript("+newtabula(!pk, a, b, c);");
        tc.processScript("+newtebele(!pk, a, b, c);");
        tc.processScript("+x(!pk, a, b, c);");
        tc.processScript("+ta(!pk, a, b, c);");
        ProcessingResult pr = tc.processScript("-*ta*;");
        assertEquals(false, tc.db().existsTable("newtable"));
        assertEquals(false, tc.db().existsTable("newtabula"));
        assertEquals(false, tc.db().existsTable("ta"));
        assertEquals(true, tc.db().existsTable("newtebele"));
        assertEquals(true, tc.db().existsTable("x"));

        // ...don't delete reserved keywords like variables, warnings
        pr = tc.processScript("-*ar*;");
        assertEquals(true, tc.db().existsTable("variables"));
        assertEquals(true, tc.db().existsTable("warnings"));
    }

    @AfterClass
    public static void cleanUp() {
        tc.dispose();
    }
}
