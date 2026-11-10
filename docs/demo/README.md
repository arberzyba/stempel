# Demo files

Invoices for trying Stempel by hand. Start Stempel (`docker compose up`), open http://localhost:8080 and drop in a file. `DemoFilesTest` checks on every build that each file still gives the result below.

| File | What it is | What you should see |
|---|---|---|
| `01-gueltig-xrechnung-ubl.xml` | Valid XRechnung 3.0, UBL syntax | Green stamp "GEPRÜFT · XRECHNUNG 3.0", report "Gültig", 1 notice (no delivery date) |
| `02-gueltig-xrechnung-cii.xml` | The same invoice in CII syntax | Same as 01; the report shows format "XML · CII" |
| `03-gueltig-zugferd.pdf` | Valid ZUGFeRD PDF, profile EN 16931, two VAT rates | Invoice appears first, "Wird geprüft …" for about a second, then "GEPRÜFT · EN 16931" and "PDF/A-3 geprüft". 7 notices (XRechnung rules, which don't apply to plain ZUGFeRD) |
| `04-fehler-gesamtbetrag.xml` | Total with VAT is 529.78 instead of 529.87 | Red stamp "NICHT GÜLTIG", errors BR-CO-15 and BR-CO-16. "Im Dokument zeigen" frames the totals |
| `05-fehler-kaeuferreferenz.xml` | XRechnung without a buyer reference (Leitweg-ID) | 1 error BR-DE-15; "Im Dokument zeigen" frames the recipient |
| `06-fehler-mehrere.xml` | No buyer reference, no seller contact, no buyer city, wrong total | 5 errors: BR-CO-15, BR-CO-16, BR-DE-15, BR-DE-2, BR-DE-8; each frames its part of the invoice |
| `07-anzahlung-guthaben.xml` | Valid; a 400 € down payment on a 336.90 € invoice | "Bereits gezahlt −400,00 €" and "Guthaben 63,10 €" in the totals |
| `08-xrechnung-extension.xml` | XRechnung Extension (special variant), from the KoSIT test suite | Warning "Diese XRechnung-Variante prüft Stempel nur teilweise", plus code-list errors Stempel can't judge for this variant |
| `09-keine-e-rechnung.pdf` | A normal PDF without e-invoice data | Error screen "Datei nicht lesbar" |

Two more things to try without a file from here:

- **Too large:** any file over 10 MB is rejected in the browser with "Datei zu groß", before it is uploaded.
- **Not an invoice at all:** any `.txt` or image gives "Datei nicht lesbar".

## Where the files come from

- 01, 02, 05, 06 and 07 are made by `generate.mjs` from the KoSIT sample 01.01a ([XRechnung test suite](https://github.com/itplr-kosit/xrechnung-testsuite), Apache 2.0). It replaces the placeholders with made-up names and addresses (e-mail domains end in `.example`, which is reserved for examples) and then removes or changes single fields for the error cases. Run it again with `node docs/demo/generate.mjs`.
- 03 is the ZUGFeRD example `EN16931_Einfach` by FeRD, from the [Mustang project](https://github.com/ZUGFeRD/mustangproject) (Apache 2.0).
- 04 is the XML of that same example with one change: `GrandTotalAmount` is 529.78 instead of 529.87. The FeRD usage notice at the top of the file is unchanged; FeRD's licence allows editing.
- 08 is the KoSIT test case 04.05a, unchanged.
- 09 is written by `generate.mjs`.
