package com.forgeai.project.dto;

// One entry of a Git tree: type is "blob" (file) or "tree" (directory).

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GitHubTreeEntry {

    private String path;
    private String mode;
    private String type;
    private String sha;
    private Integer size;
}
