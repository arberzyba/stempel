package io.github.arberzyba.stempel.validation;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Component;

/** Plain German explanations for rule IDs, loaded from rule-explanations-de.properties. */
@Component
public class RuleExplanations {

    static final String FILE = "/rule-explanations-de.properties";

    public record Explanation(String title, String text, String area) {
    }

    private final Properties properties = new Properties();

    public RuleExplanations() {
        try (InputStream in = getClass().getResourceAsStream(FILE)) {
            if (in == null) {
                throw new IllegalStateException(FILE + " is missing from the classpath");
            }
            // Read as UTF-8: Properties.load(InputStream) would assume ISO-8859-1 and break the umlauts.
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Optional<Explanation> find(String ruleId) {
        if (ruleId == null || ruleId.isBlank()) {
            return Optional.empty();
        }
        String title = properties.getProperty(ruleId + ".title");
        if (title == null) {
            return Optional.empty();
        }
        return Optional.of(new Explanation(
                title,
                properties.getProperty(ruleId + ".text"),
                properties.getProperty(ruleId + ".area")));
    }

    /** All rule IDs that have an explanation (used by tests). */
    Set<String> ruleIds() {
        Set<String> ids = new TreeSet<>();
        for (String key : properties.stringPropertyNames()) {
            ids.add(key.substring(0, key.lastIndexOf('.')));
        }
        return ids;
    }
}
