
package com.marquee.http;

public final class RequestBodyException extends Exception {

    private final int statusCode;

    public RequestBodyException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
