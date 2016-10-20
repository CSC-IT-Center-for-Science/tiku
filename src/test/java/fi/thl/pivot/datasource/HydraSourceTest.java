package fi.thl.pivot.datasource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import fi.thl.pivot.model.Dimension;
import fi.thl.pivot.model.DimensionNode;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { HsqlTestConfiguration.class })
@TestPropertySource(properties = "database.environment.schema=test")
public class HydraSourceTest {

    private static DateFormat df = new SimpleDateFormat("yyyy-MM-dd");

    @Autowired
    private AmorDao dao;

    private HydraSource source;

    @Before
    public void loadSource() {
        source = dao.loadSource("test", "a-subject.test-hydra.fact.1003");
        source.loadMetadata();
    }

    @Test
    public void shouldLoadSource() {
        assertNotNull("Source not loaded", source);
        assertTrue("Metadata not loaded", source.isMetadataLoaded());
    }

    @Test
    public void shouldLoadFactMeadata() {
        assertEquals(1, source.getLanguages().size());
        assertTrue(source.getLanguages().contains("fi"));
        assertEquals("Test fact", source.getName().getValue("fi"));
        assertTrue(source.isProtected());
        assertTrue(source.isCubeAccessDenied());
        assertTrue(source.isOpenData());
        assertTrue(source.isMasterPassword("Test password"));
    }

    @Test
    public void shouldLoadSchemaMetadata() {
        assertEquals(3, source.getColumns().size());
        assertTrue(source.getColumns().contains("time_key"));
        assertTrue(source.getColumns().contains("region_key"));
        assertTrue(source.getColumns().contains("measure_key"));
    }

    @Test
    public void shouldLoadDimensions() {
        assertEquals(2, source.getDimensions().size());
        assertEquals(3, source.getDimensionsAndMeasures().size());
    }

    @Test
    public void shouldLoadTimeDimension() {
        assertNotNull(source.getDimension("time"));
        Dimension time = source.getDimension("time");
        assertNotNull(time.getRootNode());
        assertEquals(time.getNode("https://sampo.thl.fi/meta/aika/"), time.getRootNode());
        assertEquals("5", time.getRootNode().getId());
        assertEquals("https://sampo.thl.fi/meta/aika/", time.getRootNode().getReference());
        assertEquals(5, time.getRootNode().getSurrogateId());
        assertEquals("Kaikki vuodet", time.getRootNode().getLabel().getValue("fi"));
        assertEquals(1, time.getRootNode().getChildren().size());
        assertEquals("https://sampo.thl.fi/meta/aika/vuosi/2016",
                ((DimensionNode) time.getRootNode().getChildren().toArray()[0]).getReference());

        assertEquals(1, time.getLevel("root").getNodes().size());
        assertEquals(1, time.getLevel("leaf").getNodes().size());
    }

    @Test
    public void shouldFindNodesByName() {
        DimensionNode node = source.findNodeByName("2016", "fi");
        assertNotNull(node);
        assertEquals("2016", node.getLabel().getValue("fi"));
        assertEquals("time", node.getDimension().getId());
    }

    @Test
    public void shouldFindNodesByRef() {
        DimensionNode node = source.findNodeByRef("https://sampo.thl.fi/meta/aika/vuosi/2016");
        assertNotNull(node);
        assertEquals("2016", node.getLabel().getValue("fi"));
        assertEquals("time", node.getDimension().getId());
    }

    @Test
    public void shouldSortNodesBySortPredicate() {
        List<DimensionNode> nodes = source.getDimension("region").getLevel("leaf").getNodes();

        assertEquals(3, nodes.size());
        assertEquals("Espoo", nodes.get(0).getLabel().getValue("fi"));
        assertEquals("Helsinki", nodes.get(1).getLabel().getValue("fi"));
        assertEquals("Vantaa", nodes.get(2).getLabel().getValue("fi"));

    }
    
    @Test
    public void shouldProtectNodes() {
        DimensionNode node = source.getDimension("measure").getRootNode();
        assertEquals(2, node.getDecimals());
        
        DimensionNode second = source.findNodeByRef("https://sampo.thl.fi/meta/test/mittari/2");
        assertEquals("decimals set even though illegal metadata value", 0, second.getDecimals());
    }
}
