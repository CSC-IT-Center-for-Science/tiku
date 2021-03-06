package fi.thl.pivot.datasource;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.google.common.hash.Hashing;

import fi.thl.pivot.model.DimensionNode;
import fi.thl.pivot.web.CubeService;

@Component
public class LogSource {

    private static final Logger LOG = Logger.getLogger(LogSource.class);
    private static final String USAGE_TEMPLATE = "insert into amor_%s.user_log(log_id,subject, hydra, fact, run_id, host, ip_addr, session_id, \"view\", filter_zero, filter_empty) values (?, ?, ?, ?,  ?, ? ,?,  ?, ?, ?, ?)";
    private static final String SELECTION_TEMPLATE = "insert into amor_%s.user_log_selection(log_id, dimension, node, usage) values (?, ?, ?, ?)";

    private final class SelectionBatchSetter implements BatchPreparedStatementSetter {
        private final String id;
        private final List<List<DimensionNode>> nodes;
        private final String usage;

        private SelectionBatchSetter(String id, List<List<DimensionNode>> nodes, String usage) {
            this.id = id;
            this.nodes = nodes;
            this.usage = usage;
        }

        @Override
        public void setValues(PreparedStatement ps, int index) throws SQLException {
            ps.setString(1, id);
            ps.setString(4, usage);

            int i = 0;
            for (List<DimensionNode> level : nodes) {

                if (index < i + level.size()) {
                    DimensionNode dn = level.get(index - i);
                    ps.setString(2, dn.getDimension().getId());
                    ps.setString(3, dn.getId());
                    break;
                }
                i += level.size();
            }
        }

        @Override
        public int getBatchSize() {
            int i = 0;
            for (List<DimensionNode> level : nodes) {
                i += level.size();
            }
            return i;
        }
    }

    private JdbcTemplate jdbcTemplate;

    @Autowired
    public LogSource(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public void logDisplayEvent(String cube, String env, final CubeService cs, String view) {
        try {
            final String id = Hashing.md5().hashBytes((cube + System.currentTimeMillis()).getBytes()).toString();
            HttpServletRequest req = ((ServletRequestAttributes) (RequestContextHolder.currentRequestAttributes())).getRequest();
            String c[] = cube.split("\\.");
            jdbcTemplate.update(String.format(USAGE_TEMPLATE, env), id, c[0], c[1], c[2], c.length > 4 ? c[3] : "latest", req.getLocalAddr(), req.getRemoteAddr(),
                    req.getSession().getId(), view, cs.isZeroValuesFiltered() ? "t" : "f", cs.isEmptyValuesFiltered() ? "t" : "f");

            logSelectedValues(env, cs, id);
        } catch (Exception e) {
            LOG.warn("Could not log event " + e.getMessage());
        }
    }

    @Transactional
    private void logSelectedValues(String env, final CubeService cs, final String id) {
        jdbcTemplate.batchUpdate(String.format(SELECTION_TEMPLATE, env), new SelectionBatchSetter(id, cs.getColumnNodes(), "c"));
        jdbcTemplate.batchUpdate(String.format(SELECTION_TEMPLATE, env), new SelectionBatchSetter(id, cs.getRowNodes(), "r"));
        jdbcTemplate.batchUpdate(String.format(SELECTION_TEMPLATE, env), new SelectionBatchSetter(id, cs.getFilterNodes(), "f"));
    }
}
