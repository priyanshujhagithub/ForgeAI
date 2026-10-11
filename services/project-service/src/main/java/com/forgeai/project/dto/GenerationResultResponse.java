package com.forgeai.project.dto;

// Mirrors the Python GenerationResult.

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GenerationResultResponse {

    private List<CodeChangeResponse> changes;
}
