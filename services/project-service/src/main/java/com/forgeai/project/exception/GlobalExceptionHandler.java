package com.forgeai.project.exception;

// Every error leaves the API as an RFC 9457 problem document with an accurate status,
// so a downstream failure is never masked by a generic 403 or 500.

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ProjectNotFoundException.class)
    public ProblemDetail handleProjectNotFound(ProjectNotFoundException exception){
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                exception.getMessage()
        );
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ProblemDetail handleEmailAlreadyExists(EmailAlreadyExistsException exception){
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage()
        );
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException exception){
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                exception.getMessage()
        );
    }

    @ExceptionHandler({
            GitHubNotConnectedException.class,
            RepositoryNotConnectedException.class
    })
    public ProblemDetail handleNotConnected(RuntimeException exception){
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage()
        );
    }

    @ExceptionHandler(AiServiceException.class)
    public ProblemDetail handleAiServiceFailure(AiServiceException exception){
        log.warn("AI service call failed: {}", exception.getMessage());
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY,
                exception.getMessage()
        );
    }

    // Raised by GitHubClient when GitHub answers with a 4xx or 5xx.
    @ExceptionHandler(RestClientResponseException.class)
    public ProblemDetail handleGitHubFailure(RestClientResponseException exception){
        log.warn("GitHub request failed with HTTP {}", exception.getStatusCode().value());
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY,
                "GitHub request failed with HTTP " + exception.getStatusCode().value()
        );
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception){
        log.error("Unhandled exception", exception);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected server error"
        );
    }
}
