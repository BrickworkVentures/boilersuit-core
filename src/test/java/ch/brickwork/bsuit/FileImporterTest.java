package ch.brickwork.bsuit;

import ch.brickwork.bsuit.database.Record;
import ch.brickwork.bsuit.interpreter.interpreters.ProcessingResult;
import ch.brickwork.bsuit.util.FileIOUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import java.io.File;
import java.util.List;
import static org.junit.Assert.assertEquals;

/**
 * Created by marcel on 29.07.15.
 */
public class FileImporterTest {
    private static BoilerSuitTestContext tc = new BoilerSuitTestContext();

    @BeforeClass
    public static void prepareFiles() {
        File f1 = tc.createFile("test1.csv");
        FileIOUtils.overwriteFile(f1.getAbsolutePath(),
                "BPARTNER_ID,ABC_PARTNER_ID,ABC_PARTNER_TITLE_CODE,ABC_PARTNER_TITLE_NAME,ABC_NAME_1_LAST_NAME,ABC_NAME_1_LAST_NAME_NORM,ABC_NAME_2_FIRST_NAME,ABC_NAME_3_MIDDLE_NAME,PARTNER_NAME,FULL_NAME,FULL_NAME_NORM,ABC_NAME_4_NICK_NAME,BUSINESS_KURZUAGE_ISO_CODE,BUSINESS_KURZUAGE_NAME,NATIVE_KURZUAGE_ISO_CODE,NATIVE_KURZUAGE_NAME,ABC_PARTNER_TYPE_CODE,ABC_PARTNER_TYPE_NAME,ABC_CREATED_DATE,ABC_CREATED_BY,ABC_UPDATED_DATE,ABC_UPDATED_BY,CRM_MODIFIED_ON,CRM_MODIFIED_BY,MAIN_INDUSTRY_SEGMENT_CODE,MAIN_INDUSTRY_SEGMENT,MAIN_INDUSTRY_SEGMENT_DESC,SUPER_CARE_TEAM_CODE,SUPER_CARE_TEAM,ABC_DATA_QUALITY_STATUS,ABC_DATA_QUALITY_VALUE,LIQUIDATION_DATE,STATUS_CODE,STATUS_NAME,FINANCIAL_STATUS_CODE,FINANCIAL_STATUS_NAME,LEGAL_ENTITY_FLAG,ACADEMIC_DEGREE_CODE,ACADEMIC_DEGREE_NAME,GOODNESS,LOCAL_SPELLING,USER_ID,GLOBAL_SEGMENT_CATEGORY_CODE,GLOBAL_SEGMENT_CATEGORY_NAME,CE_CLIENT,DIVISON,PROFESSION,MILITARY_TITLE_CODE,MILITARY_TITLE_NAME,UPID,COFFEE_FLAG,DUPLICATE_FLAG,RESTRICTED_USE_ENTITY_FLAG,ABC_INTERFACE_UPDATE_TIMESTAMP,CLIENT_BEAUTY_FACTOR_STAT_DESC,CLIENT_BEAUTY_FACTOR_STAT_CODE,FLUX_COMPENSATOR_INDICATOR,PAYMENT_TERM_CODE,PAYMENT_TERM,OVERALL_CATEGORY_CODE,OVERALL_CATEGORY_NAME\n" +
                        "115351,7082,,,MyCompany Semiconductors Africa Ltd,MY COMPANY SEMICONDUCTORS AFRICA LTD,\", Harare Branch\",,\"MyCompany Semiconductors Africa Ltd, Harare Branch\",\"MyCompany Semiconductors Africa Ltd, Harare Branch\",\"MY COMPANY SEMICONDUCTORS AFRICA LTD, HARARE BRANCH\",,,,,,2,Organization,26/04/2013 19:53:54.000000,PI,04/09/2014 14:35:03.000000,PI,20130715215725,ABCDXY,,,,,,,,,CODE123,unused,1,unknown,FALSE,,,FALSE,,,2,SpecialCategory,FALSE,,,,,,FALSE,FALSE,FALSE,24/05/2015 11:30:15.000000,,,FALSE,,,,");
        File f2 = tc.createFile("test2.csv");
        FileIOUtils.overwriteFile(f2.getAbsolutePath(),
                "ID,PARTNER_ID,ABC_PARTNER_TITLE_CODE,ABC_PARTNER_TITLE_NAME,ABC_NAME_1_LAST_NAME,ABC_NAME_1_LAST_NAME_NORM,ABC_NAME_2_FIRST_NAME,ABC_NAME_3_MIDDLE_NAME,PARTNER_NAME,FULL_NAME,FULL_NAME_NORM,ABC_NAME_4_NICK_NAME,BUSINESS_KURZUAGE_ISO_CODE,BUSINESS_KURZUAGE_NAME,NATIVE_KURZUAGE_ISO_CODE,NATIVE_KURZUAGE_NAME,ABC_PARTNER_TYPE_CODE,ABC_PARTNER_TYPE_NAME,ABC_CREATED_DATE,ABC_CREATED_BY,ABC_UPDATED_DATE,ABC_UPDATED_BY,CRM_MODIFIED_ON,CRM_MODIFIED_BY,MAIN_INDUSTRY_SEGMENT_CODE,MAIN_INDUSTRY_SEGMENT,MAIN_INDUSTRY_SEGMENT_DESC,SUPER_CARE_TEAM_CODE,SUPER_CARE_TEAM,ABC_DATA_QUALITY_STATUS,ABC_DATA_QUALITY_VALUE,LIQUIDATION_DATE,STATUS_CODE,STATUS_NAME,FINANCIAL_STATUS_CODE,FINANCIAL_STATUS_NAME,LEGAL_ENTITY_FLAG,ACADEMIC_DEGREE_CODE,ACADEMIC_DEGREE_NAME,GOODNESS,LOCAL_SPELLING,USER_ID,GLOBAL_SEGMENT_CATEGORY_CODE,GLOBAL_SEGMENT_CATEGORY_NAME,CE_CLIENT,DIVISON,PROFESSION,MILITARY_TITLE_CODE,MILITARY_TITLE_NAME,UPID,COFFEE_FLAG,DUPLICATE_FLAG,RESTRICTED_USE_ENTITY_FLAG,ABC_INTERFACE_UPDATE_TIMESTAMP,CLIENT_BEAUTY_FACTOR_STAT_DESC,CLIENT_BEAUTY_FACTOR_STAT_CODE,FLUX_COMPENSATOR_INDICATOR,PAYMENT_TERM_CODE,PAYMENT_TERM,OVERALL_CATEGORY_CODE,OVERALL_CATEGORY_NAME\n" +
                        "1969474,293991,,,,,,,\"Rest. Kunsteisbahn \"\"Icebreaker\"\" KURZ\",\"Rest. Kunsteisbahn \"\"Icebreaker\"\" KURZ\",\"REST. KUNSTEISBAHN \"\"ICEBREAKER\"\" KURZ\",,,,,,2,Organization,21/06/2015 01:04:52.000000,PI,21/06/2015 01:04:52.000000,PI,2014-11-11,PI,1003930,MyIndustry,,XX_YA_22,Ice Production Team 77,,,,ABC222,Active,1,solvent,FALSE,,,TRUE,\"Rest. Kunsteisbahn \"\"Icebreaker\"\" KURZ\",,99,Not rated,,,,,,,FALSE,FALSE,,25/06/2015 12:50:43.000000,,,FALSE,X12,30 days net,,\n");
        File f3 = tc.createFile("test3.csv");
        FileIOUtils.overwriteFile(f3.getAbsolutePath(), "ID,ABC_CLIENT_ID,ABC_CLIENT_TITLE_CODE,ABC_CLIENT_TITLE_NAME,ABC_NAME_1_LAST_NAME,ABC_NAME_1_LAST_NAME_NORM,ABC_NAME_2_FIRST_NAME,ABC_NAME_3_MIDDLE_NAME,CLIENT_NAME,FULL_NAME,FULL_NAME_NORM,ABC_NAME_4_NICK_NAME,BUSINESS_KURZUAGE_ISO_CODE,BUSINESS_KURZUAGE_NAME,NATIVE_KURZUAGE_ISO_CODE,NATIVE_KURZUAGE_NAME,ABC_CLIENT_TYPE_CODE,ABC_CLIENT_TYPE_NAME,ABC_CREATED_DATE,ABC_CREATED_BY,ABC_UPDATED_DATE,ABC_UPDATED_BY,CRM_MODIFIED_ON,CRM_MODIFIED_BY,MAIN_INDUSTRY_SEGMENT_CODE,MAIN_INDUSTRY_SEGMENT,MAIN_INDUSTRY_SEGMENT_DESC,CARE_TEAM_CODE,CARE_TEAM,ABC_DATA_QUALITY_STATUS,ABC_DATA_QUALITY_VALUE,LIQUIDATION_DATE,STATUS_CODE,STATUS_NAME,FINANCIAL_STATUS_CODE,FINANCIAL_STATUS_NAME,LEGAL_ENTITY_FLAG,ACADEMIC_DEGREE_CODE,ACADEMIC_DEGREE_NAME,GOODNESS,LOCAL_SPELLING,USER_ID,SEGMENT_CATEGORY_CODE,SEGMENT_CATEGORY_NAME,CE_CLIENT,DIVISON,PROFESSION,MILITARY_TITLE_CODE,MILITARY_TITLE_NAME,UPID,COFFEE_FLAG,DUPLICATE_FLAG,RESTRICTED_USE_ENTITY_FLAG,ABC_INTERFACE_UPDATE_TIMESTAMP,CLIENT_BEAUTY_FACTOR_STAT_DESC,CLIENT_BEAUTY_FACTOR_STAT_CODE,BASE_CLIENT_INDICATOR,PAYMENT_TERM_CODE,PAYMENT_TERM,SEGMENT_SUB_CATEGORY_CODE,SEGMENT_SUB_CATEGORY_NAME\n" +
                "1101376,24893,1,Ms.,Magnokova,MAGNOKOVA,Ana,,,\"Magnokova, Ana \"\"the best\"\"\",\"MAGNOKOVA, EVA\",Eva,1006866,English,1006866,English,1,Person,30/04/2013 10:06:09.000000,PI,05/09/2014 13:30:14.000000,PI,20140305235129,A_CODE,,,,,,,,,ABCC010,active,,,FALSE,,,FALSE,,123456,99,Not rated,FALSE,\"Europe, Middle East & Africa\",,,,,FALSE,FALSE,FALSE,23/05/2015 18:42:00.000000,,,FALSE,,,,");

        File f4 = tc.createFile("another3.csv");
        FileIOUtils.overwriteFile(f4.getAbsolutePath(), "ID,ABC_CLIENT_ID,ABC_CLIENT_TITLE_CODE,ABC_CLIENT_TITLE_NAME,ABC_NAME_1_LAST_NAME,ABC_NAME_1_LAST_NAME_NORM,ABC_NAME_2_FIRST_NAME,ABC_NAME_3_MIDDLE_NAME,CLIENT_NAME,FULL_NAME,FULL_NAME_NORM,ABC_NAME_4_NICK_NAME,BUSINESS_KURZUAGE_ISO_CODE,BUSINESS_KURZUAGE_NAME,NATIVE_KURZUAGE_ISO_CODE,NATIVE_KURZUAGE_NAME,ABC_CLIENT_TYPE_CODE,ABC_CLIENT_TYPE_NAME,ABC_CREATED_DATE,ABC_CREATED_BY,ABC_UPDATED_DATE,ABC_UPDATED_BY,CRM_MODIFIED_ON,CRM_MODIFIED_BY,MAIN_INDUSTRY_SEGMENT_CODE,MAIN_INDUSTRY_SEGMENT,MAIN_INDUSTRY_SEGMENT_DESC,CARE_TEAM_CODE,CARE_TEAM,ABC_DATA_QUALITY_STATUS,ABC_DATA_QUALITY_VALUE,LIQUIDATION_DATE,STATUS_CODE,STATUS_NAME,FINANCIAL_STATUS_CODE,FINANCIAL_STATUS_NAME,LEGAL_ENTITY_FLAG,ACADEMIC_DEGREE_CODE,ACADEMIC_DEGREE_NAME,GOODNESS,LOCAL_SPELLING,USER_ID,SEGMENT_CATEGORY_CODE,SEGMENT_CATEGORY_NAME,CE_CLIENT,DIVISON,PROFESSION,MILITARY_TITLE_CODE,MILITARY_TITLE_NAME,UPID,COFFEE_FLAG,DUPLICATE_FLAG,RESTRICTED_USE_ENTITY_FLAG,ABC_INTERFACE_UPDATE_TIMESTAMP,CLIENT_BEAUTY_FACTOR_STAT_DESC,CLIENT_BEAUTY_FACTOR_STAT_CODE,BASE_CLIENT_INDICATOR,PAYMENT_TERM_CODE,PAYMENT_TERM,SEGMENT_SUB_CATEGORY_CODE,SEGMENT_SUB_CATEGORY_NAME\n" +
                "1101376,24893,1,Ms.,Magnokova,MAGNOKOVA,Ana,,,\"Magnokova, Ana \"\"the best\"\"\",\"MAGNOKOVA, EVA\",Eva,1006866,English,1006866,English,1,Person,30/04/2013 10:06:09.000000,PI,05/09/2014 13:30:14.000000,PI,20140305235129,A_CODE,,,,,,,,,ABCC010,active,,,FALSE,,,FALSE,,123456,99,Not rated,FALSE,\"Europe, Middle East & Africa\",,,,,FALSE,FALSE,FALSE,23/05/2015 18:42:00.000000,,,FALSE,,,,");

        File f5 = tc.createFile("test4.csv");
        FileIOUtils.overwriteFile(f5.getAbsolutePath(), "\"col1\", \"col2\", \"col3\", \"col4\"\n" +
                "\"val11\", \"val12\", \"val13\", \"val14\"\n" +
                "\"val21\", \"val22\", \"val23\", \"val24\nwhich is\ntruncated\n" +
                "until here\"\n" +
                "\"val31\", \"val32\", \"val33\",\n" +
                "\"val41\",,,\"val44\"\n" +
                "\"val51\",,,\"val54\""
        );

        File f6 = tc.createFile("test5.csv");
        FileIOUtils.overwriteFile(f6.getAbsolutePath(), "strange:character-col(1),strange:character/col(2)\nval1, val2");

        File f7 = tc.createFile("test7.csv");
        FileIOUtils.overwriteFile(f7.getAbsolutePath(), "exid;code;somedate;anotherdate\n"
            + "1635115544;abc;2016-03-01 00:00:00;2016-03-07 00:00:00\n" + "1635115544;cde;2016-03-29 00:00:00;2016-04-09 00:00:00\n"
            + "1635115544;abc;2016-05-06 00:00:00;2016-05-16 00:00:00\n" + "1635115544;efg;2016-07-18 00:00:00;2016-07-25 00:00:00");

/*
        File t = tc.createFile("temp.csv");
        FileIOUtils.overwriteFile(t.getAbsolutePath(), "");
*/
        tc.writeToFile("us-500.csv", tc.getResource("us-500.csv"));
    }

