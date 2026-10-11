package com.forgeai.project.exception;

// The Python AI service failed, timed out, or could not be reached.

public class AiServiceException extends RuntimeException {

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
