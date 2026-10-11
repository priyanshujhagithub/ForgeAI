package com.forgeai.project.security;

// Encrypts a column at rest with AES-256-GCM.
//
// Used for GitHub access tokens: a copy of the database alone (a backup, a
// leaked dump) must not be enough to act as a user on GitHub. The key lives
// outside the database, in the environment.
//
// Stored format: "enc:v1:" + base64(12-byte IV || ciphertext || 16-byte tag).
// The version prefix lets the scheme change later, and lets values written
// before encryption existed be recognised (see TokenEncryptionMigrator).

import com.forgeai.project.exception.TokenDecryptionException;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    public static final String PREFIX = "enc:v1:";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public EncryptedStringConverter(
            @Value("${forgeai.encryption-key}") String base64Key
    ) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);

        if (keyBytes.length != KEY_BYTES) {
            throw new IllegalArgumentException(
                    "forgeai.encryption-key must be 32 bytes, base64-encoded "
                            + "(generate one with: openssl rand -base64 32)"
            );
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public String convertToDatabaseColumn(String plainText) {
        if (plainText == null) {
            return null;
        }

        try {
            // A fresh random IV per value: the same token never encrypts to
            // the same bytes twice, and GCM requires IVs never repeat per key.
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] stored = ByteBuffer.allocate(iv.length + cipherText.length)
                    .put(iv)
                    .put(cipherText)
                    .array();

            return PREFIX + Base64.getEncoder().encodeToString(stored);

        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt value", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        if (stored == null) {
            return null;
        }

        // Written before encryption was introduced. Readable as it is;
        // TokenEncryptionMigrator rewrites these rows at startup.
        if (!isEncrypted(stored)) {
            return stored;
        }

        try {
            ByteBuffer buffer = ByteBuffer.wrap(
                    Base64.getDecoder().decode(stored.substring(PREFIX.length()))
            );

            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));

            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);

        } catch (GeneralSecurityException | RuntimeException e) {
            // Wrong key or tampered data. GCM authenticates, so this is
            // detected instead of returning garbage.
            throw new TokenDecryptionException(e);
        }
    }

    public static boolean isEncrypted(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }
}
