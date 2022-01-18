package de.novatec.dwhexport.configuration.model;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class MetricQuery {

    @NotBlank
    private String name;

    @NotBlank
    private String query;
}
