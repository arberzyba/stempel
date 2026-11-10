package io.github.arberzyba.stempel.invoice;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.mustangproject.BankDetails;
import org.mustangproject.Invoice;
import org.mustangproject.TradeParty;
import org.mustangproject.ZUGFeRD.IZUGFeRDExportableItem;
import org.mustangproject.ZUGFeRD.IZUGFeRDExportableProduct;
import org.mustangproject.ZUGFeRD.IZUGFeRDTradeSettlement;
import org.mustangproject.ZUGFeRD.LineCalculator;
import org.mustangproject.ZUGFeRD.TransactionCalculator;
import org.mustangproject.ZUGFeRD.ZUGFeRDInvoiceImporter;
import org.springframework.stereotype.Service;

/**
 * Reads an e-invoice (XRechnung XML or ZUGFeRD PDF) with Mustang and turns it
 * into an {@link InvoiceView}. Works only in memory: nothing is stored.
 */
@Service
public class InvoiceReader {

    public InvoiceView read(InputStream file) {
        Invoice invoice;
        String xml;
        try {
            ZUGFeRDInvoiceImporter importer = new ZUGFeRDInvoiceImporter(file);
            invoice = importer.extractInvoice();
            xml = importer.getUTF8();
        } catch (Exception e) {
            // Mustang throws different exceptions for broken files; we turn them into one.
            throw new InvoiceReadException("Could not read the file as an e-invoice", e);
        }
        return toView(invoice, UblLegalNames.from(xml));
    }

    private InvoiceView toView(Invoice invoice, Optional<UblLegalNames> ublNames) {
        // If the UBL name is missing, map() gives an empty Optional and orElse() uses Mustang's value.
        String sellerName = ublNames.map(UblLegalNames::seller).orElse(invoice.getSender().getName());
        String buyerName = ublNames.map(UblLegalNames::buyer).orElse(invoice.getRecipient().getName());

        TransactionCalculator calculator = new TransactionCalculator(invoice);
        BigDecimal net = calculator.getTaxBasis();
        BigDecimal gross = calculator.getGrandTotal();

        return new InvoiceView(
                invoice.getNumber(),
                toLocalDate(invoice.getIssueDate()),
                toLocalDate(invoice.getDueDate()),
                invoice.getCurrency(),
                invoice.getReferenceNumber(),
                toParty(invoice.getSender(), sellerName),
                toParty(invoice.getRecipient(), buyerName),
                toLines(invoice.getZFItems()),
                toVatLines(calculator),
                new InvoiceView.Totals(net, gross.subtract(net), gross, calculator.getDuePayable()),
                new InvoiceView.Payment(invoice.getPaymentTermDescription(), findIban(invoice)));
    }

    private static InvoiceView.Party toParty(TradeParty party, String name) {
        return new InvoiceView.Party(
                name,
                party.getStreet(),
                party.getZIP(),
                party.getLocation(),
                party.getCountry(),
                party.getVatID());
    }

    private static List<InvoiceView.Line> toLines(IZUGFeRDExportableItem[] items) {
        if (items == null) {
            return List.of();
        }
        return Stream.of(items).map(item -> {
            IZUGFeRDExportableProduct product = item.getProduct();
            return new InvoiceView.Line(
                    product.getName(),
                    product.getDescription(),
                    item.getQuantity(),
                    product.getUnit(),
                    item.getPrice(),
                    product.getVATPercent(),
                    new LineCalculator(item).getItemTotalNetAmount());
        }).toList();
    }

    private static List<InvoiceView.VatLine> toVatLines(TransactionCalculator calculator) {
        // getTaxDetails() is a Set with no fixed order, so we sort by rate for a stable display.
        // Mustang's amounts here are unrounded (e.g. 22.0402); EN 16931 amounts use 2 decimals.
        return calculator.getTaxDetails().stream()
                .map(vat -> new InvoiceView.VatLine(
                        vat.getCategoryCode(),
                        vat.getApplicablePercent(),
                        toCents(vat.getBasis()),
                        toCents(vat.getCalculated())))
                .sorted(Comparator.comparing(InvoiceView.VatLine::percent))
                .toList();
    }

    /** The seller's IBAN, if the invoice says to pay by bank transfer. */
    private static String findIban(Invoice invoice) {
        IZUGFeRDTradeSettlement[] settlements = invoice.getTradeSettlement();
        if (settlements != null) {
            for (IZUGFeRDTradeSettlement settlement : settlements) {
                if (settlement instanceof BankDetails bank && bank.getIBAN() != null) {
                    return bank.getIBAN();
                }
            }
        }
        List<BankDetails> sellerAccounts = invoice.getSender().getBankDetails();
        if (sellerAccounts != null && !sellerAccounts.isEmpty()) {
            return sellerAccounts.get(0).getIBAN();
        }
        return null;
    }

    private static BigDecimal toCents(BigDecimal amount) {
        return amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static LocalDate toLocalDate(Date date) {
        // Mustang uses the old java.util.Date; we convert it to the modern LocalDate.
        return date == null ? null : date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