    @AfterClass
    public static void cleanUp() {
        tc.dispose();
    }

    @Before
    public void flush() {
        tc.flush();
    }

    @Test
    public void test1() {
        ProcessingResult pr = tc.processScript("test1 := test1.csv;");

        // warnings
        assertEquals(tc.getTestLog().getErrLog().size(), 0);

        // values
        List<Record> records = tc.db().getAllRecordsFromTableOrView("test1", null, null);
        assertEquals("115351", records.get(0).getValue("BPARTNER_ID").getValue().toString());
        assertEquals("7082", records.get(0).getValue("ABC_PARTNER_ID").getValue().toString());
        assertEquals("", records.get(0).getValue("ABC_PARTNER_TITLE_CODE").getValue().toString());
        assertEquals("", records.get(0).getValue("ABC_PARTNER_TITLE_NAME").getValue().toString());
        assertEquals("MyCompany Semiconductors Africa Ltd", records.get(0).getValue("ABC_NAME_1_LAST_NAME").getValue().toString());
        assertEquals("MY COMPANY SEMICONDUCTORS AFRICA LTD", records.get(0).getValue("ABC_NAME_1_LAST_NAME_NORM").getValue().toString());
        assertEquals(", Harare Branch", records.get(0).getValue("ABC_NAME_2_FIRST_NAME").getValue().toString());
        assertEquals("", records.get(0).getValue("ABC_NAME_3_MIDDLE_NAME").getValue().toString());
        assertEquals("MyCompany Semiconductors Africa Ltd, Harare Branch", records.get(0).getValue("PARTNER_NAME").getValue().toString());
        assertEquals("MyCompany Semiconductors Africa Ltd, Harare Branch", records.get(0).getValue("FULL_NAME").getValue().toString());
        assertEquals("MY COMPANY SEMICONDUCTORS AFRICA LTD, HARARE BRANCH", records.get(0).getValue("FULL_NAME_NORM").getValue().toString());
        assertEquals("", records.get(0).getValue("OVERALL_CATEGORY_NAME").getValue().toString());
        assertEquals("", records.get(0).getValue("OVERALL_CATEGORY_CODE").getValue().toString());
        assertEquals("", records.get(0).getValue("PAYMENT_TERM").getValue().toString());
        assertEquals("", records.get(0).getValue("PAYMENT_TERM_CODE").getValue().toString());
        assertEquals("FALSE", records.get(0).getValue("FLUX_COMPENSATOR_INDICATOR").getValue().toString());
    }

