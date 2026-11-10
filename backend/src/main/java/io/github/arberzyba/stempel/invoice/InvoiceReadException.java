package io.github.arberzyba.stempel.invoice;

/** Thrown when an uploaded file can't be read as an e-invoice. */
public class InvoiceReadException extends RuntimeException {

    public InvoiceReadException(String message, Throwable cause) {
        super(message, cause);
    }
}
