package com.forgeai.project.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnalysisFilePolicyTest {

    private final AnalysisFilePolicy policy =
            new AnalysisFilePolicy();

    @Test
    void shouldIncludeSourceFiles() {
        assertTrue(policy.shouldInclude("src/main/App.java"));
        assertTrue(policy.shouldInclude("src/main/service.py"));
        assertTrue(policy.shouldInclude("README.md"));
        assertTrue(policy.shouldInclude("pom.xml"));
    }

    @Test
    void shouldExcludeGeneratedDirectories() {
        assertFalse(policy.shouldInclude("target/App.class"));
        assertFalse(policy.shouldInclude("build/classes/App.class"));
        assertFalse(policy.shouldInclude("dist/app.js"));
        assertFalse(policy.shouldInclude("node_modules/library/index.js"));
    }

    @Test
    void shouldExcludeBinaryFiles() {
        assertFalse(policy.shouldInclude("image.png"));
        assertFalse(policy.shouldInclude("archive.zip"));
        assertFalse(policy.shouldInclude("library.jar"));
        assertFalse(policy.shouldInclude("program.exe"));
    }

    @Test
    void shouldExcludeGitDirectory() {
        assertFalse(policy.shouldInclude(".git/config"));
        assertFalse(policy.shouldInclude("project/.git/config"));
    }

    @Test
    void shouldRejectInvalidPaths() {
        assertFalse(policy.shouldInclude(null));
        assertFalse(policy.shouldInclude(""));
        assertFalse(policy.shouldInclude("   "));
    }

    @Test
    void shouldHandleWindowsStylePaths() {
        assertTrue(policy.shouldInclude("src\\main\\App.java"));

        assertFalse(policy.shouldInclude(".git\\config"));
        assertFalse(policy.shouldInclude("target\\classes\\App.class"));
        assertFalse(policy.shouldInclude("node_modules\\library\\index.js"));
    }
}