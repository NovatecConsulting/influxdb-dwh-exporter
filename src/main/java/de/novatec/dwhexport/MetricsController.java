package de.novatec.dwhexport;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.domain.InfluxQLQuery;
import com.influxdb.query.InfluxQLQueryResult;
import de.novatec.dwhexport.configuration.model.DwhExporterSettings;
import de.novatec.dwhexport.configuration.model.MetricQuery;
import de.novatec.dwhexport.data.Metric;
import de.novatec.dwhexport.data.MetricValue;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@Slf4j
public class MetricsController {

    @Autowired
    private InfluxDBClient influx;

    @Autowired
    private DwhExporterSettings metrics;

    @GetMapping("/dwh")
    public ResponseEntity<?> collectMetrics(@RequestParam(required = false) String interval,
                                            @RequestParam(required = false) String range,
                                            @RequestParam(required = false) String offset,
                                            @RequestParam(required = false) Long start,
                                            @RequestParam(required = false) Long end,
                                            @RequestParam(required = false, defaultValue = "") String token) {

        if (!metrics.getToken().equalsIgnoreCase(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Duration intervalDur = Optional.ofNullable(interval)
                .map(DurationStyle.SIMPLE::parse)
                .orElse(Duration.ofMinutes(1));

        if (start != null || end != null) {
            if (range != null || offset != null) {
                return ResponseEntity.badRequest()
                        .body("You must either specify 'start' and 'end' or 'range' and 'offset', but not both!");
            }
            return processExplicitStartEndRequest(start, end, intervalDur);
        } else {
            return processRangeOffsetRequest(range, offset, intervalDur);
        }
    }

    private ResponseEntity<?> processRangeOffsetRequest(String range, String offset, Duration intervalDur) {
        Duration rangeDur = Optional.ofNullable(range).map(DurationStyle.SIMPLE::parse).orElse(intervalDur);

        Duration offsetDur = Optional.ofNullable(offset).map(DurationStyle.SIMPLE::parse).orElse(Duration.ofMinutes(1));

        long now = System.currentTimeMillis();
        long endTime = (now - offsetDur.toMillis()) / intervalDur.toMillis() * intervalDur.toMillis();
        long startTime = endTime - rangeDur.toMillis();
        List<Metric> body = queryMetrics(intervalDur, startTime, endTime);
        return ResponseEntity.ok(body);
    }

    private ResponseEntity<?> processExplicitStartEndRequest(Long start, Long end, Duration intervalDur) {
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

            InfluxQLQueryResult data = executeQuery(metric, extendedStartTime, endTime, interval);
            List<Metric> result = data.getResults()
                    .stream()
                    .flatMap(r -> r.getSeries().stream())
                    .map(series -> getResultsFromSeries(metric, series, startTime, endTime))
                    .toList();
            return result;
        } catch (Exception e) {
            log.error("Error fetching data for {}", metric.getName(), e);
        }
        return Collections.emptyList();
    }

    private Metric getResultsFromSeries(MetricQuery metric, InfluxQLQueryResult.Series series, long startTime, long endTime) {
        if (series.getColumns().size() != 2) {
            throw new IllegalArgumentException("Query returned more than one field!");
        }

        Map<String, String> tagsLowerCase = keysToLowerCase(series.getTags());
        StringLookup tagLookup = variable -> tagsLowerCase.getOrDefault(variable.toLowerCase(), "");

        int timeColumnIndex = series.getColumns().get("time");
        int valueColumnIndex = (timeColumnIndex + 1) % 2;

        Metric.MetricBuilder builder = Metric.builder();
        builder.metricPath(new StringSubstitutor(tagLookup).replace(metric.getName()));
        for (InfluxQLQueryResult.Series.Record record : series.getValues()) {
            Object[] values = record.getValues();
            Object timeCol = values[timeColumnIndex];
            Object valueCol = values[valueColumnIndex];

            try {
                // convert nanos to millis
                long timeMillis = Long.parseLong(timeCol.toString()) / 1000 / 1000;
                double resultValue = Double.parseDouble(valueCol.toString());
                if (timeMillis >= startTime && timeMillis < endTime) {
                    builder.metricValue(MetricValue.builder().startInMillis(timeMillis).value(resultValue).build());
                }
            } catch (NumberFormatException e) {
                // Ignore value
            }
        }
        return builder.build();
    }

    private Map<String, String> keysToLowerCase(Map<String, String> map) {
        if (CollectionUtils.isEmpty(map)) {
            return Collections.emptyMap();
        }

        return map.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().toLowerCase(), Map.Entry::getValue));
    }

    private InfluxQLQueryResult executeQuery(MetricQuery metric, long startMillis, long endMillis, Duration interval) {
        StringLookup lookup = (variable) -> {
            if (variable.equalsIgnoreCase("interval")) {
                return DurationStyle.SIMPLE.print(interval);
            }
            if (variable.equalsIgnoreCase("timeFilter")) {
                return buildTimeFilter(startMillis, endMillis);
            }
            throw new IllegalArgumentException("Unknown query variable: " + variable);
        };
        StringSubstitutor subst = new StringSubstitutor(lookup);
        String parametrizedQuery = metric.getQuery();
        String queryString = subst.replace(parametrizedQuery);
        log.debug("Executing query: {}", queryString);

        // We still use InfluxQL instead of Flux
        InfluxQLQuery influxQLQuery = new InfluxQLQuery(queryString, metric.getDatabase());
        return influx.getInfluxQLQueryApi().query(influxQLQuery);
    }

    private String buildTimeFilter(long startMillis, long endMillis) {
        return "(time >= " + startMillis + "000000" + " AND time < " + endMillis + "000000)";
    }
}
