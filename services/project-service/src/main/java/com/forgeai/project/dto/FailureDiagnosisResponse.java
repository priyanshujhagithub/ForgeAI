package com.forgeai.project.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class FailureDiagnosisResponse {

    private String rootCause;
    private String fixStrategy;
    private List<String> filesToChange;
}
