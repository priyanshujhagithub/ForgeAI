package com.forgeai.project.dto;

// Tells the Python service where the sandbox should clone from.
// accessToken is only set for private repositories.

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AiRepositoryRef {

    private String cloneUrl;
    private String baseBranch;
    private String baseSha;
    private String accessToken;
}
