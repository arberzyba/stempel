package io.github.arberzyba.stempel.validation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import io.github.arberzyba.stempel.web.ApiExceptionHandler;

@WebMvcTest(ValidationController.class)
@Import({InvoiceValidator.class, RuleExplanations.class, ApiExceptionHandler.class})
class ValidationControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void returnsReportForValidInvoice() throws Exception {
        mvc.perform(multipart("/api/invoices/validate").file(sample("01.01a-INVOICE_ubl.xml")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.format").value("UBL"))
                .andExpect(jsonPath("$.profile").value("XRechnung 3.0"));
    }

    @Test
    void returnsErrorsForInvalidInvoice() throws Exception {
        String xml = new String(sample("01.01a-INVOICE_uncefact.xml").getBytes(), StandardCharsets.UTF_8)
                .replace("<ram:GrandTotalAmount>336.9</ram:GrandTotalAmount>",
                        "<ram:GrandTotalAmount>336.0</ram:GrandTotalAmount>");
        MockMultipartFile wrongTotal = new MockMultipartFile("file", "wrong.xml", "text/xml",
                xml.getBytes(StandardCharsets.UTF_8));

        // An invalid invoice is still a successful check, so the answer is 200 with valid=false.
        mvc.perform(multipart("/api/invoices/validate").file(wrongTotal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.findings[0].severity").value("ERROR"))
                .andExpect(jsonPath("$.findings[0].title").isNotEmpty());
    }

    @Test
    void rejectsEmptyFile() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "leer.xml", "text/xml", new byte[0]);

        mvc.perform(multipart("/api/invoices/validate").file(empty))
                .andExpect(status().isBadRequest());
    }

    private MockMultipartFile sample(String fileName) throws Exception {
        try (InputStream file = getClass().getResourceAsStream("/samples/" + fileName)) {
            return new MockMultipartFile("file", fileName, null, file.readAllBytes());
        }
    }
}
