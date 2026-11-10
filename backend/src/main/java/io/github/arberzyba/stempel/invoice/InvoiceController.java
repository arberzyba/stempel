package io.github.arberzyba.stempel.invoice;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceReader reader;

    // Constructor injection: Spring sees this constructor and passes in its InvoiceReader.
    public InvoiceController(InvoiceReader reader) {
        this.reader = reader;
    }

    /** Upload an XRechnung XML or ZUGFeRD PDF and get it back as readable JSON. */
    @PostMapping(path = "/view", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public InvoiceView view(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Die hochgeladene Datei ist leer.");
        }
        try (InputStream content = file.getInputStream()) {
            return reader.read(content);
        }
    }
}
