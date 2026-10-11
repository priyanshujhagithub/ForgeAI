package com.forgeai.project.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RepositorySnapshotBudgetTest {

    private final RepositorySnapshotBudget budget =
            new RepositorySnapshotBudget();

    @Test
    void shouldAllowFileWithinLimits() {
        assertTrue(
                budget.canIncludeFile(
                        0,
                        0,
                        1000
                )
        );
    }

    @Test
    void shouldRejectWhenMaximumFileCountReached() {
        assertFalse(
                budget.canIncludeFile(
                        200,
                        0,
                        1000
                )
        );
    }

    @Test
    void shouldRejectFileLargerThanMaximum() {
        assertFalse(
                budget.canIncludeFile(
                        0,
                        0,
                        500 * 1024 + 1
                )
        );
    }

    @Test
    void shouldAllowFileExactlyAtMaximumSize() {
        assertTrue(
                budget.canIncludeFile(
                        0,
                        0,
                        500 * 1024
                )
        );
    }

    @Test
    void shouldRejectWhenTotalSizeWouldExceedLimit() {
        assertFalse(
                budget.canIncludeFile(
                        10,
                        5L * 1024 * 1024 - 100,
                        101
                )
        );
    }

    @Test
    void shouldAllowTotalSizeExactlyAtLimit() {
        assertTrue(
                budget.canIncludeFile(
                        10,
                        5L * 1024 * 1024 - 100,
                        100
                )
        );
    }

    @Test
    void shouldRejectUnknownFileSize() {
        assertFalse(
                budget.canIncludeFile(
                        0,
                        0,
                        null
                )
        );
    }

    @Test
    void shouldRejectNegativeFileSize() {
        assertFalse(
                budget.canIncludeFile(
                        0,
                        0,
                        -1
                )
        );
    }
}