    @Test
    public void test2() {
        ProcessingResult pr = tc.processScript("test2 := test2.csv;");

        assertEquals("partnername", "Rest. Kunsteisbahn \"Icebreaker\" KURZ", tc.getContext().getDatabase().getAllRecordsFromTableOrView("test2", null, null).get(0).getValue("PARTNER_NAME").getValue());

        // warnings
        assertEquals(tc.getTestLog().getErrLog().size(), 0);
    }

    @Test
    public void wildCardAsterixTest() {
        ProcessingResult pr = tc.processScript(":= test*.csv;");
        tc.getTestLog().log(pr.getResultSummary());
        assertEquals("wa1", "1", tc.processScript("#test1_csv").getSingleValue());
        assertEquals("wa2", "1", tc.processScript("#test2_csv").getSingleValue());
        assertEquals("wa3", "1", tc.processScript("#test3_csv").getSingleValue());

        flush();
        pr = tc.processScript("-test1_csv;-test2_csv;-test3_csv;");
        pr = tc.processScript(":= *.csv");
        assertEquals("wa4", "1", tc.processScript("#test1_csv").getSingleValue());
        assertEquals("wa5", "1", tc.processScript("#test2_csv").getSingleValue());
        assertEquals("wa6", "1", tc.processScript("#test3_csv").getSingleValue());

        flush();
        pr = tc.processScript("-test1_csv");
        pr = tc.processScript(":= test1.csv");
        assertEquals("1", tc.processScript("#test1_csv").getSingleValue());
    }

