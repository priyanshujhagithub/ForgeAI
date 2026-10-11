package com.forgeai.project.dto;

// One generated file change. content is the complete resulting file.

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CodeChangeResponse {

    private String path;
    // "modify", "create" or "delete"
    private String action;
    private String content;
    private String explanation;
}
