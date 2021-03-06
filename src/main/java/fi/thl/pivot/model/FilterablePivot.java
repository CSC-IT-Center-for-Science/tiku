package fi.thl.pivot.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import fi.thl.pivot.util.Functions;

public class FilterablePivot extends AbstractPivotForwarder {

    private static final Logger LOG = Logger.getLogger(FilterablePivot.class);

    private static interface HeaderCallback {
        DimensionNode getHeaderAt(int level, int index);
    }

    private List<PivotLevel> filteredRows = null;
    private List<PivotLevel> filteredColumns = null;

    private long totalTimeSpent;
    private List<Integer> rowIndices;
    private List<Integer> columnIndices;
    private List<PivotLevel> rows;
    private List<PivotLevel> columns;

    public FilterablePivot(Pivot delegate) {
        super(delegate);
        this.rowIndices = new ArrayList<>(Functions.upto(delegate.getRowCount()));
        this.columnIndices = new ArrayList<>(Functions.upto(delegate.getColumnCount()));
        this.rows = delegate.getRows();
        this.columns = delegate.getColumns();
    }

    @Override
    public boolean isFirstColumn(int column) {
        return isColumn(column, 0);
    }

    @Override
    public boolean isColumn(int column, int targetColumn) {
        return columnIndices.indexOf(column) == targetColumn;
    }

    @Override
    public int getColumnNumber(int column) {
        return delegate.getColumnNumber(columnIndices.indexOf(column));
    }

    @Override
    public PivotCell getCellAt(int row, int column) {
        return new PivotCellForwarder(super.getCellAt(rowIndices.get(row), columnIndices.get(column)), row, column);
    }

    @Override
    public DimensionNode getRowAt(int level, int row) {
        return super.getRowAt(level, rowIndices.get(row));
    }

    @Override
    public DimensionNode getColumnAt(int level, int column) {
        return super.getColumnAt(level, columnIndices.get(column));
    }

    @Override
    public int getRowCount() {
        return rowIndices.size();
    }

    @Override
    public int getColumnCount() {
        return columnIndices.size();
    }

    public void applyFilter(Predicate<PivotCell> filter) {
        Preconditions.checkNotNull(filter, "Applied filter must not be null");
        applyFilters(ImmutableList.of(filter));
    }

    public void applyFilters(List<Predicate<PivotCell>> filters) {
        LOG.debug("Applying filters " + filters + " table size [" + rowIndices.size() + ", " + columnIndices.size() + "]");
        if (filters.isEmpty()) {
            return;
        }
        // Initially all rows and columns are filtered
        // Rows and columns are only shown if exists one
        // or more cells in that column or row where
        // the cell is not filtered.
        //
        // Note that the method may be called more
        // than once
        Set<Integer> filteredRows = Functions.upto(getRowCount());
        Set<Integer> filteredColumns = Functions.upto(getColumnCount());

        // goes through the whole multidimensional table
        // and applies the filter for each cell
        applyFiltersForEachCell(filters, filteredRows, filteredColumns);
        updateFilteredHeaderCounts(filteredRows, filteredColumns);

        filteredRows = null;
        filteredColumns = null;
    }

    private void updateFilteredHeaderCounts(Set<Integer> filteredRows, Set<Integer> filteredColumns) {
        // Update row indices and row count to match the
        // number of shown rows af filteration
        // rowIndices.removeAll(filteredRows);
        List<Integer> r = new ArrayList<>(filteredRows);
        Collections.sort(r);
        Collections.reverse(r);
        for (int i : r) {
            int row = rowIndices.remove(i);
            for(int column = 0; column < delegate.getColumnCount(); ++column) {
                delegate.filterCellAt(row, column);
            }
        }

        // Update column indices and column count to match the
        // number of shown rows af filteration
        List<Integer> c = new ArrayList<>(filteredColumns);
        Collections.sort(c);
        Collections.reverse(c);
        for (int i : c) {
            int column = columnIndices.remove(i);
            for(int row = 0; row < delegate.getRowCount(); ++row) {
                delegate.filterCellAt(row, column);
            }
        }
    }

