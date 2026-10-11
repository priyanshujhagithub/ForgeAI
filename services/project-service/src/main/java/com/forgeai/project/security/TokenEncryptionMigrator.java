package com.forgeai.project.security;

// Encrypts GitHub tokens that were stored as plain text before
// EncryptedStringConverter existed. Runs at startup and is safe to run
// again: rows that are already encrypted are skipped.
//
// Plain JDBC is used on purpose. Going through the entity would not help:
// the decrypted value is unchanged, so Hibernate would see nothing to update.

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class TokenEncryptionMigrator {

    private static final Logger log =
            LoggerFactory.getLogger(TokenEncryptionMigrator.class);

    private final JdbcTemplate jdbcTemplate;
    private final EncryptedStringConverter converter;

    public TokenEncryptionMigrator(
            JdbcTemplate jdbcTemplate,
            EncryptedStringConverter converter
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.converter = converter;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void encryptPlainTextTokens() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, access_token FROM github_connections "
                        + "WHERE access_token NOT LIKE ?",
                EncryptedStringConverter.PREFIX + "%"
        );

        for (Map<String, Object> row : rows) {
            jdbcTemplate.update(
                    "UPDATE github_connections SET access_token = ? WHERE id = ?",
                    converter.convertToDatabaseColumn((String) row.get("access_token")),
                    row.get("id")
            );
        }

        if (!rows.isEmpty()) {
            log.info("Encrypted {} GitHub token(s) that were stored as plain text", rows.size());
        }
    }
}
