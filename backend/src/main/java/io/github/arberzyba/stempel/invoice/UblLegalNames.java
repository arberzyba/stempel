package io.github.arberzyba.stempel.invoice;

import static io.github.arberzyba.stempel.invoice.SafeXml.path;

import java.util.Optional;

/**
 * Workaround for Mustang 2.26.0: for UBL invoices it ignores the legal name
 * (cbc:RegistrationName, BT-27 seller / BT-44 buyer). This reads it directly
 * from the XML. For non-UBL files (CII) there is nothing to fix.
 */
record UblLegalNames(String seller, String buyer) {

    static Optional<UblLegalNames> from(SafeXml xml) {
        if (!xml.isUbl()) {
            return Optional.empty();
        }
        return Optional.of(new UblLegalNames(
                registrationName(xml, "AccountingSupplierParty"),
                registrationName(xml, "AccountingCustomerParty")));
    }

    /** The party's RegistrationName, or null if the file has none. */
    private static String registrationName(SafeXml xml, String party) {
        return xml.text(path("*", party, "Party", "PartyLegalEntity", "RegistrationName"));
    }
}
