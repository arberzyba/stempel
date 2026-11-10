package io.github.arberzyba.stempel;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mustangproject.Invoice;
import org.mustangproject.ZUGFeRD.TransactionCalculator;
import org.mustangproject.ZUGFeRD.ZUGFeRDInvoiceImporter;

/**
 * Learning test: checks that Mustang reads the official KoSIT sample invoice
 * the way we expect, in both XRechnung syntaxes (UBL and CII).
 */
class MustangImportTest {

    // Known Mustang quirk (2.26.0): for UBL files, getName() returns PartyName
    // (the trading name, BT-28) and ignores RegistrationName (the legal name,
    // BT-27/BT-44). So the seller shows the trading name and the buyer, who has
    // no PartyName in this sample, gets null. For CII both are correct.
    // Our InvoiceView must not rely on getName() alone for UBL files.
    // (An empty value in @CsvSource means null.)
    @ParameterizedTest
    @CsvSource({
        "01.01a-INVOICE_uncefact.xml, [Seller name],         [Buyer name]",
        "01.01a-INVOICE_ubl.xml,      [Seller trading name],"
    })
    void readsOfficialSampleInvoice(String fileName, String expectedSellerName,
                                    String expectedBuyerName) throws Exception {
        try (InputStream file = getClass().getResourceAsStream("/samples/" + fileName)) {
            assertThat(file).as("sample file on the test classpath").isNotNull();

            ZUGFeRDInvoiceImporter importer = new ZUGFeRDInvoiceImporter(file);
            Invoice invoice = importer.extractInvoice();

            assertThat(invoice.getNumber()).isEqualTo("123456XX");
            assertThat(invoice.getCurrency()).isEqualTo("EUR");
            assertThat(invoice.getSender().getName()).isEqualTo(expectedSellerName);
            assertThat(invoice.getRecipient().getName()).isEqualTo(expectedBuyerName);
            assertThat(invoice.getZFItems()).hasSize(2);

            TransactionCalculator totals = new TransactionCalculator(invoice);
            assertThat(totals.getTaxBasis()).isEqualByComparingTo("314.86");
            assertThat(totals.getGrandTotal()).isEqualByComparingTo("336.90");
        }
    }
}