    @Override
    public List<PivotLevel> getColumns() {
        if (null == filteredColumns) {
            filteredColumns = Collections.unmodifiableList(filter(columns, getColumnCount(), new HeaderCallback() {
                @Override
                public DimensionNode getHeaderAt(int level, int index) {
                    return getColumnAt(level, index);
                }
            }));
        }
        return filteredColumns;
    }

    @Override
    public List<PivotLevel> getRows() {
        if (null == filteredRows) {
            filteredRows = Collections.unmodifiableList(filter(rows, getRowCount(), new HeaderCallback() {
                @Override
                public DimensionNode getHeaderAt(int level, int index) {
                    return getRowAt(level, index);
                }
            }));
        }
        return filteredRows;
    }

    private List<PivotLevel> filter(final List<PivotLevel> filterable, int max, HeaderCallback cb) {
        List<PivotLevel> filtered = Lists.newArrayList();
        for (int i = 0; i < filterable.size(); ++i) {
            filtered.add(new PivotLevel(filterable.get(i)));
            List<DimensionNode> retainable = Lists.newArrayList();
            for (int j = 0; j < max; ++j) {
                DimensionNode n = cb.getHeaderAt(i, j);
                retainable.add(n);
            }
            filtered.get(i).retainAll(retainable);
        }
        return filtered;
    }

    /**
     * Traverses the dataset and removes all rows and columns where filter
     * returns true for each cell in row or column. The filter collections lists
     * all row and column indices that should not be visible after filter has
     * been applied
     * 
     * @param filter
     *            predicate that returns true if cell should be filtered out
     * @param filteredRows
     *            hidden row indices
     * @param filteredColumns
     *            hidden column indices
     */
    private void applyFiltersForEachCell(List<Predicate<PivotCell>> filter, Set<Integer> filteredRows, Set<Integer> filteredColumns) {
        if (columnIndices.size() == 0) {
            applyFiltersForSingleDimensionCubes(filter, rowIndices.size(), true, filteredRows);
        } else if (rowIndices.size() == 0) {
            applyFiltersForSingleDimensionCubes(filter, columnIndices.size(), false, filteredColumns);
        } else {
            applyFiltersForAllCells(filter, filteredRows, filteredColumns);
        }
    }

    private void applyFiltersForAllCells(List<Predicate<PivotCell>> filters, Set<Integer> filteredRows, Set<Integer> filteredColumns) {
        long i = 0L;
        for (int column = 0; column < columnIndices.size(); ++column) {
            for (int row = 0; row < rowIndices.size(); ++row) {
                for (Predicate<PivotCell> filter : filters) {
                    PivotCell cell = getCellAt(row, column);
                    if (!filter.apply(cell)) {
                        filteredRows.remove(row);
                        filteredColumns.remove(column);
                        break;
                    }
                }
                if (++i % 100000 == 0) {
                    LOG.debug("Filter applied to " + i + " cells / " + (columnIndices.size() * rowIndices.size()));
                    LOG.debug(totalTimeSpent);
                }
            }
        }
    }

    private void applyFiltersForSingleDimensionCubes(List<Predicate<PivotCell>> filters, int max, boolean isRow, Set<Integer> nodes) {
        for (int row = 0; row < max; ++row) {
            PivotCellImpl cell = isRow ? createSentinelCell(row, 0) : createSentinelCell(0, row);
            for (Predicate<PivotCell> filter : filters) {
                if (!filter.apply(cell)) {
                    nodes.remove(row);
                    break;
                }
            }
        }
    }

    private PivotCellImpl createSentinelCell(int row, int column) {
        PivotCellImpl cell = new PivotCellImpl("..");
        cell.setRowNumber(row);
        cell.setColumnNumber(column);
        return cell;
    }

    public void filterHiearachy() {
        updateFilteredHeaderCounts(filterHieararchyInRows(), filterHieararchyInColumns());
        filteredRows = null;
        filteredColumns = null;
    }

