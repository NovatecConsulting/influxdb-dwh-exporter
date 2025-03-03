package de.novatec.dwhexport.service.influx;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts information from InfluxQL queries via regex patterns.
 */
@Slf4j
@Component
public class QueryExtractor {

    /**
     * Basically, extract the string after a FROM statement, which is enclosed by double quotes.
     * For example: SELECT * FROM "inspectit"."raw"."measure" --> inspectit
     */
    private static final String DATABASE_PATTERN = "(?i)\\bFROM\\s+\"([^\"]+)";

    /**
     * @param query the InfluxQL query body
     * @return the database
     * @throws IllegalArgumentException if no database could be extracted
     */
    public String extractDatabase(String query) {
        Pattern pattern = Pattern.compile(DATABASE_PATTERN);
        Matcher matcher = pattern.matcher(query);

        if (matcher.find()) {
            String database = matcher.group(1);
            log.debug("Extracted database from query: {}", database);
            return database;
        }
        else throw new IllegalArgumentException("No match found for database. You can specify the database directly as well");
    }
}
