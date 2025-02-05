package de.novatec.dwhexport;

import de.novatec.dwhexport.configuration.model.DwhExporterSettings;
import de.novatec.dwhexport.service.MetricsService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.*;

@Slf4j
@RestController
@AllArgsConstructor
public class MetricsController {

    private MetricsService service;

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
            return service.processExplicitStartEndRequest(start, end, intervalDur);
        } else {
            return service.processRangeOffsetRequest(range, offset, intervalDur);
        }
    }
}
