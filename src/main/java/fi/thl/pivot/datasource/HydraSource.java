package fi.thl.pivot.datasource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.springframework.util.StopWatch;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import fi.thl.pivot.model.Dataset;
import fi.thl.pivot.model.Dimension;
import fi.thl.pivot.model.DimensionLevel;
import fi.thl.pivot.model.DimensionNode;
import fi.thl.pivot.model.Label;
import fi.thl.pivot.model.Query;
import fi.thl.pivot.model.Tuple;
import fi.thl.pivot.util.Constants;

/**
 * <p>
 * HydraSource is responsible for providing access to metadata and data
 * represented in the hydra 4.x format.
 * </p>
 * 
 * <p>
 * The class is abstract and the user is expected to implement methods for
 * accessing raw data. This class provides the common algorithms for assigning
 * relations and metadata attributes to dimension
 * </p>
 * 
 * @author aleksiyrttiaho
 *
 */
public abstract class HydraSource {

    private static final ImmutableList<String> GRAPH_PREDICATES = ImmutableList.of(Constants.CONFIDENCE_INTERVAL_LOWER_LIMIT,
            Constants.CONFIDENCE_INTERVAL_UPPER_LIMIT, Constants.SAMPLE_SIZE);
    private static final String PREDICATE_DENY_ACCESS_TO_CUBE = "deny";
    private static final String PREDICATE_VALUE_TRUE = "1";
    private static final String PREDICATE_RESERVED = "reserved";
    private static final int KEY_COLUMN_POSTFIX_LENGTH = 4;
    private static final String PREDICATE_IS = "is";
    private static final String PREDICATE_PASSWD = "password";
    private static final String PREDICATE_NAME = "name";
    private static final String PREDICATE_SORT = "sort";
    private static final String PREDICATE_CODE = "code";
    private static final String PREDICATE_DECIMALS = "decimals";

    /**
     * This callback handler is used when traversing the hydra metadata tree.
     * The implementer is expected to provide implementations that provide
     * values for each node. The rest of the tree travelsal is implemented by
     * the callback handler.
     * 
     * @author aleksiyrttiaho
     *
     */
    protected static abstract class TreeRowCallbackHandler {

        private static final Logger LOG = Logger.getLogger(TreeRowCallbackHandler.class);

        private final Map<String, Dimension> dimensions;
        private final Map<String, DimensionNode> nodes;
        private final Map<String, DimensionLevel> dimensionLevels;
        private final Map<String, DimensionNode> nodesByRef;
        private final Map<String, DimensionLevel> currentLevel;

        protected TreeRowCallbackHandler(Map<String, Dimension> dimensions, Map<String, DimensionNode> nodes, Map<String, DimensionNode> nodesByRef,
                Map<String, DimensionLevel> dimensionLevels) {
            this.dimensions = dimensions;
            this.nodes = nodes;
            this.nodesByRef = nodesByRef;
            this.dimensionLevels = dimensionLevels;
            this.currentLevel = Maps.newHashMap();
        }

        protected abstract String getDimension() throws Exception;

        protected abstract String getDimensionLevel() throws Exception;

        protected abstract String getMetaReference() throws Exception;

        protected abstract String getNodeId() throws Exception;

        protected abstract String getParentId() throws Exception;

        protected Integer getSurrogateId() throws Exception {
            return getNodeId().hashCode();
        }

        public void handleRow() throws Exception {
            String dimension = getDimension();
            String dimensionLevel = getDimensionLevel();

            createDimension(dimensions, dimension);
            createDimensionLevel(dimensionLevels, dimension, dimensionLevel);
            createNode(dimensionLevels, nodes, dimension, dimensionLevel);
        }

