package com.samwang.flexirate;

/**
 * Thrown when the top-level document is malformed or missing required arrays.
 */
public class FatalBillingException extends RuntimeException {
    public FatalBillingException(String message) {
        super(message);
    }
    public FatalBillingException(String message, Throwable cause) {
        super(message, cause);
    }
}
