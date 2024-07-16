package org.apache.dubbo.xds.Exceptions;

public class ResourceInvalidException extends Exception {
    private static final long serialVersionUID = 0L;

    public ResourceInvalidException(String message) {
        super(message, null, false, false);
    }

    public ResourceInvalidException(String message, Throwable cause) {
        super(cause != null ? message + ": " + cause.getMessage() : message, cause, false, false);
    }
}
