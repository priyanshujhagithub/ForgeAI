package com.forgeai.project.service;

import org.springframework.stereotype.Component;

@Component
public class AnalysisFilePolicy {

    public boolean shouldInclude(String path) {

        if (path == null || path.isBlank()) {
            return false;
        }

        String normalized = path
                .trim()
                .replace('\\', '/')
                .toLowerCase();

        String[] segments = normalized.split("/");

        for (String segment : segments) {
            if (segment.equals(".git") ||
                    segment.equals("node_modules") ||
                    segment.equals("target") ||
                    segment.equals("build") ||
                    segment.equals("dist")) {
                return false;
            }
        }

        if (normalized.endsWith(".class") ||
                normalized.endsWith(".jar") ||
                normalized.endsWith(".war") ||
                normalized.endsWith(".exe") ||
                normalized.endsWith(".dll")) {
            return false;
        }

        if (normalized.endsWith(".png") ||
                normalized.endsWith(".jpg") ||
                normalized.endsWith(".jpeg") ||
                normalized.endsWith(".gif") ||
                normalized.endsWith(".mp4") ||
                normalized.endsWith(".zip")) {
            return false;
        }

        return true;
    }
}