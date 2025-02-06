package de.novatec.dwhexport.service.influx;

import com.influxdb.query.InfluxQLQueryResult;
import de.novatec.dwhexport.configuration.model.MetricQuery;
import de.novatec.dwhexport.data.Metric;
import de.novatec.dwhexport.data.MetricValue;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookup;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps the query result from InfluxDB to {@link Metric}.
 */
@Slf4j
@Component
public class MetricsMapper {

    public List<Metric> map(InfluxQLQueryResult queryResult, MetricQuery metric, long startTime, long endTime) {
        List<Metric> result = queryResult.getResults()
            .stream()
            .flatMap(r -> r.getSeries().stream())
            .map(series -> getResultsFromSeries(series, metric, startTime, endTime))
            .toList();
        return result;
    }

    private Metric getResultsFromSeries(InfluxQLQueryResult.Series series, MetricQuery metric, long startTime, long endTime) {
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
}
