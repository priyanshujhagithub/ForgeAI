package com.forgeai.project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ProjectServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProjectServiceApplication.class, args);
	}
}
// DTO     = API representation
//Entity  = domain/persistence representation
//Mapper  = converts between representations
//Repo    = persistence
//Service = business/use-case logic
//Controller = HTTP


//                  REQUEST DIRECTION
//CLIENT
//  ↓
//CreateProjectRequest
//  ↓
//ProjectMapper.toEntity()
//  ↓
//Project
//  ↓
//ProjectService
//  ↓
//ProjectRepository
//  ↓
//DATABASE
//
//
//                 RESPONSE DIRECTION
//DATABASE
//  ↓
//Project
//  ↓
//ProjectMapper.toResponse()
//  ↓
//ProjectResponse
//  ↓
//CLIENT