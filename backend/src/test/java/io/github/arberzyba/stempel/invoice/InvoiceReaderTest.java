package io.github.arberzyba.stempel.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
    void rejectsAFileThatIsNotAnInvoice() {
        InputStream notAnInvoice = new ByteArrayInputStream(
                "Hallo, ich bin keine Rechnung.".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> reader.read(notAnInvoice))
                .isInstanceOf(InvoiceReadException.class);
    }
}
