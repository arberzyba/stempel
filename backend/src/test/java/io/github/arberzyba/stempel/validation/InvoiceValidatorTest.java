package io.github.arberzyba.stempel.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.arberzyba.stempel.validation.ValidationReport.Finding;
import io.github.arberzyba.stempel.validation.ValidationReport.Severity;

class InvoiceValidatorTest {

    private final InvoiceValidator validator = new InvoiceValidator(new RuleExplanations());

    @ParameterizedTest
    @CsvSource({"01.01a-INVOICE_uncefact.xml, CII", "01.01a-INVOICE_ubl.xml, UBL"})
    void officialXRechnungSamplesAreValid(String fileName, String expectedFormat) throws IOException {
        ValidationReport report = validator.validate(sample(fileName), fileName);

        assertThat(report.valid()).isTrue();
        assertThat(report.format()).isEqualTo(expectedFormat);
        assertThat(report.profile()).isEqualTo("XRechnung 3.0");
        assertThat(report.pdfChecked()).isFalse();
        assertThat(report.findings()).noneMatch(f -> f.severity() == Severity.ERROR);

        // The sample has no delivery date, which XRechnung recommends ("should").
        assertThat(report.findings()).anySatisfy(f -> {
            assertThat(f.ruleId()).isEqualTo("BR-DE-TMP-32");
            assertThat(f.severity()).isEqualTo(Severity.NOTICE);
            assertThat(f.title()).isEqualTo("Das Liefer- oder Leistungsdatum fehlt");
        });
    }

    @Test
    void zugferdPdfIsValidAndXRechnungRulesAreOnlyNotices() throws IOException {
        ValidationReport report = validator.validate(sample("EN16931_Einfach.pdf"), "EN16931_Einfach.pdf");

        assertThat(report.valid()).isTrue();
        assertThat(report.pdfChecked()).isTrue();
        assertThat(report.profile()).isEqualTo("EN 16931");
        // A ZUGFeRD invoice doesn't have to be an XRechnung, so e.g. BR-DE-15 is only a notice here.
        assertThat(report.findings()).filteredOn(f -> f.ruleId().startsWith("BR-DE"))
                .isNotEmpty()
                .allMatch(f -> f.severity() == Severity.NOTICE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"03.01a-INVOICE_uncefact.xml", "03.01a-INVOICE_ubl.xml"})
    void roundingPerLineIsValid(String fileName) throws IOException {
        // Stated totals one cent off a recalculation are still valid (see InvoiceReaderTest).
        assertThat(validator.validate(sample(fileName), fileName).valid()).isTrue();
    }

    @Test
    void warnsThatXRechnungExtensionIsOnlyPartlyChecked() throws IOException {
        ValidationReport report = validator.validate(sample("04.05a-INVOICE_uncefact.xml"), "04.05a.xml");

        assertThat(report.profile()).isEqualTo("XRechnung 3.0 Extension");
        assertThat(report.findings()).anySatisfy(f -> {
            assertThat(f.severity()).isEqualTo(Severity.WARNING);
            assertThat(f.title()).isEqualTo("Diese XRechnung-Variante prüft Stempel nur teilweise");
        });
        // The plain-XRechnung samples get no such warning.
        assertThat(validator.validate(sample("01.01a-INVOICE_ubl.xml"), "01.01a.xml").findings())
                .noneMatch(f -> f.severity() == Severity.WARNING);
    }

    @Test
    void findsAWrongTotal() throws IOException {
        byte[] wrongTotal = changeSample("01.01a-INVOICE_uncefact.xml",
                "<ram:GrandTotalAmount>336.9</ram:GrandTotalAmount>",
                "<ram:GrandTotalAmount>336.0</ram:GrandTotalAmount>");

        ValidationReport report = validator.validate(wrongTotal, "wrong-total.xml");

        assertThat(report.valid()).isFalse();
        Finding finding = findingFor(report, "BR-CO-15");
        assertThat(finding.severity()).isEqualTo(Severity.ERROR);
        assertThat(finding.title()).isEqualTo("Der Gesamtbetrag stimmt nicht");
        assertThat(finding.explanation()).contains("Netto plus Umsatzsteuer");
        assertThat(finding.area()).isEqualTo("totals");
        // The ID prefix and Mustang's "[ID ...] from ..." suffix are removed.
        assertThat(finding.technicalMessage())
                .startsWith("Invoice total amount with VAT (BT-112)")
                .doesNotContain("[ID", "xslt");
    }

