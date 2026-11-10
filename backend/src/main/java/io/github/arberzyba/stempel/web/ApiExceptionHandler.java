package io.github.arberzyba.stempel.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

import io.github.arberzyba.stempel.invoice.InvoiceReadException;

/**
 * Turns errors into clear JSON answers (RFC 9457 "problem details") with German
 * text the frontend can show as is. Never logs file contents.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvoiceReadException.class)
    ProblemDetail unreadableInvoice(InvoiceReadException e) {
        // Log the technical reason for us, but send only a friendly message to the user.
        log.info("Rejected upload: {} ({})", e.getMessage(),
                e.getCause() == null ? "no cause" : e.getCause().getClass().getSimpleName());
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Datei nicht lesbar",
                "Die Datei ist keine lesbare E-Rechnung. Bitte laden Sie eine XRechnung (XML) "
                        + "oder eine ZUGFeRD-Rechnung (PDF) hoch.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail fileTooLarge(MaxUploadSizeExceededException e) {
        return problem(HttpStatus.CONTENT_TOO_LARGE, "Datei zu groß",
                "Die Datei ist zu groß. Erlaubt sind höchstens 10 MB.");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ProblemDetail noFile(MissingServletRequestPartException e) {
        return problem(HttpStatus.BAD_REQUEST, "Keine Datei", "Bitte wählen Sie eine Datei aus.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail statusError(ResponseStatusException e) {
        return problem(e.getStatusCode(), "Ungültige Anfrage", e.getReason());
    }

    private static ProblemDetail problem(HttpStatusCode status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
