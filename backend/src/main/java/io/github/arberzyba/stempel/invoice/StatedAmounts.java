package io.github.arberzyba.stempel.invoice;

import static io.github.arberzyba.stempel.invoice.SafeXml.path;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The totals and VAT amounts exactly as the invoice states them (BT-106 to BT-117).
 *
 * Mustang recalculates these from the lines, and a legal invoice may differ from
 * that by a cent because of rounding per line. A viewer must show what the
 * invoice says; whether it adds up is the validator's job (rules BR-CO-*).
 * Each value is null if the file doesn't contain it.
 */
record StatedAmounts(
        BigDecimal net,
        BigDecimal vat,
        BigDecimal gross,
        BigDecimal prepaid,
        BigDecimal duePayable,
        List<InvoiceView.VatLine> vatLines,
        /** Net amount of each line (BT-131), in document order; null where missing. */
        List<BigDecimal> lineAmounts) {

    static StatedAmounts from(SafeXml xml) {
        return xml.isUbl() ? fromUbl(xml) : fromCii(xml);
    }

    private static StatedAmounts fromCii(SafeXml xml) {
        String settlement = path("CrossIndustryInvoice", "SupplyChainTradeTransaction", "ApplicableHeaderTradeSettlement");
        String sums = settlement + path("SpecifiedTradeSettlementHeaderMonetarySummation");
        String currency = xml.text(settlement + path("InvoiceCurrencyCode"));

        List<InvoiceView.VatLine> vatLines = new ArrayList<>();
        String taxes = settlement + path("ApplicableTradeTax");
        for (int i = 1; i <= xml.count(taxes); i++) {
            String tax = taxes + "[" + i + "]";
            vatLines.add(new InvoiceView.VatLine(
                    xml.text(tax + path("CategoryCode")),
                    xml.decimal(tax + path("RateApplicablePercent")),
                    xml.decimal(tax + path("BasisAmount")),
                    xml.decimal(tax + path("CalculatedAmount"))));
        }

        String lines = path("CrossIndustryInvoice", "SupplyChainTradeTransaction", "IncludedSupplyChainTradeLineItem");
        List<BigDecimal> lineAmounts = new ArrayList<>();
        for (int i = 1; i <= xml.count(lines); i++) {
            lineAmounts.add(xml.decimal(lines + "[" + i + "]" + path("SpecifiedLineTradeSettlement",
                    "SpecifiedTradeSettlementLineMonetarySummation", "LineTotalAmount")));
        }

        return new StatedAmounts(
                xml.decimal(sums + path("TaxBasisTotalAmount")),
                // There can be a second TaxTotalAmount in the accounting currency; we want the invoice currency.
                xml.decimal(sums + path("TaxTotalAmount") + inCurrency(currency)),
                xml.decimal(sums + path("GrandTotalAmount")),
                xml.decimal(sums + path("TotalPrepaidAmount")),
                xml.decimal(sums + path("DuePayableAmount")),
                vatLines,
                lineAmounts);
    }

    private static StatedAmounts fromUbl(SafeXml xml) {
        String sums = path("*", "LegalMonetaryTotal");
        String currency = xml.text(path("*", "DocumentCurrencyCode"));
        // As above: a second TaxTotal may exist for the accounting currency.
        String taxTotal = path("*", "TaxTotal") + "[" + path("TaxAmount").substring(1) + inCurrency(currency) + "]";

        List<InvoiceView.VatLine> vatLines = new ArrayList<>();
        String subtotals = taxTotal + path("TaxSubtotal");
        for (int i = 1; i <= xml.count(subtotals); i++) {
            String subtotal = subtotals + "[" + i + "]";
            vatLines.add(new InvoiceView.VatLine(
                    xml.text(subtotal + path("TaxCategory", "ID")),
                    xml.decimal(subtotal + path("TaxCategory", "Percent")),
                    xml.decimal(subtotal + path("TaxableAmount")),
                    xml.decimal(subtotal + path("TaxAmount"))));
        }

        // Invoices have InvoiceLine, credit notes have CreditNoteLine.
        String lines = "/*/*[local-name()='InvoiceLine' or local-name()='CreditNoteLine']";
        List<BigDecimal> lineAmounts = new ArrayList<>();
        for (int i = 1; i <= xml.count(lines); i++) {
            lineAmounts.add(xml.decimal("(" + lines + ")[" + i + "]" + path("LineExtensionAmount")));
        }

        return new StatedAmounts(
                xml.decimal(sums + path("TaxExclusiveAmount")),
                xml.decimal(taxTotal + path("TaxAmount")),
                xml.decimal(sums + path("TaxInclusiveAmount")),
                xml.decimal(sums + path("PrepaidAmount")),
                xml.decimal(sums + path("PayableAmount")),
                vatLines,
                lineAmounts);
    }

    /** An XPath filter for elements with currencyID = the invoice currency (or any, if unknown). */
    private static String inCurrency(String currency) {
        if (currency == null || !currency.matches("[A-Za-z]{3}")) {
            return "[1]";
        }
        return "[@currencyID='" + currency + "' or not(@currencyID)]";
    }
}
