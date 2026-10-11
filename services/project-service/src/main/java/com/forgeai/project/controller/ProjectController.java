package com.forgeai.project.controller;

import com.forgeai.project.dto.ConnectGitHubRepositoryRequest;
import com.forgeai.project.dto.CreateProjectRequest;
import com.forgeai.project.dto.ProjectResponse;
import com.forgeai.project.entity.Project;
import com.forgeai.project.mapper.ProjectMapper;
import com.forgeai.project.security.AuthenticatedUser;
import com.forgeai.project.service.ProjectService;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {
    private final ProjectService projectService;
    private final ProjectMapper projectMapper;
    public ProjectController(ProjectService projectService, ProjectMapper projectMapper) {
        this.projectService = projectService;
        this.projectMapper = projectMapper;
    }

    @PostMapping
    public ProjectResponse createProject(
            @Valid @RequestBody CreateProjectRequest request
    ){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        var project= projectService.createProject(request,user.userId());
        return projectMapper.toResponse(project);
    }
    @GetMapping("/{id}")
    public ProjectResponse getProject(@PathVariable Long id){

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();

        var project= projectService.getProject(id, user.userId());
        return projectMapper.toResponse(project);
    }
    @GetMapping
    public List<ProjectResponse> getProjects(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AuthenticatedUser user = (AuthenticatedUser)authentication.getPrincipal();

        return projectService.getProjectsForUser(user.userId())
                .stream()
                .map(projectMapper::toResponse)
                .toList();
    }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProject(@PathVariable Long id){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AuthenticatedUser user = (AuthenticatedUser)authentication.getPrincipal();
        projectService.deleteProject(id,user.userId());
    }

    @PostMapping("/{id}/github")
    public ProjectResponse connectGitHubRepository(
            @PathVariable Long id,
            @Valid @RequestBody ConnectGitHubRepositoryRequest request
            )
    {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AuthenticatedUser user = (AuthenticatedUser)authentication.getPrincipal();
        var project=projectService.connectGitHubRepository(
                id,
                user.userId(),
                request.getOwner(),
                request.getRepository()
        );
        return projectMapper.toResponse(project);
    }
}