        private void createNode(final Map<String, DimensionLevel> dimensionLevels, final Map<String, DimensionNode> nodes, String dimension,
                String dimensionLevel) throws Exception {

            DimensionLevel nodeLevel = dimensionLevels.get(dimension + "-" + dimensionLevel);
            if (nodeLevel == null) {
                LOG.warn("No level found for node " + dimension + "-" + dimensionLevel);
                return;
            }
            DimensionNode node;
            if (isRootNode()) {
                LOG.debug("Found root node " + getMetaReference() + " for " + dimension);
                Preconditions.checkArgument(nodeLevel.getNodes().size() == 1);
                node = nodeLevel.getNodes().get(0);
                node.setId(getNodeId());
            } else {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Adding node " + getMetaReference() + " to " + dimension + "-" + dimensionLevel + ": " + nodeLevel);
                }
                node = nodeLevel.createNode(getNodeId(), new Label(), nodes.get(getParentId()));
            }
            node.setSurrogateId(getSurrogateId());
            nodes.put(getNodeId(), node);
            nodesByRef.put(getMetaReference(), node);
        }

        private boolean isRootNode() throws Exception {
            return null == getParentId();
        }

        private void createDimensionLevel(final Map<String, DimensionLevel> dimensionLevels, String dimension, String dimensionLevel) {
            if (dimensionLevel != null) {
                DimensionLevel prevLevel = currentLevel.get(dimension);
                if (null == prevLevel) {
                    LOG.warn("No prev level found for " + dimension);
                } else if (!dimensionLevel.equals(prevLevel.getId())) {
                    LOG.debug("Added new level " + dimension + "-" + dimensionLevel);
                    DimensionLevel newLevel = prevLevel.addLevel(dimensionLevel);
                    dimensionLevels.put(dimension + "-" + dimensionLevel, newLevel);
                    currentLevel.put(dimension, newLevel);
                }
            }
        }

