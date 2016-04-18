package ch.brickwork.bsuit;

import ch.brickwork.bsuit.database.Record;
import ch.brickwork.bsuit.interpreter.interpreters.ProcessingResult;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Created by marcel on 24.10.15.
 */
public class BsfTest {
    private static BoilerSuitTestContext tc = new BoilerSuitTestContext();

    @BeforeClass
    public static void initTables() {
        tc.processScript(
                "+sucktest(!id, name);\n" +
                        "INSERT INTO sucktest VALUES(1, 'Müllér, Bob');\n" +
                        "INSERT INTO sucktest VALUES(2, 'Müller-Huber, Bob-Alice');\n" +
                        "INSERT INTO sucktest VALUES(3, 'Müller');\n" +
                        "INSERT INTO sucktest VALUES(4, 'Müller');\n" +
                        "INSERT INTO sucktest VALUES(5, 'Meier');\n" +
                        "INSERT INTO sucktest VALUES(6, 'Meier');\n" +
                        "INSERT INTO sucktest VALUES(7, 'Meier');\n"

        );

        tc.writeToFile("us-500.csv", tc.getResource("us-500.csv"));
        tc.processScript("u := us-500.csv;");
    }

    @Test
    public void testSuck() {
        ProcessingResult pr = tc.processScript("SUCKTEST2 := SUCKTEST(\n" +
                " \tID,\n" +
                "\tSUCK(NAME, [A-Za-zöäüéàèÖÄÜÉÀÈêçñí\\-]+, 1) AS NAME1,\n" +
                "\tSUCK(NAME, [A-Za-zöäüéàèÖÄÜÉÀÈêçñí\\-]+, 2) AS NAME2\n" +
                ");");
        List<Record> recs = tc.getContext().getDatabase().getAllRecordsFromTableOrView("SUCKTEST2", null, null);
        assertEquals("'Müllér, Bob'", "Müllér", recs.get(0).getValue("NAME1").getValue());
        assertEquals("'Müllér, Bob'", "Bob", recs.get(0).getValue("NAME2").getValue());
        assertEquals("'Müller-Huber, Bob-Alice'", "Müller-Huber", recs.get(1).getValue("NAME1").getValue());
        assertEquals("'Müller-Huber, Bob-Alice'", "Bob-Alice", recs.get(1).getValue("NAME2").getValue());
        assertEquals("Müller", "Müller", recs.get(2).getValue("NAME1").getValue());
        assertEquals("Müller", "", recs.get(2).getValue("NAME2").getValue());
        assertEquals("Müller", "Müller", recs.get(3).getValue("NAME1").getValue());
        assertEquals("Müller", "", recs.get(3).getValue("NAME2").getValue());
        assertEquals("Meier", "Meier", recs.get(4).getValue("NAME1").getValue());
        assertEquals("Meier", "", recs.get(4).getValue("NAME2").getValue());
        assertEquals("Meier", "Meier", recs.get(5).getValue("NAME1").getValue());
        assertEquals("Meier", "", recs.get(5).getValue("NAME2").getValue());
        assertEquals("Meier", "Meier", recs.get(6).getValue("NAME1").getValue());
        assertEquals("Meier", "", recs.get(6).getValue("NAME2").getValue());
    }

    @Test
    public void testMagicDate() {
        // write files from resources
        tc.writeToFile("customers.csv", tc.getResource("customers.csv"));

        // read in files
        tc.processScript("customers := customers.csv;");

        ProcessingResult pr = tc.processScript("c1 := customers(*, magicdate(regdate) AS x, magicdate(birthdate) AS y)");

        Record r = tc.getContext().getDatabase().prepare("SELECT x, y FROM c1 WHERE \"id\"='1'").get(0);
        assertEquals("17.07.2016", r.getValue("x").getValue());
        assertEquals("22.09.1884", r.getValue("y").getValue());
        System.out.println(tc.getTestLog().toString());

        r = tc.getContext().getDatabase().prepare("SELECT x, y FROM c1 WHERE \"id\"='2'").get(0);
        assertEquals("11.08.1978", r.getValue("x").getValue());
        assertEquals("((07/07/07))", r.getValue("y").getValue());
        assertEquals(true, tc.getTestLog().isMentionedInWarnLog("multiple parsers found for"));

        r = tc.getContext().getDatabase().prepare("SELECT x, y FROM c1 WHERE \"id\"='3'").get(0);
        assertEquals("((unparseable date))", r.getValue("x").getValue());
        assertEquals(true, tc.getTestLog().isMentionedInWarnLog("no parser found for"));
    }

    @Test
    public void testHash() {
        tc.processScript("uh := u(rowid AS id, hash(company_name + address) AS a, hash(county) AS b)");

        tc.processScript("ax := select count(*) from uh group by a having a like 'X%'");
        tc.processScript("ay := select count(*) from uh group by a having a like 'Y%'");
        tc.processScript("bx := select count(*) from uh group by b having b like 'X%'");
        tc.processScript("by := select count(*) from uh group by b having b like 'Y%'");

        assertEquals("unique a's starting with X", 247, tc.getContext().getDatabase().count("ax"));
        assertEquals("unique a's starting with Y", 253, tc.getContext().getDatabase().count("ay"));
        assertEquals("unique b's starting with X", 120, tc.getContext().getDatabase().count("bx"));
        assertEquals("unique b's starting with Y", 89, tc.getContext().getDatabase().count("by"));
    }

    @AfterClass
    public static void cleanUp() {
        tc.dispose();
    }
}
