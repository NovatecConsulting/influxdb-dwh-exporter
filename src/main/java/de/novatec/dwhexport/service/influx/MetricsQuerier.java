package de.novatec.dwhexport.service.influx;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.domain.InfluxQLQuery;
import com.influxdb.query.InfluxQLQueryResult;
import de.novatec.dwhexport.configuration.model.MetricQuery;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Sends queries to InfluxDBv2.
 */
@Slf4j
@Component
@AllArgsConstructor
public class MetricsQuerier {

    private InfluxDBClient influx;

    public InfluxQLQueryResult executeQuery(MetricQuery metric, long startMillis, long endMillis, Duration interval) {
        StringLookup lookup = buildVariableLookUp(startMillis, endMillis, interval);
        StringSubstitutor subst = new StringSubstitutor(lookup);

        String parametrizedQuery = metric.getQuery();
        String queryString = subst.replace(parametrizedQuery);
        log.debug("Executing query: {}", queryString);

        // We still use InfluxQL instead of Flux
        InfluxQLQuery influxQLQuery = new InfluxQLQuery(queryString, metric.getDatabase());
        return influx.getInfluxQLQueryApi().query(influxQLQuery);
    }

    private StringLookup buildVariableLookUp(long startMillis, long endMillis, Duration interval) {
        return (variable) -> {
            if (variable.equalsIgnoreCase("timeFilter")) {
                return buildTimeFilter(startMillis, endMillis);
            }
            if (variable.equalsIgnoreCase("interval")) {
                return DurationStyle.SIMPLE.print(interval);
            }
            throw new IllegalArgumentException("Unknown query variable: " + variable);
        };
    }

    private String buildTimeFilter(long startMillis, long endMillis) {
        return "(time >= " + startMillis + "000000" + " AND time < " + endMillis + "000000)";
    }
}
