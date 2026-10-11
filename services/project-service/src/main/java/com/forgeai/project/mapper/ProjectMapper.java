package com.forgeai.project.mapper;

import com.forgeai.project.dto.CreateProjectRequest;
import com.forgeai.project.dto.ProjectResponse;
import com.forgeai.project.entity.Project;
import org.springframework.stereotype.Component;

@Component
public class ProjectMapper {
    public ProjectResponse toResponse(Project project){
        ProjectResponse response = new ProjectResponse();

        response.setId(project.getId());
        response.setUserId(project.getUserId());
        response.setName(project.getName());
        response.setDescription(project.getDescription());
        response.setGithubRepoUrl(project.getGithubRepoUrl());
        response.setGithubRepoName(project.getGithubRepoName());
        response.setGithubOwner(project.getGithubOwner());
        response.setCreatedAt(project.getCreatedAt());
        response.setUpdatedAt(project.getUpdatedAt());
        return response;
    }
    public Project toEntity(CreateProjectRequest request,Long userId){
        Project project=new Project();
        project.setUserId(userId);
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        return project;
    }
}
