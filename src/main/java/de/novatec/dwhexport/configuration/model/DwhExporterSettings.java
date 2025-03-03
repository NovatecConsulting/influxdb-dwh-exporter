package de.novatec.dwhexport.configuration.model;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "dwh")
@Configuration
@Validated
public class DwhExporterSettings {

    /**
     * Token for accessing the exposed endpoint.
     */
    private String token = "";

    /**
     * True, if the database for queries should be extracted via regex from the query body.
     * False, if the database should be explicitly specified for every query.
     */
    private boolean deriveDatabaseFromQuery = false;

    @NotNull
    private List<MetricQuery> metrics;
}