    private Set<Integer> filterHieararchyInRows() {
        Set<Integer> newFilteredRows = Sets.newHashSet();
        List<PivotLevel> someRows = getRows();

        Multimap<Dimension, Integer> dims = determineDimensionInRow(someRows);
        Map<Dimension, Collection<Integer>> asMap = dims.asMap();
        if (asMap.size() != someRows.size()) {
            for (Integer i = 0; i < rowIndices.size(); ++i) {
                boolean filtered = determineIfRowShouldBeFiltered(asMap, i);
                if (filtered) {
                    newFilteredRows.add(rowIndices.get(i));
                }
            }
        }
        return newFilteredRows;
    }

    private Set<Integer> filterHieararchyInColumns() {
        Set<Integer> newFilteredColumns = Sets.newHashSet();
        List<PivotLevel> someColumns = getColumns();

        Multimap<Dimension, Integer> dims = determineDimensionInColumn(someColumns);
        Map<Dimension, Collection<Integer>> asMap = dims.asMap();
        if (asMap.size() != someColumns.size()) {
            for (Integer i = 0; i < columnIndices.size(); ++i) {
                boolean filtered = determineIfColumnShouldBeFiltered(asMap, i);
                if (filtered) {
                    newFilteredColumns.add(columnIndices.get(i));
                }
            }
        }
        return newFilteredColumns;
    }

    private boolean determineIfRowShouldBeFiltered(Map<Dimension, Collection<Integer>> asMap, int i) {
        for (Map.Entry<Dimension, Collection<Integer>> e : asMap.entrySet()) {
            List<Integer> l = new ArrayList<>(e.getValue());
            for (int a = 0; a < l.size() - 1; ++a) {
                for (int b = a + 1; b < l.size(); ++b) {
                    if (sameNodeUsedTwiceInRows(i, a, b)) {
                        return true;
                    }
                    if (invalidRowHiearachy(i, a, b)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean invalidRowHiearachy(int i, int a, int b) {
        return !getRowAt(a, i).ancestorOf(getRowAt(b, i)) && !getRowAt(b, i).ancestorOf(getRowAt(a, i));
    }

    private boolean sameNodeUsedTwiceInRows(int i, int a, int b) {
        return rows.get(a).getLastNode() == getRowAt(a, i) && rows.get(b).getLastNode() != (getRowAt(b, i));
    }

    private boolean determineIfColumnShouldBeFiltered(Map<Dimension, Collection<Integer>> asMap, int i) {
        for (Map.Entry<Dimension, Collection<Integer>> e : asMap.entrySet()) {
            List<Integer> l = new ArrayList<>(e.getValue());
            for (int a = 0; a < l.size() - 1; ++a) {
                for (int b = a + 1; b < l.size(); ++b) {
                    if (sameNodeUsedTwiceInColumns(i, a, b)) {
                        return true;
                    }
                    if (invalidColumnHiearachy(i, a, b)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    private boolean invalidColumnHiearachy(int i, int a, int b) {
        return !getColumnAt(a, i).ancestorOf(getColumnAt(b, i)) && !getColumnAt(b, i).ancestorOf(getColumnAt(a, i));
    }

    private boolean sameNodeUsedTwiceInColumns(int i, int a, int b) {
        return columns.get(a).getLastNode() == getColumnAt(a, i) && columns.get(b).getLastNode() != (getColumnAt(b, i));
    }


    private Multimap<Dimension, Integer> determineDimensionInRow(List<PivotLevel> rows) {
        Multimap<Dimension, Integer> dims = ArrayListMultimap.create();
        if (rowIndices.size() > 1) {
            for (int i = 0; i < rows.size(); ++i) {
                DimensionNode rowHeader = getRowAt(i, 0);
                dims.put(rowHeader.getDimension(), i);
            }
        }
        return dims;
    }

    private Multimap<Dimension, Integer> determineDimensionInColumn(List<PivotLevel> column) {
        Multimap<Dimension, Integer> dims = ArrayListMultimap.create();
        if (columnIndices.size() > 1) {
            for (int i = 0; i < column.size(); ++i) {
                DimensionNode columnHeader = getColumnAt(i, 0);
                dims.put(columnHeader.getDimension(), i);
            }
        }
        return dims;
    }

}
