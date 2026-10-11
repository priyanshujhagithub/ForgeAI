package com.forgeai.project.exception;

// A stored GitHub token could not be decrypted: the encryption key changed
// or the stored value was altered. Reconnecting GitHub stores a fresh token.

public class TokenDecryptionException extends RuntimeException {

    public TokenDecryptionException(Throwable cause) {
        super(
                "The stored GitHub token can no longer be read. "
                        + "Reconnect your GitHub account.",
                cause
        );
    }
}
