package io.github.arberzyba.stempel.validation;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.mustangproject.validator.EPart;
import org.mustangproject.validator.ESeverity;
import org.mustangproject.validator.ValidationContext;
import org.mustangproject.validator.ValidationResultItem;
import org.mustangproject.validator.ZUGFeRDValidator;
import org.springframework.stereotype.Service;

import io.github.arberzyba.stempel.validation.RuleExplanations.Explanation;
import io.github.arberzyba.stempel.validation.ValidationReport.Finding;
import io.github.arberzyba.stempel.validation.ValidationReport.Severity;

/**
 * Checks an e-invoice with Mustang's validator (the official EN 16931 and
 * XRechnung 3.0 Schematron rules, plus PDF/A for ZUGFeRD) and turns the
 * result into a {@link ValidationReport} with German explanations.
 * Works only in memory: the validator gets the bytes and writes no files.
 */
@Service
public class InvoiceValidator {

    // Mustang appends " [ID BR-CO-15] from /xslt/...)" to each message; we cut that off.
    private static final Pattern MUSTANG_SUFFIX = Pattern.compile("\\s*\\[ID [^\\]]*\\] from .*$", Pattern.DOTALL);
    // Rule texts start with their ID, e.g. "[BR-CO-15]-Invoice total ..." or "[BR-DE-15] Das Element ...".
    private static final Pattern RULE_PREFIX = Pattern.compile("^\\[[^\\]]+\\]\\s*-?\\s*");
    private static final Pattern XRECHNUNG_VERSION = Pattern.compile("xrechnung_(\\d+\\.\\d+)");
    private static final String UNSUPPORTED_VERSION = "Unsupported XR version";
    /** Our own explanation key in rule-explanations-de.properties (not an official rule). */
    static final String PARTIAL_CHECK = "STEMPEL-PARTIAL-CHECK";

    private final RuleExplanations explanations;

    public InvoiceValidator(RuleExplanations explanations) {
        this.explanations = explanations;
    }

    public ValidationReport validate(byte[] content, String fileName) {
        // A new validator per request: it keeps state, so sharing one between
        // requests running at the same time would mix up their results.
        // (The compiled rule sets are cached inside Mustang, so this stays fast.)
        ZUGFeRDValidator validator = new ZUGFeRDValidator();
        validator.validate(content, fileName);
        ValidationContext context = validator.getContext();

        boolean partlySupported = isPartlySupported(context.getProfile());
        List<Finding> findings = Stream.concat(
                        partlySupported ? Stream.of(partialCheckWarning()) : Stream.empty(),
                        context.getResults().stream()
                                // Mustang can't check XRechnung CVD fully; our warning above explains that.
                                .filter(item -> !UNSUPPORTED_VERSION.equals(cleanMessage(item.getMessage())))
                                .map(this::toFinding))
                .sorted(Comparator.comparing(Finding::severity).thenComparing(Finding::ruleId))
                .toList();

        boolean valid = findings.stream().noneMatch(f -> f.severity() == Severity.ERROR);
        return new ValidationReport(valid, formatOf(context), profileName(context.getProfile()),
                context.hasPDF(), findings);
    }

    private Finding toFinding(ValidationResultItem item) {
        String ruleId = item.getID() == null ? "" : item.getID().trim();
        String message = cleanMessage(item.getMessage());
        Severity severity = toSeverity(item.getSeverity());
        Optional<Explanation> explanation = explanations.find(ruleId);

        String title = explanation.map(Explanation::title).orElseGet(() -> fallbackTitle(item, message));
        String text = explanation.map(Explanation::text).orElse(null);
        String area = explanation.map(Explanation::area).orElseGet(() -> areaFromLocation(item.getLocation()));
        return new Finding(severity, ruleId, title, text, message, area);
    }

    /**
     * XRechnung Extension and XRechnung CVD are special variants with extra rules
     * that Mustang 2.26.0 doesn't fully know, so some of its errors may be wrong
     * (found with the KoSIT test suite, whose valid examples got errors).
     */
    public static boolean isPartlySupported(String profile) {
        if (profile == null) {
            return false;
        }
        String lower = profile.toLowerCase(Locale.ROOT);
        return lower.contains(":extension:xrechnung") || lower.contains("xrechnung:cvd");
    }

