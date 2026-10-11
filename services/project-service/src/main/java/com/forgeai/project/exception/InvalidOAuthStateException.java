package com.forgeai.project.exception;

// GitHub called back with a state we did not issue, already used, or that
// expired. Usually the callback page was reloaded, or the link was left
// open too long.

public class InvalidOAuthStateException extends RuntimeException {

    public InvalidOAuthStateException() {
        super(
                "This GitHub authorisation link has expired or was already used. "
                        + "Start the connection again from ForgeAI."
        );
    }
}
