package fi.thl.pivot.datasource;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import fi.thl.pivot.annotation.AuditedMethod;
import fi.thl.pivot.annotation.Monitored;
import fi.thl.pivot.model.Report;
import fi.thl.pivot.model.Tuple;
import fi.thl.pivot.util.Constants;
import fi.thl.summary.SummaryException;
import fi.thl.summary.SummaryReader;
import fi.thl.summary.model.Summary;

/**
 * <p>
 * AmorDao is responsible for providing browsing capabilities of different hydra
 * instances processed by the AMOR program. The data is stored in a postgresql
 * database in amor_&lt;environment&gt; schemas where envinroment is one one of
 * deve, test and prod.
 * </p>
 * <p>
 * All hydras are listed in the meta_hydra and meta_table tables. Meta_hydra
 * provides a listing of hydras per subject and their update times. Each hydra
 * may comprise multiple cubes that use the same set of the metadata and tree
 * tables.
 * </p>
 * 
 * @author aleksiyrttiaho
 *
 */
@Component
public class AmorDao {

    private static final Logger LOG = Logger.getLogger(AmorDao.class);
    private static final int ID_ELEMENT_COUNT = 4;
    private static final String ID_SEPARATOR = "\\.";

    /**
     * Provides utility methods that produce environment specific table names
     * for queries accessing Amor produces cubes and their metadata
     * 
     * @author aleksiyrttiaho
     *
     */
    private abstract class AmorCallback {

        protected String createMetaName(Report input) {
            return String.format("amor_%s.x%s_meta", schema, input.getRunId());
        }

        protected String createTreeName(Report input) {
            return String.format("amor_%s.x%s_tree", schema, input.getRunId());
        }

        protected String createFactName(Report input) {
            return String.format("amor_%s.x%s_%s", schema, input.getRunId(), input.getFact());
        }

        protected String createHydraName(Report input) {
            return String.format("%s.%s.%s.%s", input.getSubject(), input.getHydra(), input.getFact(),
                    input.getRunId());
        }
    }

    /**
     * Maps a result set to a JDBC backed HydraSource object
     * 
     * @author aleksiyrttiaho
     *
     */
    private final class ResultSetToSource extends AmorCallback implements RowMapper<HydraSource> {

        @Override
        public HydraSource mapRow(ResultSet rs, int index) throws SQLException {
            return new ReportToSource().apply(new ReportMapper().mapRow(rs, index));
        }
    }

    /**
     * Constructs a new JDBC backed HydraSource based report definition.
     * 
     * @author aleksiyrttiaho
     *
     */
    private final class ReportToSource extends AmorCallback implements Function<Report, HydraSource> {

        @Override
        public HydraSource apply(Report input) {
            HydraSource source = new JDBCSource(createHydraName(input), input.getFact(), jdbcTemplate.getDataSource(),
                    queries, createFactName(input),
                    createTreeName(input), createMetaName(input), schema);
            source.setRunDate(input.getAdded());
            return source;
        }

    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("#{'${database.environment.schema}'}")
    private String schema;

    @Autowired
    @Qualifier("queries")
    private Properties queries;

    private Cache<String, HydraSource> sourceCache = CacheBuilder.newBuilder().expireAfterAccess(10, TimeUnit.MINUTES)
            .build();

    @AuditedMethod
    public List<Report> listReports(String environment) {
        Preconditions.checkArgument(checkEnvironment(environment), "IllegalEnvironment " + environment);
        return jdbcTemplate.query(String.format(queries.getProperty("list-reports"), schema), new ReportMapper(),
                environment, environment);
    }

   

    /**
     * Lists reports within the given amor subject. Used to provide a quick way
     * to navigate between different reports of the same subject
     * 
     * TODO: Currently only returns a list of summaries but not cubes
     * 
     * @param environment
     * @param subject
     * @return
     */
    @AuditedMethod
    public List<Report> listReports(String environment, String subject, String runid) {
        Preconditions.checkArgument(checkEnvironment(environment), "IllegalEnvironment " + environment);
        final Map<String, Report> reports = Maps.newHashMap();
        return jdbcTemplate.query(String.format(queries.getProperty("list-summary-name"), schema), new ReportMapper() {
            @Override
            public Report mapRow(ResultSet rs, int arg1) throws SQLException {
                String id = String.format("%s.%s.%s", rs.getString("run_id"), rs.getString("hydra"),
                        rs.getString("id"));
                if (!reports.containsKey(id)) {
                    reports.put(id, super.mapRow(rs, arg1));
                }
                Report r = reports.get(id);
                r.getTitle().setValue(rs.getString("lang"), rs.getString("title"));
                r.getSubjectTitle().setValue(rs.getString("lang"), rs.getString("subjectitle"));
                return r;
            }
        }, environment, subject);

    }

    /**
     * Utility method to access the latest version of a specific report
     * 
     * @param environment
     *            The environment (prod, test, deve) we're interested in
     * @param subject
     *            The subject of the hydra e.g. finres
     * @param hydra
     *            The hydra within the subject
     * @param fact
     *            The fact table within the hydra
     * @return
     */
    public Report loadLatestReport(final String environment, final String subject, final String hydra,
            final String fact) {
        Preconditions.checkArgument(checkEnvironment(environment), "IllegalEnvironment");
        Preconditions.checkNotNull(subject, "No subject specified");
        Preconditions.checkNotNull(hydra, "No hydra specified");
        Preconditions.checkNotNull(fact, "No fact specified");

        List<Report> reports = jdbcTemplate.query(String.format(queries.getProperty("load-latest-report"), schema),
                new ReportMapper(), subject, hydra, fact,
                environment);
        return reports.isEmpty() ? null : reports.get(0);
    }