    @Test
    void findsAMissingBuyerReference() throws IOException {
        byte[] noBuyerReference = changeSample("01.01a-INVOICE_uncefact.xml",
                "<ram:BuyerReference>04011000-12345-03</ram:BuyerReference>", "");

        ValidationReport report = validator.validate(noBuyerReference, "no-buyer-reference.xml");

        assertThat(report.valid()).isFalse();
        Finding finding = findingFor(report, "BR-DE-15");
        assertThat(finding.severity()).isEqualTo(Severity.ERROR);
        assertThat(finding.area()).isEqualTo("buyer");
        // Checks the German umlaut survives from the rule set to our report.
        assertThat(finding.technicalMessage()).contains("übermittelt");
    }

    @Test
    void reportsAFileThatIsNotAnInvoice() {
        ValidationReport report = validator.validate("Hallo".getBytes(StandardCharsets.UTF_8), "notiz.txt");

        assertThat(report.valid()).isFalse();
        assertThat(report.format()).isNull();
        assertThat(report.findings()).singleElement().satisfies(f -> {
            assertThat(f.severity()).isEqualTo(Severity.ERROR);
            assertThat(f.title()).isEqualTo("Die Datei konnte nicht geprüft werden");
        });
    }

    @Test
    void errorsComeFirst() throws IOException {
        byte[] wrongTotal = changeSample("01.01a-INVOICE_uncefact.xml",
                "<ram:GrandTotalAmount>336.9</ram:GrandTotalAmount>",
                "<ram:GrandTotalAmount>336.0</ram:GrandTotalAmount>");

        ValidationReport report = validator.validate(wrongTotal, "wrong-total.xml");

        assertThat(report.findings()).extracting(Finding::severity)
                .isSortedAccordingTo(Enum::compareTo)
                .first().isEqualTo(Severity.ERROR);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "[BR-CO-15]-Invoice total amount (BT-112). [ID BR-CO-15] from /xslt/x.xslt) | Invoice total amount (BT-112).",
        // "\\n" is the two characters \ and n; the test turns them into a real line break.
        "\\n   [BR-DE-15] Das Element muss   übermittelt werden.\\n [ID BR-DE-15] from /a.xslt) | Das Element muss übermittelt werden.",
        "File too small | File too small"
    })
    void cleansMustangMessages(String raw, String expected) {
        assertThat(InvoiceValidator.cleanMessage(raw.replace("\\n", "\n"))).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0, XRechnung 3.0",
        "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0#conformant#urn:xeinkauf.de:kosit:extension:xrechnung_3.0, XRechnung 3.0 Extension",
        "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0#compliant#urn:xeinkauf.de:kosit:xrechnung:cvd_0.9, XRechnung 3.0 CVD",
        "urn:cen.eu:en16931:2017,                                                EN 16931",
        "urn:factur-x.eu:1p0:minimum,                                            ZUGFeRD MINIMUM",
        "urn:cen.eu:en16931:2017#compliant#urn:factur-x.eu:1p0:basic,            ZUGFeRD BASIC"
    })
    void namesProfiles(String profile, String expected) {
        assertThat(InvoiceValidator.profileName(profile)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/rsm:CrossIndustryInvoice/rsm:SupplyChainTradeTransaction[1]/ram:ApplicableHeaderTradeAgreement[1]/ram:SellerTradeParty[1]",
        "/ubl:Invoice/cac:AccountingSupplierParty[1]"
    })
    void findsTheSellerAreaInCiiAndUbl(String location) {
        assertThat(InvoiceValidator.areaFromLocation(location)).isEqualTo("seller");
    }

    private static Finding findingFor(ValidationReport report, String ruleId) {
        return report.findings().stream()
                .filter(f -> f.ruleId().equals(ruleId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No finding for " + ruleId + " in " + report.findings()));
    }

    private byte[] sample(String fileName) throws IOException {
        try (InputStream file = getClass().getResourceAsStream("/samples/" + fileName)) {
            return file.readAllBytes();
        }
    }

    private byte[] changeSample(String fileName, String original, String replacement) throws IOException {
        String xml = new String(sample(fileName), StandardCharsets.UTF_8);
        assertThat(xml).as("the sample contains the text we want to change").contains(original);
        return xml.replace(original, replacement).getBytes(StandardCharsets.UTF_8);
    }
}
