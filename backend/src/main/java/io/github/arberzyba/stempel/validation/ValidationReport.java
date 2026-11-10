package io.github.arberzyba.stempel.validation;

import java.util.List;

/**
 * The result of checking an e-invoice against the official rules, as sent to the frontend.
 *
 * @param valid      true if there are no errors (warnings and notices are allowed)
 * @param format     "CII" or "UBL" (the XML syntax), or null if unknown
 * @param profile    a readable name of the rule set, e.g. "XRechnung 3.0" or "EN 16931"
 * @param pdfChecked true if the file was a PDF and its PDF/A format was checked too
 * @param findings   all problems found, errors first
 */
public record ValidationReport(
        boolean valid,
        String format,
        String profile,
        boolean pdfChecked,
        List<Finding> findings) {

    public enum Severity {
        /** The invoice breaks a rule and is not valid. */
        ERROR,
        /** Something is probably wrong, but the invoice is still valid. */
        WARNING,
        /** A hint, e.g. a rule that only applies to another invoice type. */
        NOTICE
    }

    /**
     * One problem found in the invoice.
     *
     * @param ruleId           the official rule ID, e.g. "BR-CO-15" (may be empty for PDF checks)
     * @param title            short German title
     * @param explanation      plain German explanation, or null if we have none for this rule
     * @param technicalMessage the original message from the rule set
     * @param area             where in the invoice: seller, buyer, lines, totals, payment, or null
     */
    public record Finding(
            Severity severity,
            String ruleId,
            String title,
            String explanation,
            String technicalMessage,
            String area) {
    }
}
