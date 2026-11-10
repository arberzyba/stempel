package io.github.arberzyba.stempel.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class InvoiceReaderTest {

    // A plain unit test: no Spring needed, so we simply create the object ourselves.
    private final InvoiceReader reader = new InvoiceReader();

    @ParameterizedTest
    @ValueSource(strings = {"01.01a-INVOICE_uncefact.xml", "01.01a-INVOICE_ubl.xml"})
    void bothSyntaxesGiveTheSameView(String fileName) throws Exception {
        InvoiceView view;
        try (InputStream file = getClass().getResourceAsStream("/samples/" + fileName)) {
            view = reader.read(file);
        }

        assertThat(view.number()).isEqualTo("123456XX");
        assertThat(view.issueDate()).isEqualTo(LocalDate.of(2016, 4, 4));
        assertThat(view.currency()).isEqualTo("EUR");
        assertThat(view.buyerReference()).isEqualTo("04011000-12345-03");

        // The legal names, also for UBL (see UblLegalNames).
        assertThat(view.seller().name()).isEqualTo("[Seller name]");
        assertThat(view.buyer().name()).isEqualTo("[Buyer name]");
        assertThat(view.seller().city()).isEqualTo("[Seller city]");
        assertThat(view.seller().vatId()).isEqualTo("DE 123456789");

        assertThat(view.lines()).hasSize(2);
        InvoiceView.Line first = view.lines().get(0);
        assertThat(first.name()).isEqualTo("Zeitschrift [...]");
        assertThat(first.netAmount()).isEqualByComparingTo("288.79");
        assertThat(first.vatPercent()).isEqualByComparingTo("7");

        assertThat(view.vat()).singleElement().satisfies(vat -> {
            assertThat(vat.percent()).isEqualByComparingTo("7");
            assertThat(vat.basis()).isEqualByComparingTo("314.86");
            assertThat(vat.amount()).isEqualByComparingTo("22.04");
        });

        assertThat(view.totals().net()).isEqualByComparingTo("314.86");
        assertThat(view.totals().vat()).isEqualByComparingTo("22.04");
        assertThat(view.totals().gross()).isEqualByComparingTo("336.90");
        assertThat(view.totals().duePayable()).isEqualByComparingTo("336.90");

        assertThat(view.payment().iban()).isEqualTo("DE79000000001234567890");
        assertThat(view.payment().terms()).isEqualTo("Zahlbar sofort ohne Abzug.");
    }

    @Test
    void readsZugferdPdf() throws Exception {
        InvoiceView view;
        try (InputStream file = getClass().getResourceAsStream("/samples/EN16931_Einfach.pdf")) {
            view = reader.read(file);
        }

        assertThat(view.number()).isEqualTo("471102");
        assertThat(view.issueDate()).isEqualTo(LocalDate.of(2024, 11, 15));
        assertThat(view.seller().name()).isEqualTo("Lieferant GmbH");
        assertThat(view.seller().city()).isEqualTo("München");
        assertThat(view.buyer().name()).isEqualTo("Kunden AG Mitte");
        assertThat(view.lines()).hasSize(2);

        // Two VAT rates, sorted by rate: 7 % then 19 %.
        assertThat(view.vat()).extracting(InvoiceView.VatLine::amount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("19.25"), new BigDecimal("37.62"));

        assertThat(view.totals().net()).isEqualByComparingTo("473.00");
        assertThat(view.totals().vat()).isEqualByComparingTo("56.87");
        assertThat(view.totals().gross()).isEqualByComparingTo("529.87");
    }

    @Test
    void rejectsPdfWithoutInvoice() throws Exception {
        // A normal one-page PDF, made with PDFBox (which Mustang already uses).
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(pdf);
        }

        assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(pdf.toByteArray())))
                .isInstanceOf(InvoiceReadException.class);
    }

    /**
     * XXE attack: the file defines an "entity" that points to a file on the server
     * and uses it as the invoice number. A vulnerable parser would copy the secret
     * into the result. The upload must be rejected and the secret must not leak.
     */
    @ParameterizedTest
    @CsvSource({
        "01.01a-INVOICE_uncefact.xml, rsm:CrossIndustryInvoice, <ram:ID>123456XX</ram:ID>, <ram:ID>&xxe;</ram:ID>",
        "01.01a-INVOICE_ubl.xml,      ubl:Invoice,              <cbc:ID>123456XX</cbc:ID>, <cbc:ID>&xxe;</cbc:ID>"
    })
    void rejectsXxeAttack(String fileName, String rootElement, String original, String attack,
                          @TempDir Path tempDir) throws Exception {
        Path secret = tempDir.resolve("secret.txt");
        Files.writeString(secret, "TOP-SECRET-12345");

        String xml;
        try (InputStream file = getClass().getResourceAsStream("/samples/" + fileName)) {
            xml = new String(file.readAllBytes(), StandardCharsets.UTF_8);
        }
        String doctype = "<!DOCTYPE " + rootElement
                + " [<!ENTITY xxe SYSTEM \"" + secret.toUri() + "\">]>\n";
        String malicious = xml.replaceFirst("\\?>\\s*", "?>\n" + doctype).replaceFirst(original, attack);
        assertThat(malicious).contains("<!DOCTYPE", "&xxe;");

        assertThatThrownBy(() -> reader.read(
                new ByteArrayInputStream(malicious.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(InvoiceReadException.class)
                .satisfies(e -> assertThat(stackTraceOf(e)).doesNotContain("TOP-SECRET"));
    }

    private static String stackTraceOf(Throwable e) {
        StringWriter text = new StringWriter();
        e.printStackTrace(new PrintWriter(text));
        return text.toString();
    }

    @Test
    void rejectsAFileThatIsNotAnInvoice() {
        InputStream notAnInvoice = new ByteArrayInputStream(
                "Hallo, ich bin keine Rechnung.".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> reader.read(notAnInvoice))
                .isInstanceOf(InvoiceReadException.class);
    }
}
