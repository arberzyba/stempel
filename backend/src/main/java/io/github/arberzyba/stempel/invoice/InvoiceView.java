package io.github.arberzyba.stempel.invoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A readable version of an e-invoice, as sent to the frontend.
 * Only plain data: no Mustang types leak out of this package.
 */
public record InvoiceView(
        String number,
        LocalDate issueDate,
        LocalDate dueDate,
        String currency,
        String buyerReference,
        Party seller,
        Party buyer,
        List<Line> lines,
        List<VatLine> vat,
        Totals totals,
        Payment payment) {

    public record Party(
            String name,
            String street,
            String zip,
            String city,
            String country,
            String vatId) {
    }

    public record Line(
            String name,
            String description,
            BigDecimal quantity,
            String unit,
            BigDecimal unitPrice,
            BigDecimal vatPercent,
            BigDecimal netAmount) {
    }

    public record VatLine(
            String category,
            BigDecimal percent,
            BigDecimal basis,
            BigDecimal amount) {
    }

    public record Totals(
            BigDecimal net,
            BigDecimal vat,
            BigDecimal gross,
            BigDecimal duePayable) {
    }

    public record Payment(
            String terms,
            String iban) {
    }
}
