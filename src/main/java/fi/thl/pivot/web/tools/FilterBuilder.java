package fi.thl.pivot.web.tools;

import java.util.Collection;
import java.util.List;

import org.apache.log4j.Logger;

import com.google.common.collect.Lists;

import fi.thl.pivot.datasource.HydraSource;
import fi.thl.pivot.model.Dimension;
import fi.thl.pivot.model.DimensionNode;

/**
 * <p>
 * Creates filters for querying a hydra source. The filter is built based on the
 * following rules
 * </p>
 * <ol type="A">
 * <li>If a dimension node is explicitely defined as a filter then it is used as
 * a filter</li>
 * <li>If a dimension is used as a row or a column header a filter is not
 * created unless A</li>
 * <li>If a dimension is not used as a row or a column header and not A then the
 * root level node is used as a filter</li>
 * </ol>
 * 
 * @author aleksiyrttiaho
 *
 */
public class FilterBuilder {

    private static final Logger LOG = Logger.getLogger(FilterBuilder.class);
    private List<DimensionNode> filter;
    private HydraSource source;
    private List<List<DimensionNode>> headerNodes;
    private List<DimensionNode> filterNodes;

    public FilterBuilder(HydraSource source,
            List<List<DimensionNode>> headerNodes,
            List<DimensionNode> filterNodes) {
        this.source = source;
        this.headerNodes = headerNodes;
        this.filterNodes = filterNodes;
        this.filter = Lists.newArrayList();
    }

    public List<DimensionNode> asFilter() {
        filterBasedOn(source.getDimensions());
        filterBasedOn(source.getMeasures());
        LOG.debug(String.format(
                "Filter created based on parameteters %s => %s", filterNodes,
                filter));
        return filter;
    }

    private void filterBasedOn(Collection<Dimension> dimensions) {
        for (Dimension d : dimensions) {
            if (!isDimensionUsedAsHeader(d)) {
                LOG.debug("Using dimension as filter: " + d.getId());
                fallbackToRootNode(d, assignFilterNode(d));
            } else {
                assignFilterNode(d);
            }
        }
    }

    private boolean isDimensionUsedAsHeader(Dimension d) {
        for (List<DimensionNode> level : headerNodes) {
            for (DimensionNode dn : level) {
                if (dn.getDimension().getId().equals(d.getId())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean assignFilterNode(Dimension d) {
        boolean filterAdded = false;
        for (DimensionNode fn : filterNodes) {
            if (fn.getDimension() == null) {
                // Nopcd .
            } else if (fn.getDimension().equals(d)) {
                filter.add(fn);
                filterAdded = true;
            }
        }
        return filterAdded;
    }

    private void fallbackToRootNode(Dimension d, boolean filterFound) {
        if (!filterFound) {
            filter.add(d.getRootNode());
        }
    }
}