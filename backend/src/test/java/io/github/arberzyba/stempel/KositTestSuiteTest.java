package io.github.arberzyba.stempel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import io.github.arberzyba.stempel.invoice.InvoiceReader;
import io.github.arberzyba.stempel.invoice.InvoiceView;
import io.github.arberzyba.stempel.validation.InvoiceValidator;
import io.github.arberzyba.stempel.validation.RuleExplanations;
import io.github.arberzyba.stempel.validation.ValidationReport;
import io.github.arberzyba.stempel.validation.ValidationReport.Severity;

/**
 * Runs every invoice of the official KoSIT XRechnung test suite through the
 * reader and the validator. All of them are valid examples, so each must be
 * readable and (unless it is a variant Stempel only partly supports) valid.
 *
 * Skipped unless the suite was downloaded with scripts/fetch-kosit-testsuite.sh.
 */
class KositTestSuiteTest {

    private static final Path SUITE = Path.of(".testsuite", "kosit", "instances");

    private final InvoiceReader reader = new InvoiceReader();
    private final InvoiceValidator validator = new InvoiceValidator(new RuleExplanations());

    @TestFactory
    Stream<DynamicTest> everyOfficialExampleIsReadableAndValid() throws IOException {
        assumeTrue(Files.isDirectory(SUITE), "KoSIT test suite not downloaded; run scripts/fetch-kosit-testsuite.sh");

        List<Path> files;
        try (Stream<Path> paths = Files.walk(SUITE)) {
            files = paths.filter(path -> path.toString().endsWith(".xml")).sorted().toList();
        }
        assertThat(files).as("invoices in the test suite").hasSizeGreaterThan(50);

        // One test per file, named after it, so a failure says exactly which invoice broke.
        return files.stream().map(file -> DynamicTest.dynamicTest(SUITE.relativize(file).toString(), () -> {
            byte[] content = Files.readAllBytes(file);

            InvoiceView view = reader.read(new ByteArrayInputStream(content));
            assertThat(view.number()).as("invoice number").isNotBlank();
            assertThat(view.seller().name()).as("seller name").isNotBlank();
            assertThat(view.buyer().name()).as("buyer name").isNotBlank();
            assertThat(view.lines()).as("lines").isNotEmpty();
            assertThat(view.totals().duePayable()).as("amount due").isNotNull();

            ValidationReport report = validator.validate(content, file.getFileName().toString());
            if (!InvoiceValidator.isPartlySupported(rawProfile(content))) {
                assertThat(report.findings())
                        .as("errors in an official valid example")
                        .noneMatch(finding -> finding.severity() == Severity.ERROR);
            }
        }));
    }

    /** The profile ID straight from the XML (CII or UBL), to tell which variant a file is. */
    private static String rawProfile(byte[] content) {
        String xml = new String(content, StandardCharsets.UTF_8);
        int start = xml.indexOf("urn:cen.eu:en16931:2017");
        return start < 0 ? null : xml.substring(start, xml.indexOf('<', start));
    }
}
