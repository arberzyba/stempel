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
import java.util.stream.IntStream;

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
            ZUGFeRDInvoiceImporter importer = new ZUGFeRDInvoiceImporter();
            // Without this, Mustang refuses invoices whose stated total differs from its own
            // recalculation, even by one cent of legal per-line rounding (found with the
            // KoSIT test suite). Wrong totals are reported by the validator instead.
            // It must be set BEFORE the file is passed in: the importer reads the file
            // right away in setInputStream (and in the constructor that takes a stream).
            importer.doIgnoreCalculationErrors();
            importer.setInputStream(file);
            invoice = importer.extractInvoice();
            xml = importer.getUTF8();
        } catch (Exception e) {
            // Mustang throws different exceptions for broken files; we turn them into one.
            throw new InvoiceReadException("Could not read the file as an e-invoice", e);
        }
        // Checked outside the try block, so a real bug in our own code isn't hidden as "bad file".
        if (invoice == null || xml == null || xml.isBlank()) {
            // For example a normal PDF with no invoice XML embedded.
            throw new InvoiceReadException("The file contains no e-invoice data", null);
        }
        SafeXml parsed = SafeXml.parse(xml);
        return toView(invoice, UblLegalNames.from(parsed), StatedAmounts.from(parsed));
    }

    private InvoiceView toView(Invoice invoice, Optional<UblLegalNames> ublNames, StatedAmounts stated) {
        // If the UBL name is missing, map() gives an empty Optional and orElse() uses Mustang's value.
        String sellerName = ublNames.map(UblLegalNames::seller).orElse(invoice.getSender().getName());
        String buyerName = ublNames.map(UblLegalNames::buyer).orElse(invoice.getRecipient().getName());

        // Show what the invoice states; only fill gaps with Mustang's calculation.
        TransactionCalculator calculator = new TransactionCalculator(invoice);
        BigDecimal net = firstNonNull(stated.net(), calculator.getTaxBasis());
        BigDecimal gross = firstNonNull(stated.gross(), calculator.getGrandTotal());
        BigDecimal vat = firstNonNull(stated.vat(), gross.subtract(net));
        BigDecimal duePayable = firstNonNull(stated.duePayable(), calculator.getDuePayable());
        List<InvoiceView.VatLine> vatLines = stated.vatLines().isEmpty() ? toVatLines(calculator) : stated.vatLines();

        return new InvoiceView(
                invoice.getNumber(),
                toLocalDate(invoice.getIssueDate()),
                toLocalDate(invoice.getDueDate()),
                invoice.getCurrency(),
                invoice.getReferenceNumber(),
                toParty(invoice.getSender(), sellerName),
                toParty(invoice.getRecipient(), buyerName),
                toLines(invoice.getZFItems(), stated.lineAmounts()),
                sortByRate(vatLines),
                new InvoiceView.Totals(net, vat, gross, stated.prepaid(), duePayable),
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

    private static List<InvoiceView.Line> toLines(IZUGFeRDExportableItem[] items, List<BigDecimal> statedAmounts) {
        if (items == null) {
            return List.of();
        }
        // Use the stated line amounts (BT-131) only if they clearly belong to Mustang's
        // items one to one; otherwise (e.g. sub-lines in XRechnung Extension) recalculate.
        boolean useStated = statedAmounts.size() == items.length;
        return IntStream.range(0, items.length).mapToObj(i -> {
            IZUGFeRDExportableItem item = items[i];
            IZUGFeRDExportableProduct product = item.getProduct();
            BigDecimal stated = useStated ? statedAmounts.get(i) : null;
            return new InvoiceView.Line(
                    product.getName(),
                    product.getDescription(),
                    item.getQuantity(),
                    product.getUnit(),
                    item.getPrice(),
                    product.getVATPercent(),
                    firstNonNull(stated, new LineCalculator(item).getItemTotalNetAmount()));
        }).toList();
    }

    /** Fallback when the file has no VAT breakdown: Mustang's calculation. */
    private static List<InvoiceView.VatLine> toVatLines(TransactionCalculator calculator) {
        // Mustang's amounts here are unrounded (e.g. 22.0402); EN 16931 amounts use 2 decimals.
        return calculator.getTaxDetails().stream()
                .map(vat -> new InvoiceView.VatLine(
                        vat.getCategoryCode(),
                        vat.getApplicablePercent(),
                        toCents(vat.getBasis()),
                        toCents(vat.getCalculated())))
                .toList();
    }

    /** Lowest rate first, for a stable display. Lines without a rate go last. */
    private static List<InvoiceView.VatLine> sortByRate(List<InvoiceView.VatLine> lines) {
        return lines.stream()
                .sorted(Comparator.comparing(InvoiceView.VatLine::percent,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private static <T> T firstNonNull(T value, T fallback) {
        return value != null ? value : fallback;
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