    @Test
    public void utfTest() {
        tc.flush();

        // write files from resources
        tc.writeToFile("utf8test.csv", tc.getResource("utf8test.csv"));

        // read in files
        ProcessingResult pr = tc.processScript("utf8test := utf8test.csv;");

        // check
        assertEquals(true, tc.getTestLog().isMentionedInInfoLog("Detected UTF-8"));
        assertEquals(2, tc.db().count("utf8test"));
    }

    @Test
    public void emptyLineTest() {
        tc.flush();

        // write files from resources
        tc.writeToFile("emptyLine.csv", tc.getResource("utf8test.csv") + "\n ");

        // read in files
        ProcessingResult pr = tc.processScript("emptyLine := emptyLine.csv;");

        // check
        assertEquals("warn", true, tc.getTestLog().isMentionedInWarnLog("Ignored empty line at 3"));
        assertEquals("count", 2, tc.db().count("emptyLine"));
    }

    @Test
    public void lineBreakTest() {
        tc.flush();

        // read in files
        ProcessingResult pr = tc.processScript("test4 := test4.csv;");

        List<Record> r = tc.getContext().getDatabase().getAllRecordsFromTableOrView("test4", null, null);
        assertEquals("1", "Record: col1: val11, col2: val12, col3: val13, col4: val14", r.get(0).toString());
        assertEquals("2", "Record: col1: val21, col2: val22, col3: val23, col4: val24\nwhich is\ntruncated\nuntil here", r.get(1).toString());
        assertEquals("3", "Record: col1: val31, col2: val32, col3: val33, col4: ", r.get(2).toString());
    }