    private Finding partialCheckWarning() {
        Explanation text = explanations.find(PARTIAL_CHECK)
                .orElseThrow(() -> new IllegalStateException(PARTIAL_CHECK + " is missing"));
        return new Finding(Severity.WARNING, "", text.title(), text.text(), "", null);
    }

    static String cleanMessage(String message) {
        if (message == null) {
            return "";
        }
        String text = MUSTANG_SUFFIX.matcher(message).replaceFirst("");
        text = RULE_PREFIX.matcher(text.strip()).replaceFirst("");
        return text.replaceAll("\\s+", " ").strip();
    }

    private static Severity toSeverity(ESeverity severity) {
        if (severity == null) {
            return Severity.ERROR;
        }
        return switch (severity) {
            case notice -> Severity.NOTICE;
            case warning -> Severity.WARNING;
            // error, fatal and exception all mean: the invoice is not valid.
            default -> Severity.ERROR;
        };
    }

    /** Used when we have no German explanation for a rule. */
    private static String fallbackTitle(ValidationResultItem item, String message) {
        // Checked first: Mustang marks some fatal errors (e.g. "File too small") as part of the PDF check.
        if (item.getSeverity() == ESeverity.fatal || item.getSeverity() == ESeverity.exception) {
            return "Die Datei konnte nicht geprüft werden";
        }
        if (item.getPart() == EPart.pdf) {
            return "Das PDF entspricht nicht dem Archivformat PDF/A";
        }
        // Rule texts of the XRechnung rules are already German; EN 16931 ones are English.
        return message.isEmpty() ? "Unbekanntes Problem" : message;
    }

    /** Guesses the invoice area from the XPath location Mustang reports (works for CII and UBL). */
    static String areaFromLocation(String location) {
        if (location == null) {
            return null;
        }
        String path = location.toLowerCase(Locale.ROOT);
        if (path.contains("sellertradeparty") || path.contains("accountingsupplierparty")) {
            return "seller";
        }
        if (path.contains("buyertradeparty") || path.contains("accountingcustomerparty")) {
            return "buyer";
        }
        if (path.contains("includedsupplychaintradelineitem") || path.contains("invoiceline")) {
            return "lines";
        }
        if (path.contains("paymentmeans") || path.contains("paymentterms")) {
            return "payment";
        }
        if (path.contains("monetarysummation") || path.contains("legalmonetarytotal") || path.contains("taxtotal")) {
            return "totals";
        }
        return null;
    }

    private static String formatOf(ValidationContext context) {
        // Mustang reports "CII" even when it could not read the file at all; only trust it for real invoices.
        return context.getProfile() == null ? null : context.getFormat();
    }

    /** "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0" → "XRechnung 3.0" */
    static String profileName(String profile) {
        if (profile == null || profile.isBlank()) {
            return null;
        }
        String lower = profile.toLowerCase(Locale.ROOT);
        Matcher xrechnung = XRECHNUNG_VERSION.matcher(lower);
        if (xrechnung.find()) {
            String name = "XRechnung " + xrechnung.group(1);
            if (lower.contains(":extension:xrechnung")) return name + " Extension";
            if (lower.contains("xrechnung:cvd")) return name + " CVD";
            return name;
        }
        if (lower.contains("factur-x.eu:1p0:minimum")) return "ZUGFeRD MINIMUM";
        if (lower.contains("factur-x.eu:1p0:basicwl")) return "ZUGFeRD BASIC WL";
        if (lower.contains("factur-x.eu:1p0:basic")) return "ZUGFeRD BASIC";
        if (lower.contains("factur-x.eu:1p0:extended") || lower.contains("conformant#urn:zugferd")) {
            return "ZUGFeRD EXTENDED";
        }
        if (lower.startsWith("urn:cen.eu:en16931:2017")) {
            return "EN 16931";
        }
        return profile;
    }
}
