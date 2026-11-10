package io.github.arberzyba.stempel.invoice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import io.github.arberzyba.stempel.web.ApiExceptionHandler;

/**
 * Tests the HTTP layer: starts only the web part of Spring (no real server) and
 * sends fake uploads. We use the real InvoiceReader, so the whole path is tested.
 */
@WebMvcTest(InvoiceController.class)
@Import({InvoiceReader.class, ApiExceptionHandler.class})
class InvoiceControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void returnsInvoiceAsJson() throws Exception {
        mvc.perform(multipart("/api/invoices/view").file(sample("01.01a-INVOICE_ubl.xml")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value("123456XX"))
                .andExpect(jsonPath("$.issueDate").value("2016-04-04"))
                .andExpect(jsonPath("$.buyer.name").value("[Buyer name]"))
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.totals.gross").value(336.90));
    }

    @Test
    void acceptsZugferdPdf() throws Exception {
        mvc.perform(multipart("/api/invoices/view").file(sample("EN16931_Einfach.pdf")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value("471102"));
    }

    @Test
    void explainsUnreadableFileInGerman() throws Exception {
        MockMultipartFile notAnInvoice = new MockMultipartFile(
                "file", "notiz.txt", "text/plain", "keine Rechnung".getBytes());

        mvc.perform(multipart("/api/invoices/view").file(notAnInvoice))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Datei nicht lesbar"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Die Datei ist keine")));
    }

    @Test
    void rejectsEmptyFile() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "leer.xml", "text/xml", new byte[0]);

        mvc.perform(multipart("/api/invoices/view").file(empty))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Die hochgeladene Datei ist leer."));
    }

    @Test
    void rejectsRequestWithoutFile() throws Exception {
        mvc.perform(multipart("/api/invoices/view"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Keine Datei"));
    }

    private MockMultipartFile sample(String fileName) throws Exception {
        try (InputStream file = getClass().getResourceAsStream("/samples/" + fileName)) {
            return new MockMultipartFile("file", fileName, null, file.readAllBytes());
        }
    }
}
