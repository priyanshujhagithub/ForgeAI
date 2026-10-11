package com.forgeai.project.dto;

// One generate-and-test attempt. diagnosis explains why this attempt failed;
// it is null when the attempt passed.

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AttemptRecordResponse {

    private int number;
    private FailureDiagnosisResponse diagnosis;
    private List<CodeChangeResponse> changes;
    private SandboxResultResponse sandboxResult;
}
