package io.github.arberzyba.stempel.invoice;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Reads values straight from the invoice XML, for the cases where Mustang
 * doesn't give us what the file says. Parsing is XXE-safe (no DTDs).
 */
final class SafeXml {

    private static final String UBL_NAMESPACE_PREFIX = "urn:oasis:names:specification:ubl:schema:xsd:";

    private final Document document;
    private final XPath xpath = XPathFactory.newInstance().newXPath();

    private SafeXml(Document document) {
        this.document = document;
    }

    static SafeXml parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return new SafeXml(factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml))));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new InvoiceReadException("Could not parse the invoice XML", e);
        }
    }

    boolean isUbl() {
        String namespace = document.getDocumentElement().getNamespaceURI();
        return namespace != null && namespace.startsWith(UBL_NAMESPACE_PREFIX);
    }

    /** The text at the XPath, or null if it is missing or empty. */
    String text(String expression) {
        try {
            String value = xpath.evaluate(expression, document);
            return value.isBlank() ? null : value.trim();
        } catch (XPathExpressionException e) {
            // Our expressions are fixed strings, so this means a bug in our code, not a bad file.
            throw new IllegalStateException("Invalid XPath: " + expression, e);
        }
    }

    /** The number at the XPath, or null if it is missing or not a number. */
    BigDecimal decimal(String expression) {
        String value = text(expression);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Number of elements that match the XPath. */
    int count(String expression) {
        String value = text("count(" + expression + ")");
        return value == null ? 0 : (int) Double.parseDouble(value);
    }

    /**
     * Builds an XPath from element names that ignores namespace prefixes:
     * path("Invoice", "TaxTotal") → "/*[local-name()='Invoice']/*[local-name()='TaxTotal']".
     * Use "*" for "any root element".
     */
    static String path(String... elements) {
        StringBuilder result = new StringBuilder();
        for (String element : elements) {
            result.append('/').append(element.equals("*") ? "*" : "*[local-name()='" + element + "']");
        }
        return result.toString();
    }
}
