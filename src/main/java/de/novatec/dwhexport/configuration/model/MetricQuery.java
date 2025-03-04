package de.novatec.dwhexport.configuration.model;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;

@Data
public class MetricQuery {

    /** the custom metric name */
    @NotBlank
    private String name;

    /** the database name - in InfluxDBv2 the bucket name */
    @NotNull
    private String database = "";

    /** the InfluxQL query to collect metrics */
    @NotBlank
    private String query;
}
