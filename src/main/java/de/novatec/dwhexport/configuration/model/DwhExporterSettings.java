package de.novatec.dwhexport.configuration.model;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@ConfigurationProperties(prefix="dwh")
@Configuration
@Validated
public class DwhExporterSettings {

    /**
     * Token for accessing the exposed endpoint.
     */
    private String token = "";

    @NotNull
    private List<MetricQuery> metrics;
}
