package io.github.arberzyba.stempel.validation;

import java.io.IOException;

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
public class ValidationController {

    private final InvoiceValidator validator;

    public ValidationController(InvoiceValidator validator) {
        this.validator = validator;
    }

    /** Upload an XRechnung XML or ZUGFeRD PDF and get a check report with German explanations. */
    @PostMapping(path = "/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ValidationReport validate(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Die hochgeladene Datei ist leer.");
        }
        String name = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        return validator.validate(file.getBytes(), name);
    }
}
