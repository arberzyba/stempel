package io.github.arberzyba.stempel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.github.arberzyba.stempel.invoice.InvoiceReadException;
import io.github.arberzyba.stempel.invoice.InvoiceReader;
import io.github.arberzyba.stempel.invoice.InvoiceView;
import io.github.arberzyba.stempel.validation.InvoiceValidator;
import io.github.arberzyba.stempel.validation.RuleExplanations;
import io.github.arberzyba.stempel.validation.ValidationReport;
import io.github.arberzyba.stempel.validation.ValidationReport.Finding;
import io.github.arberzyba.stempel.validation.ValidationReport.Severity;

/**
 * Checks that every file in docs/demo does what docs/demo/README.md promises,
 * so the demo files can't quietly go wrong when the code changes.
 */
class DemoFilesTest {

    private static final Path DEMO = Path.of("..", "docs", "demo");

    private final InvoiceReader reader = new InvoiceReader();
    private final InvoiceValidator validator = new InvoiceValidator(new RuleExplanations());

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        // file                         | valid | errors (rule IDs, space-separated)
        "01-gueltig-xrechnung-ubl.xml   | true  | ",
        "02-gueltig-xrechnung-cii.xml   | true  | ",
        "03-gueltig-zugferd.pdf         | true  | ",
        "04-fehler-gesamtbetrag.xml     | false | BR-CO-15 BR-CO-16",
        "05-fehler-kaeuferreferenz.xml  | false | BR-DE-15",
        "06-fehler-mehrere.xml          | false | BR-CO-15 BR-CO-16 BR-DE-15 BR-DE-2 BR-DE-8",
        "07-anzahlung-guthaben.xml      | true  | ",
    })
    void demoFileGivesTheDocumentedResult(String fileName, boolean valid, String errors) throws IOException {
        byte[] content = Files.readAllBytes(DEMO.resolve(fileName));

        InvoiceView view = reader.read(new ByteArrayInputStream(content));
        assertThat(view.number()).isNotBlank();

        ValidationReport report = validator.validate(content, fileName);
        assertThat(report.valid()).isEqualTo(valid);
        List<String> expectedErrors = errors == null ? List.of() : Arrays.asList(errors.trim().split("\\s+"));
        assertThat(report.findings()).filteredOn(f -> f.severity() == Severity.ERROR)
                .extracting(Finding::ruleId)
                .containsExactlyInAnyOrderElementsOf(expectedErrors);
    }

    @Test
    void realisticNamesInTheGeneratedFiles() throws IOException {
        InvoiceView view = reader.read(Files.newInputStream(DEMO.resolve("01-gueltig-xrechnung-ubl.xml")));

        assertThat(view.seller().name()).isEqualTo("Fachverlag Sonnenschein GmbH");
        assertThat(view.buyer().name()).isEqualTo("Café Morgenrot GmbH");
        assertThat(view.buyer().city()).isEqualTo("Nürnberg");
    }

    @Test
    void downPaymentLeavesACredit() throws IOException {
        InvoiceView view = reader.read(Files.newInputStream(DEMO.resolve("07-anzahlung-guthaben.xml")));

        assertThat(view.totals().gross()).isEqualByComparingTo("336.90");
        assertThat(view.totals().prepaid()).isEqualByComparingTo("400.00");
        assertThat(view.totals().duePayable()).isEqualByComparingTo("-63.10");
    }

    @Test
    void extensionGetsThePartialCheckWarning() throws IOException {
        byte[] content = Files.readAllBytes(DEMO.resolve("08-xrechnung-extension.xml"));

        ValidationReport report = validator.validate(content, "08-xrechnung-extension.xml");

        assertThat(report.profile()).isEqualTo("XRechnung 3.0 Extension");
        assertThat(report.findings()).anyMatch(f -> f.severity() == Severity.WARNING
                && f.title().equals("Diese XRechnung-Variante prüft Stempel nur teilweise"));
    }

    @Test
    void normalPdfIsNotReadable() {
        assertThatThrownBy(() -> reader.read(Files.newInputStream(DEMO.resolve("09-keine-e-rechnung.pdf"))))
                .isInstanceOf(InvoiceReadException.class);
    }
}
