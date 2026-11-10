# Test samples

Official example invoices used by the tests. They are not changed; tests that need broken invoices change them in memory.

| File | Source | License |
|---|---|---|
| `01.01a-INVOICE_ubl.xml`, `01.01a-INVOICE_uncefact.xml` | [KoSIT XRechnung test suite](https://github.com/itplr-kosit/xrechnung-testsuite), standard cases | Apache 2.0 |
| `03.01a-INVOICE_ubl.xml`, `03.01a-INVOICE_uncefact.xml` | KoSIT test suite v2026-08-31, standard cases (stated totals differ from a recalculation by one cent of rounding, and there is a prepaid amount) | Apache 2.0 |
| `04.05a-INVOICE_uncefact.xml` | KoSIT test suite v2026-08-31, extension cases (XRechnung Extension) | Apache 2.0 |
| `EN16931_Einfach.pdf` | [Mustang project](https://github.com/ZUGFeRD/mustangproject) test resources (ZUGFeRD, profile EN 16931) | Apache 2.0 |

The full KoSIT test suite is not committed. Run `scripts/fetch-kosit-testsuite.sh` to download it; `KositTestSuiteTest` then checks every file in it.