    public List<Tuple> loadCubeMetadata(final String environment, final String fact, final String runId) {
        Preconditions.checkArgument(checkEnvironment(environment), "IllegalEnvironment");
        Preconditions.checkNotNull(fact, "No fact specified");
        Preconditions.checkNotNull(runId, "No run id specified");

        return jdbcTemplate.query(
                String.format(queries.getProperty("load-cube-metadata"), "amor_" + schema + ".x" + runId + "_meta"),
                new TupleMapper(), fact);
    }

    @AuditedMethod
    @Monitored
    public Summary loadSummary(final String environment, String id) {
        Preconditions.checkArgument(checkEnvironment(environment), "IllegalEnvironment");
        Preconditions.checkNotNull(id, "No source id provided");

        String[] params = id.split(ID_SEPARATOR);
        Preconditions.checkArgument(params.length <= ID_ELEMENT_COUNT, "Invalid id provided");
        String latestRunId = determineReportVersion(environment, params);

        List<Summary> summaries = jdbcTemplate.query(
                String.format("select * from amor_%1$s.x%2$s_amor_summary where summary_id = ?", schema, latestRunId),
                new RowMapper<Summary>() {

                    @Override
                    public Summary mapRow(ResultSet rs, int idx) throws SQLException {
                        try {
                            SummaryReader sr = new SummaryReader();
                            sr.read(new ByteArrayInputStream(rs.getString("summary_xml").getBytes("UTF-8")));
                            return sr.getSummary();
                        } catch (SummaryException e) {
                            LOG.error("Could not parse summary", e);
                            throw new IllegalStateException("Could not parse summary", e);
                        } catch (UnsupportedEncodingException e) {
                            LOG.fatal("Could not resolve UTF-8", e);
                            throw new IllegalStateException("Could not resolve known character encoding");
                        }
                    }
                }, params[2]);

        return summaries.isEmpty() ? null : summaries.get(0);
    }

    @AuditedMethod
    @Monitored
    public HydraSource loadSource(final String environment, String id) {
        Preconditions.checkArgument(checkEnvironment(environment), "IllegalEnvironment");
        Preconditions.checkNotNull(id, "No source id provided");

        String[] params = id.split(ID_SEPARATOR);
        Preconditions.checkArgument(params.length <= ID_ELEMENT_COUNT,
                "Invalid id provided " + Lists.newArrayList(params));

        String latestRunId = determineReportVersion(environment, params);
        id = id.replaceAll("latest", latestRunId);

        HydraSource cached = sourceCache.getIfPresent(id);
        if (null != cached) {
            return cached;
        }

        List<HydraSource> sources = jdbcTemplate.query(String.format(queries.getProperty("list-sources"), schema),
                new ResultSetToSource(), params[0],
                params[1], params[2], Long.parseLong(latestRunId), environment);

        if (sources.size() == 1) {
            sourceCache.put(id, sources.get(0));
            sources.get(0).setRunId(latestRunId);
            return sources.get(0);
        }

        LOG.warn("Could not find cube " + id);

        return null;
    }

    private String determineReportVersion(final String environment, String[] params) {
        String latestRunId;
        if (params.length < 4 || "latest".equals(params[3])) {
            Report r = loadLatestReport(environment, params[0], params[1], params[2]);
            latestRunId = r == null ? "0" : r.getRunId();
        } else {
            latestRunId = params[3];
        }
        LOG.debug(String.format("Run id %s => %s", Lists.newArrayList(params).toString(), latestRunId));
        return latestRunId;
    }

    /**
     * List all sources from the given environment. The method is based on the
     * {@link #listReports(String)} method and wraps each report to a
     * HydraSource instance that can further be used to access the actual
     * dataset and metadata of the cubes available in the environment.
     * 
     * IllegalArgumentException is thrown if environment is not one of valid
     * environments
     * 
     * @param environment
     *            The environment (prod, test, deve) we are interested in
     * 
     * @return
     */
    @AuditedMethod
    public List<HydraSource> listSources(final String environment) {
        Preconditions.checkArgument(checkEnvironment(environment), "IllegalEnvironment");
        return Lists.transform(listReports(environment), new ReportToSource());
    }


    private boolean checkEnvironment(String environment) {
        return Constants.VALID_ENVIRONMENTS.contains(environment);
    }

    /**
     * Takes in a fact or summary id and replaces the fact table identifier with
     * the one given as parameter. For example sade.seuranta.tiiviste.latest =>
     * sade.seuranta.fakta.latest
     * 
     * @param id
     * @param factTable
     * @return
     */
    public String replaceFactInIdentifier(String id, String factTable) {
        String[] params = id.split(ID_SEPARATOR);
        if (params.length == ID_ELEMENT_COUNT) {
            return Joiner.on(".").join(params[0], params[1], factTable, params[3]);
        } else {
            return Joiner.on(".").join(params[0], params[1], factTable);
        }

    }

}
