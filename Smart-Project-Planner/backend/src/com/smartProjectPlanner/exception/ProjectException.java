package com.smartProjectPlanner.exception;

/**
 * Custom checked exception for business validation and workflow errors.
 */
public class ProjectException extends Exception {
    private final int statusCode;

    public ProjectException(String message) {
        super(message);
        this.statusCode = 400; // Bad Request default
    }

    public ProjectException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public ProjectException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 500;
    }

    public ProjectException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