    @Test
    public void us500Test() {
        tc.flush();
        ProcessingResult pr = tc.processScript("u := us-500.csv");
        assertEquals("count", 500, tc.getContext().getDatabase().count("u"));
        assertEquals("6th rec", "Record: first_name: Mitsue, last_name: Tollner, company_name: Morlong Associates, address: 7 Eads St, city: Chicago, county: Cook, state: IL, zip: 60632, phone1: 773-573-6914, phone2: 773-924-8565, email: mitsue_tollner@yahoo.com, web: http://www.morlongassociates.com",
                tc.getContext().getDatabase().getAllRecordsFromTableOrView("u", null, null).get(6).toString());
    }

    @Test
    public void sanitizeColumnNamesTest() {
        tc.flush();
        ProcessingResult pr = tc.processScript("t := test5.csv");
        assertEquals("strange_character_col_1", tc.getContext().getDatabase().getTableOrViewColumnNames("t").get(0));
    }

    @Test
    public void withDelimTest() {
        tc.flush();
        ProcessingResult pr = tc.processScript("t := test7.csv WITH delim(\";\")");
        assertEquals(4, tc.db().count("t"));
    }

    //@Test
    public void temp() {
        tc.flush();

        // read in files
        ProcessingResult pr = tc.processScript("t := temp.csv");
    }
}
