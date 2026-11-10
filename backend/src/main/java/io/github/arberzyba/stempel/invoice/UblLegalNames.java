package io.github.arberzyba.stempel.invoice;

import java.io.IOException;
import java.io.StringReader;
import java.util.Optional;

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
 * Workaround for Mustang 2.26.0: for UBL invoices it ignores the legal name
 * (cbc:RegistrationName, BT-27 seller / BT-44 buyer). This reads it directly
 * from the XML. For non-UBL files (CII) there is nothing to fix.
 */
record UblLegalNames(String seller, String buyer) {

    private static final String UBL_NAMESPACE_PREFIX = "urn:oasis:names:specification:ubl:schema:xsd:";

    static Optional<UblLegalNames> from(String xml) {
        try {
            Document document = parseSafely(xml);
            String namespace = document.getDocumentElement().getNamespaceURI();
            if (namespace == null || !namespace.startsWith(UBL_NAMESPACE_PREFIX)) {
                return Optional.empty();
            }
            XPath xpath = XPathFactory.newInstance().newXPath();
            return Optional.of(new UblLegalNames(
                    registrationName(xpath, document, "AccountingSupplierParty"),
                    registrationName(xpath, document, "AccountingCustomerParty")));
        } catch (ParserConfigurationException | SAXException | IOException | XPathExpressionException e) {
            throw new InvoiceReadException("Could not read party names from the UBL invoice", e);
        }
    }

    /** Returns the party's RegistrationName, or null if the file has none. */
    private static String registrationName(XPath xpath, Document document, String partyElement)
            throws XPathExpressionException {
        // local-name() matches the element name and ignores its namespace prefix (cac:, cbc:).
        String expression = "/*/*[local-name()='" + partyElement + "']"
                + "/*[local-name()='Party']"
                + "/*[local-name()='PartyLegalEntity']"
                + "/*[local-name()='RegistrationName']";
        String value = xpath.evaluate(expression, document);
        return value.isBlank() ? null : value.trim();
    }

    /** Parses uploaded XML with DTDs switched off, which blocks XXE attacks. */
    private static Document parseSafely(String xml)
            throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }
}
