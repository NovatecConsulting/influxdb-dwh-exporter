package de.novatec.dwhexport.service;

import com.influxdb.query.InfluxQLQueryResult;
import de.novatec.dwhexport.configuration.model.DwhExporterSettings;
import de.novatec.dwhexport.configuration.model.MetricQuery;
import de.novatec.dwhexport.data.Metric;
import de.novatec.dwhexport.service.influx.MetricsMapper;
import de.novatec.dwhexport.service.influx.MetricsQuerier;
import de.novatec.dwhexport.service.influx.QueryExtractor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class MetricsService {

    private MetricsQuerier querier;

    private MetricsMapper mapper;

    private QueryExtractor extractor;

    private DwhExporterSettings metrics;

    public ResponseEntity<?> processRangeOffsetRequest(String range, String offset, Duration intervalDur) {
        Duration rangeDur = Optional.ofNullable(range).map(DurationStyle.SIMPLE::parse).orElse(intervalDur);

        Duration offsetDur = Optional.ofNullable(offset).map(DurationStyle.SIMPLE::parse).orElse(Duration.ofMinutes(1));

        long now = System.currentTimeMillis();
        long endTime = (now - offsetDur.toMillis()) / intervalDur.toMillis() * intervalDur.toMillis();
        long startTime = endTime - rangeDur.toMillis();
        List<Metric> body = queryMetrics(intervalDur, startTime, endTime);
        return ResponseEntity.ok(body);
    }

    public ResponseEntity<?> processExplicitStartEndRequest(Long start, Long end, Duration intervalDur) {
        if (start == null || end == null) {
            return ResponseEntity.badRequest().body("You must specify both 'start' and 'end'!");
        }
        if (start % intervalDur.toMillis() != 0 || end % intervalDur.toMillis() != 0) {
            return ResponseEntity.badRequest()
                    .body("The 'start' and 'end' timestamps must be aligned to the given interval!");
        }
        List<Metric> body = queryMetrics(intervalDur, start, end);
        return ResponseEntity.ok(body);
    }

    private List<Metric> queryMetrics(Duration interval, long startTime, long endTime) {
        return metrics.getMetrics()
                .stream()
                .flatMap(metric -> queryMetric(metric, interval, startTime, endTime).stream())
                .collect(Collectors.toList());
    }

    private Collection<Metric> queryMetric(MetricQuery metric, Duration interval, long startTime, long endTime) {
        try {
            long intervalMillis = interval.toMillis();
            long extendedStartTime = startTime - intervalMillis;
            String database = getDatabase(metric);

            InfluxQLQueryResult data = querier.executeQuery(metric, database, extendedStartTime, endTime, interval);

            return mapper.map(data, metric, startTime, endTime);
        } catch (Exception e) {
            log.error("Error fetching data for {}", metric.getName(), e);
        }
        return Collections.emptyList();
    }

    /**
     * Get the database for the {@link com.influxdb.client.domain.InfluxQLQuery}.
     * If a database was specified explicitly, use the provided value.
     * If no database was specified AND the option to derive it from the query is enabled, extract the database from
     * the query body.
     *
     * @param metric the metric query
     * @return the database to use for the query
     */
    private String getDatabase(MetricQuery metric) {
        if(metric.getDatabase().isBlank() && metrics.isDeriveDatabaseFromQuery()) return extractor.extractDatabase(metric.getQuery());
        else return metric.getDatabase();
    }
}
