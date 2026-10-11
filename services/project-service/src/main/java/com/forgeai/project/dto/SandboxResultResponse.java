package com.forgeai.project.dto;

// One test run inside the sandbox.

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SandboxResultResponse {

    // "passed", "tests_failed" or "infrastructure_error"
    private String outcome;
    private String command;
    private Integer exitCode;
    private String stdout;
    private String stderr;
    private double durationSeconds;
    private boolean timedOut;
    private String message;
}
