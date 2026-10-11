package com.forgeai.project.service;

import com.forgeai.project.dto.GitHubFileResponse;
import com.forgeai.project.dto.RepositoryMetadata;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RepositoryMetadataDetector {

    public RepositoryMetadata detect(
            List<GitHubFileResponse> files
    ) {
        boolean maven=false;
        boolean gradle=false;
        boolean node=false;
        boolean python=false;
        boolean go=false;
        boolean springboot=false;

        for(GitHubFileResponse file:files){

            String path=file.getPath();

            if(path==null){
                continue;
            }

            String normalized=path
                    .replace('\\','/')
                    .toLowerCase();

            String fileName=normalized;

            int lastSlash=normalized.lastIndexOf('/');

            if(lastSlash>=0){
                fileName=normalized.substring(lastSlash+1);
            }

            if(fileName.equals("pom.xml")){
                maven=true;
            }

            if(fileName.equals("build.gradle") || fileName.equals("build.gradle.kts")){
                gradle=true;
            }

            if(fileName.equals("package.json")){
                node=true;
            }

            if(fileName.equals("requirements.txt") || fileName.equals("pyproject.toml")){
                python=true;
            }

            if(fileName.equals("go.mod")){
                go=true;
            }

            if(file.getContent() != null){
                String content=
                        file.getContent().toLowerCase();

                if(content.contains("org.springframework.boot")){
                    springboot=true;
                }
            }
        }

        String projectType="UNKNOWN";

        if(maven){
            projectType="MAVEN";
        }else if(gradle){
            projectType="GRADLE";
        }else if(node){
            projectType="NODE";
        }else if(python){
            projectType="PYTHON";
        }else if(go){
            projectType="GO";
        }

        return new RepositoryMetadata(
                projectType,
                springboot
        );
    }
}
