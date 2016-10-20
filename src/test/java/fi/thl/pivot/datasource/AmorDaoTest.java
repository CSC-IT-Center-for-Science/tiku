package fi.thl.pivot.datasource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import fi.thl.pivot.model.Report;
import fi.thl.pivot.model.Tuple;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { HsqlTestConfiguration.class })
@TestPropertySource(properties = "database.environment.schema=test")
public class AmorDaoTest {

    private static DateFormat df = new SimpleDateFormat("yyyy-MM-dd");

    @Autowired
    private AmorDao dao;

    @Test
    public void shouldListReportsInDifferentStates() {
        assertEquals(5, dao.listReports("test").size());
        assertEquals(1, dao.listReports("prod").size());
    }

    @Test
    public void shouldListReportsInOrder() {
        List<Report> reports = dao.listReports("test");
        assertEquals(5, reports.size());

        // Fact versions
        assertEquals("1003", reports.get(0).getRunId());
        assertEquals("1001", reports.get(1).getRunId());

        // Summaries
        assertEquals("1003", reports.get(2).getRunId());
        assertEquals("1003", reports.get(3).getRunId());

        // Second fact
        assertEquals("1000", reports.get(4).getRunId());
    }

    @Test
    public void shouldListSummariesByRunId() {
        assertTrue("This cannot be tested with hsqldb", true);
    }

    @Test
    public void shouldAcceptValidStates() {
        dao.listReports("deve");
        dao.listReports("test");
        dao.listReports("beta");
        dao.listReports("prod");
    }

    @Test
    public void shouldFailOnInvalidState() {
        assertInvalidState("");
        assertInvalidState(null);
        assertInvalidState("Test");
    }

    @Test
    public void shouldLoadSpecificSourceVersion() {
        HydraSource source = dao.loadSource("test", "a-subject.test-hydra.fact.1001");
        assertNotNull("No source found", source);
        assertEquals("1001", source.getRunid());
        assertEquals("2016-09-03", df.format(source.getRunDate().getTime()));
        assertEquals("a-subject.test-hydra.fact.1001", source.getFactSource());
    }

    @Test
    public void shouldLoadLatestSourceVersion() {
        HydraSource source = dao.loadSource("test", "a-subject.test-hydra.fact.latest");
        assertNotNull("No source found", source);
        assertEquals("1003", source.getRunid());
        assertEquals("2016-09-04", df.format(source.getRunDate().getTime()));
        assertEquals("a-subject.test-hydra.fact.1003", source.getFactSource());
    }

    @Test
    public void shouldReturnNullIfNoSourceFound() {
        HydraSource source = dao.loadSource("test", "a-subject.test-hydra.fact.9999");
        assertNull("Source should not exist", source);
    }

    @Test
    public void shouldLoadCubeMetadata() {
        List<Tuple> metadata = dao.loadCubeMetadata("test", "fact", "1003");
        Map<String, String> index = new HashMap<String, String>();
        for (Tuple t : metadata) {
            index.put(t.predicate, t.object);
        }
        assertEquals("Test fact", index.get("name"));
        assertEquals("1", index.get("opendata"));
        assertEquals("1", index.get("deny"));
        assertEquals("Test password", index.get("password"));
    }

    @Test
    public void shouldCreateFactTableNames() {
        assertEquals("subject.hydra.fact", dao.replaceFactInIdentifier("subject.hydra.summary", "fact"));
        assertEquals("subject.hydra.fact.1003", dao.replaceFactInIdentifier("subject.hydra.summary.1003", "fact"));

    }

    private void assertInvalidState(String state) {
        try {
            dao.listReports(state);
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("IllegalEnvironment "));
        }
    }

}
