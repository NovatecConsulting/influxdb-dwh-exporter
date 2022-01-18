package de.novatec.dwhexport.data;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class Metric {

    String metricPath;

    @Singular
    List<MetricValue> metricValues;
}
