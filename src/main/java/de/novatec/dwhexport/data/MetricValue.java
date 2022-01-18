package de.novatec.dwhexport.data;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MetricValue {

    long startInMillis;

    double value;
}