        private void createDimension(final Map<String, Dimension> dimensions, String dimension) {
            if (!dimensions.containsKey(dimension)) {
                LOG.debug("Found new dimension " + dimension);
                dimensions.put(dimension, new Dimension(dimension, Label.create("fi", dimension), Label.create("fi", dimension)));

                dimensionLevels.put(dimension + "-root", dimensions.get(dimension).getRootLevel());
                currentLevel.put(dimension, dimensions.get(dimension).getRootLevel());
            }
        }
    }

    /**
     * Describes a single internationalized (subject, predicate, object literal)
     * n-tuple where the subject is implied by the owner of the property. Used
     * to hold metadata attributes .
     * 
     * @author aleksiyrttiaho
     *
     */
    protected final class Property {

        private final String predicate;
        private final String lang;
        private final String value;

        protected Property(String predicate, String language, String value) {
            this.predicate = predicate;
            this.lang = language;
            this.value = value;
        }
    }

    private static final Logger LOG = Logger.getLogger(HydraSource.class);

    private Map<String, Dimension> dimensions;
    private Dataset dataSet;
    private Map<String, DimensionNode> nodes;
    private List<String> columns;
    private Label name;
    private List<String> passwords = new ArrayList<>();
    private Set<String> languages = new TreeSet<>();
    private Map<Integer, DimensionNode> nodeIndex;
    private boolean isOpenData = true;
    private Date runDate;
    private String runid;
    private boolean denyCubeAccess;
    private String masterPassword;
    private Map<String, Label> predicates = new HashMap<>();

    protected HydraSource() {
    }

    public final boolean isMetadataLoaded() {
        return dimensions != null;
    }

    public final boolean isDataLoaded() {
        return dataSet != null;
    }

    public final boolean isOpenData() {
        return isOpenData;
    }

    public final Collection<Dimension> getDimensions() {
        return Collections2.filter(dimensions.values(), new Predicate<Dimension>() {
            public boolean apply(Dimension d) {
                return !d.isMeasure();
            }
        });
    }

    public final Collection<Dimension> getMeasures() {
        return Collections2.filter(dimensions.values(), new Predicate<Dimension>() {
            public boolean apply(Dimension d) {
                return d.isMeasure();
            }
        });
    }

    public final Dataset loadData() {
        if (isDataLoaded()) {
            return this.dataSet;
        }
        synchronized (this) {
            if (isDataLoaded()) {
                return this.dataSet;
            }
            this.dataSet = loadDataInner();
        }
        return this.dataSet;
    }

    /**
     * Attempts to resolve a node id in order of prerence
     * 
     * <ol>
     * <li>Surrogate id</li>
     * <li>Actual id</li>
     * </ol>
     * 
     * @param nodeId
     * @return
     */
    public DimensionNode resolve(String nodeId) {
        Preconditions.checkNotNull(nodeId, "Node id must not be null");
        Preconditions.checkArgument(nodeId.matches("^\\d+$"), "Node id must be numeric");
        DimensionNode node = nodeIndex.get(Integer.parseInt(nodeId));
        return null == node ? getNode(nodeId) : node;
    }

    public boolean isProtected() {
        return !passwords.isEmpty();
    }

    public boolean isProtectedWith(String password) {
        return passwords.contains(password);
    }

    public boolean isMasterPassword(String password) {
        return password.equals(masterPassword);
    }

    public final void loadMetadata() {

        final StopWatch watch = new StopWatch();

        final Map<String, Dimension> newDimensions = Maps.newLinkedHashMap();
        final Map<String, DimensionLevel> dimensionLevels = Maps.newHashMap();
        final Map<String, DimensionNode> newNodes = Maps.newHashMap();
        final Map<String, DimensionNode> nodesByRef = Maps.newHashMap();
        final Map<String, List<Property>> propertiesByRef = Maps.newHashMap();
        final List<String> newDimensionColumns = Lists.newArrayList();

        watch.start("load fact metadata");
        assignFactMetaData(loadFactMetadata());
        watch.stop();

        watch.start("load fact dimension metadata");
        loadFactDimensionMetadata(newDimensionColumns);
        LOG.debug("Fact metadata loaded, found dimensions " + newDimensionColumns);
        watch.stop();

        watch.start("load nodes");
        loadNodes(newDimensions, dimensionLevels, newNodes, nodesByRef);
        LOG.debug(String.format("Dimension tree read from %s found %d dimensions, %d levels and %d nodes", getTreeSource(), newDimensions.size(),
                dimensionLevels.size(), newNodes.size()));
        if (LOG.isTraceEnabled()) {
            LOG.trace("dimensions: " + newDimensions);
            LOG.trace("dimensionLevels: " + dimensionLevels);
        }
        watch.stop();

        watch.start("log nodes without metadata");
        logColumnsWithNoMetadata(newDimensions, newDimensionColumns);
        watch.stop();

        watch.start("drop unused dimensions");
        dropDimensionsNotUsedInTheCube(newDimensions, newDimensionColumns);
        watch.stop();

        watch.start("load metadata");
        loadMetadata(propertiesByRef);
        LOG.debug("Loaded " + propertiesByRef.size() + " metadata entries ");
        watch.stop();

        watch.start("assign metadata");
        assignMetadata(newDimensions, nodesByRef, propertiesByRef);
        watch.stop();

        watch.start("sort dimensions");
        sortDimensions(newDimensions);

        Map<String, Dimension> sorted = Maps.newLinkedHashMap();
        for (String key : newDimensionColumns) {
            String d = key.substring(0, key.length() - KEY_COLUMN_POSTFIX_LENGTH);
            sorted.put(d, newDimensions.get(d));
        }

        watch.stop();

        Map<Integer, DimensionNode> newNodeIndex = Maps.newHashMap();
        for (DimensionNode node : newNodes.values()) {
            newNodeIndex.put(node.getSurrogateId(), node);
        }

        this.dimensions = sorted;
        this.nodes = newNodes;
        this.columns = newDimensionColumns;
        this.nodeIndex = newNodeIndex;

        if (LOG.isDebugEnabled()) {
            LOG.debug("Cube name loaded: " + watch.prettyPrint());
        }

    }

    private void assignFactMetaData(List<Tuple> metadata) {
        for (Tuple t : metadata) {
            if (PREDICATE_PASSWD.equals(t.predicate)) {
                this.passwords.add(t.object);
                this.masterPassword = t.object;
            } else if (PREDICATE_NAME.equals(t.predicate)) {
                if (null == this.name) {
                    this.name = new Label();
                }
                this.name.setValue(t.lang, t.object);
                this.languages.add(t.lang);
            } else if (PREDICATE_RESERVED.equals(t.predicate) && PREDICATE_VALUE_TRUE.equals(t.object)) {
                this.isOpenData = false;
            } else if (PREDICATE_DENY_ACCESS_TO_CUBE.equals(t.predicate) && PREDICATE_VALUE_TRUE.equals(t.object)) {
                this.denyCubeAccess = true;
            } else {
                if (!this.predicates.containsKey(t.predicate)) {
                    this.predicates.put(t.predicate, new Label());
                }
                this.predicates.get(t.predicate).setValue(t.lang, t.object);
            }

        }
    }

    protected abstract List<Tuple> loadFactMetadata();

    public final Label getName() {
        return name;
    }

    public Map<String, Label> getPredicates() {
        return predicates;
    }

    /**
     * Loads internationalized version of the cubes name. This should be
     * implemented by the extending class but a dummy is provided none the less.
     * 
     * @return
     */
    protected Label loadCubeName() {
        return new Label();
    }

    private void sortDimensions(Map<String, Dimension> newDimensions) {
        for (Dimension d : newDimensions.values()) {
            sort(d.getRootLevel().getNodes().get(0));
        }
    }

    private void sort(DimensionNode node) {
        node.sortChildren(new Comparator<DimensionNode>() {
            @Override
            public int compare(DimensionNode o1, DimensionNode o2) {
                return o1.getSort().compareTo(o2.getSort());
            }
        });
        for (DimensionNode child : node.getChildren()) {
            sort(child);
        }
    }

    public final Collection<Dimension> getDimensionsAndMeasures() {
        return dimensions.values();
    }

    public final boolean isBasedOn(String cube) {
        return getFactSource().equals(cube);
    }

    public final DimensionNode getNode(String id) {
        Preconditions.checkNotNull(id, "Node id must not be null");
        return nodes.get(id);
    }

    protected final List<String> getColumns() {
        return columns;
    }

    /**
     * @return Should return the name of the tree file or table
     */
    protected abstract String getTreeSource();

    /**
     * 
     * @return Should return the name of the fact file or table
     */
    protected abstract String getFactSource();

    /**
     * 
     * @return Should load a complete dataset
     */
    protected abstract Dataset loadDataInner();

    /**
     * Should load a subset of data as defined by the criteria
     * 
     * @param query
     *            Desired pojection of the fact file or table
     * @param filter
     *            Filterin rules for the subset
     * @return A complete dataset where filter and projection are applied
     */
    public abstract Dataset loadSubset(Query query, List<DimensionNode> filter);

    public abstract Dataset loadSubset(Query queryNodes, List<DimensionNode> filter, boolean showValueTypes);

    /**
     * Should apply metadata to the given set of dimensions
     * 
     * @param newDimensionColumns
     *            dimensions for which metadata should be loaded
     */
    protected abstract void loadFactDimensionMetadata(final List<String> newDimensionColumns);

    /**
     * Should load nodes from the tree file or table
     * 
     * @param newDimensions
     *            Dimenions that should be loaded
     * @param dimensionLevels
     *            The dimension levels that should be loaded
     * @param newNodes
     *            Index of nodes by surrogate ids as an output parameter
     * @param nodesByRef
     *            Index of nodes by metadata reference as an output parameter
     */
    protected abstract void loadNodes(final Map<String, Dimension> newDimensions, final Map<String, DimensionLevel> dimensionLevels,
            final Map<String, DimensionNode> newNodes, final Map<String, DimensionNode> nodesByRef);

    /**
     * Should load metadata from the meta file or table
     * 
     * @param propertiesByRef
     *            Index of properties by metadata reference as an output
     *            parameter
     */
    protected abstract void loadMetadata(final Map<String, List<Property>> propertiesByRef);

    public void setRunDate(Date runDate) {
        this.runDate = runDate;
    }

    public Date getRunDate() {
        return runDate;
    }

    public Dimension getDimension(String dimension) {
        for (Dimension d : getDimensionsAndMeasures()) {
            if (d.getId().equals(dimension)) {
                return d;
            }
        }
        return null;
    }

    private void assignMetadata(final Map<String, Dimension> newDimensions, final Map<String, DimensionNode> nodesByRef,
            final Map<String, List<Property>> propertiesByRef) {
        for (Map.Entry<String, List<Property>> nodeMetadata : propertiesByRef.entrySet()) {
            if (nodesByRef.containsKey(nodeMetadata.getKey())) {
                assignMetadataToNode(nodesByRef, nodeMetadata);
            } else {
                assignMetadataToDimensionsAndLevels(newDimensions, nodeMetadata);
            }
        }
    }

    private void dropDimensionsNotUsedInTheCube(final Map<String, Dimension> newDimensions, final List<String> newDimensionColumns) {
        List<String> dimensionsNotInUse = Lists.newArrayList();
        for (String dimension : newDimensions.keySet()) {
            if (!newDimensionColumns.contains(dimension + "_key")) {
                dimensionsNotInUse.add(dimension);
                LOG.debug("Dropped dimension " + dimension + " as it is not present in the fact table " + getFactSource());
            }
        }
        for (String dimension : dimensionsNotInUse) {
            newDimensionColumns.remove(dimension);
        }
    }

    private void logColumnsWithNoMetadata(final Map<String, Dimension> newDimensions, final List<String> newDimensionColumns) {
        for (String column : newDimensionColumns) {
            if (!newDimensions.keySet().contains(column.replaceAll("_key", ""))) {
                LOG.warn("No metadata described for dimension " + column);
            }
        }
    }

    private void assignMetadataToDimensionsAndLevels(final Map<String, Dimension> dimensions, Map.Entry<String, List<Property>> nodeMetadata) {
        String[] is = findIsPredicate(nodeMetadata);
        if (predicateIsDefined(is)) {
            assignIsPredicate(dimensions, nodeMetadata, is);
        } else {
            warnInvalidIsPredicate(nodeMetadata, is);
        }
    }

    private String[] findIsPredicate(Map.Entry<String, List<Property>> nodeMetadata) {
        String[] is = null;
        for (Property p : nodeMetadata.getValue()) {
            if (PREDICATE_IS.equals(p.predicate)) {
                is = p.value.split("/");
                break;
            }
        }
        return is;
    }

    private boolean predicateIsDefined(String[] is) {
        return null != is && is.length > 0;
    }

    private void assignIsPredicate(final Map<String, Dimension> dimensions, Map.Entry<String, List<Property>> nodeMetadata, String[] is) {
        Dimension d = dimensions.get(is[0]);
        if (is.length == 1 && d != null) {
            assignDimensionMetadata(nodeMetadata, d);
        } else if (is.length == 2) {
            assignLevelMetadata(nodeMetadata, is, d);
        } else {
            LOG.warn("Invalid value for 'is' " + Arrays.asList(is));
        }
    }

    private void warnInvalidIsPredicate(Map.Entry<String, List<Property>> nodeMetadata, String[] is) {
        if (null == is) {
            LOG.warn("No node found for ref " + nodeMetadata.getKey() + " and no predicate 'is' found either");
        } else {
            LOG.warn("An empty value for is for ref " + nodeMetadata.getKey());
        }
    }

    private void assignLevelMetadata(Map.Entry<String, List<Property>> nodeMetadata, String[] is, Dimension d) {
        if (null == d) {
            LOG.warn("No dimension provided");
            return;
        }
        DimensionLevel level = d.getRootLevel();
        boolean levelFound = false;
        while (!levelFound && null != level) {
            if (level.getId().equals(is[1])) {
                for (Property p : nodeMetadata.getValue()) {
                    if (PREDICATE_NAME.equals(p.predicate)) {
                        level.getLabel().setValue(p.lang, p.value);
                    }
                }
                levelFound = true;
            }
            level = level.getChildLevel();
        }
        if (!levelFound) {
            LOG.warn("No level " + is[1] + " found for dimension " + is[0]);
        }
    }

    private void assignDimensionMetadata(Map.Entry<String, List<Property>> nodeMetadata, Dimension d) {
        for (Property p : nodeMetadata.getValue()) {
            if (PREDICATE_NAME.equals(p.predicate)) {
                d.getLabel().setValue(p.lang, p.value);
            }
        }
    }

    private void assignMetadataToNode(final Map<String, DimensionNode> nodesByRef, Map.Entry<String, List<Property>> nodeMetadata) {
        DimensionNode node = nodesByRef.get(nodeMetadata.getKey());
        if(node == null) {
            LOG.warn("No node found with key" + nodeMetadata.getKey());
            return;
        }
        node.setReference(nodeMetadata.getKey());
        for (Property p : nodeMetadata.getValue()) {
            if (PREDICATE_NAME.equals(p.predicate)) {
                node.getLabel().setValue(p.lang, p.value);
                // Root node and root level should have the same name
                if (node.isRootLevelNode()) {
                    node.getDimension().getRootLevel().getLabel().setValue(p.lang, p.value);
                } 
            } else if (PREDICATE_SORT.equals(p.predicate)) {
                if (p.value != null && p.value.matches("^\\d+$")) {
                    node.setSort(p.lang, Long.parseLong(p.value));
                } else {
                    LOG.warn("Illegal value '" + p.value +"' for sort predicate for node " + nodeMetadata.getKey());
                }
            } else if (PREDICATE_CODE.equals(p.predicate)) {
                node.setCode(p.value);
            } else if (PREDICATE_DECIMALS.equals(p.predicate)) {
                if (p.value != null && p.value.matches("^\\d+$")) {
                    node.setDecimals(Integer.parseInt(p.value));
                } else {
                    LOG.warn("Illegal value '" + p.value +"' for decimal predicate for node " + nodeMetadata.getKey());
                }
            } else if (PREDICATE_PASSWD.equals(p.predicate)) {
                node.setPassword(p.value);
                this.passwords.add(p.value);
            } else if (GRAPH_PREDICATES.contains(p.predicate)) {
                node.addEdge(p.predicate, nodesByRef.get(p.value));
            } else {
                node.setProperty(p.predicate, p.lang, p.value);
            }
        }
    }

    public void setRunId(String latestRunId) {
        this.runid = latestRunId;
    }

    public String getRunid() {
        return runid;
    }

    public Set<String> getLanguages() {
        return Collections.unmodifiableSet(languages);
    }

    public boolean isCubeAccessDenied() {
        return this.denyCubeAccess;
    }

    public DimensionNode findNodeByRef(String item) {
        for (Dimension d : dimensions.values()) {
            DimensionNode node = findNodeByRefInLevel(d.getRootLevel(), item);
            if (node != null) {
                return node;
            }
        }
        return null;
    }

    public DimensionNode findNodeByName(String item, String language) {
        for (Dimension d : dimensions.values()) {
            DimensionNode node = findNodeByNameInLevel(d.getRootLevel(), item, language);
            if (node != null) {
                return node;
            }
        }
        return null;
    }

    private DimensionNode findNodeByRefInLevel(DimensionLevel level, String item) {
        if (null == level) {
            return null;
        }
        for (DimensionNode n : level.getNodes()) {
            if (item.equals(n.getReference())) {
                return n;
            }
        }
        return findNodeByRefInLevel(level.getChildLevel(), item);
    }

    private DimensionNode findNodeByNameInLevel(DimensionLevel level, String item, String language) {
        if (null == level) {
            return null;
        }
        for (DimensionNode n : level.getNodes()) {
            if (item.equals(n.getLabel().getValue(language))) {
                return n;
            }
        }
        return findNodeByNameInLevel(level.getChildLevel(), item, language);
    }

}
