package com.forgeai.project.security;

import com.forgeai.project.exception.TokenDecryptionException;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptedStringConverterTest {

    private static final String KEY = key((byte) 'a');
    private static final String OTHER_KEY = key((byte) 'b');
    private static final String TOKEN = "gho_exampleAccessToken1234567890";

    private final EncryptedStringConverter converter = new EncryptedStringConverter(KEY);

    @Test
    void roundTripReturnsTheOriginalToken() {
        String stored = converter.convertToDatabaseColumn(TOKEN);

        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo(TOKEN);
    }

    @Test
    void storedValueIsMarkedAndDoesNotContainTheToken() {
        String stored = converter.convertToDatabaseColumn(TOKEN);

        assertThat(stored).startsWith("enc:v1:").doesNotContain(TOKEN);
        assertThat(EncryptedStringConverter.isEncrypted(stored)).isTrue();
    }

    @Test
    void sameTokenEncryptsDifferentlyEachTime() {
        // A fresh IV per value: equal tokens must not be recognisable as equal.
        assertThat(converter.convertToDatabaseColumn(TOKEN))
                .isNotEqualTo(converter.convertToDatabaseColumn(TOKEN));
    }

    @Test
    void valueWrittenBeforeEncryptionExistedIsStillReadable() {
        assertThat(converter.convertToEntityAttribute(TOKEN)).isEqualTo(TOKEN);
        assertThat(EncryptedStringConverter.isEncrypted(TOKEN)).isFalse();
    }

    @Test
    void nullStaysNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void wrongKeyIsDetectedInsteadOfReturningGarbage() {
        String stored = converter.convertToDatabaseColumn(TOKEN);
        EncryptedStringConverter otherConverter = new EncryptedStringConverter(OTHER_KEY);

        assertThatThrownBy(() -> otherConverter.convertToEntityAttribute(stored))
                .isInstanceOf(TokenDecryptionException.class)
                .hasMessageContaining("Reconnect your GitHub account");
    }

    @Test
    void tamperedValueIsRejected() {
        String stored = converter.convertToDatabaseColumn(TOKEN);
        char last = stored.charAt(stored.length() - 2);
        String tampered = stored.substring(0, stored.length() - 2)
                + (last == 'A' ? 'B' : 'A')
                + stored.charAt(stored.length() - 1);

        assertThatThrownBy(() -> converter.convertToEntityAttribute(tampered))
                .isInstanceOf(TokenDecryptionException.class);
    }

    @Test
    void keyOfTheWrongLengthIsRefusedAtStartup() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new EncryptedStringConverter(shortKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
    }

    private static String key(byte fill) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, fill);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
