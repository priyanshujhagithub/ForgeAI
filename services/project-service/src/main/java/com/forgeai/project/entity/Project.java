package com.forgeai.project.entity;
// Project
//    ↓
//how our application represents database data
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name= "projects")
@Getter
@Setter
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name= "user_id", nullable=false)
    private Long userId;

    @Column(name= "name", nullable=false, length=255)
    private String name;

    @Column(name= "description", nullable=false)
    private String description;

    @Column(name= "created_at", nullable=false)
    private LocalDateTime createdAt;

    @Column(name= "updated_at", nullable=false)
    private LocalDateTime updatedAt;

    @Column(name = "github_repo_url", length = 500)
    private String githubRepoUrl;

    @Column(name = "github_repo_name", length = 500)
    private String githubRepoName;

    @Column(name = "github_owner", length = 255)
    private String githubOwner;
    
    @PrePersist
    protected void onCreate(){
        LocalDateTime now=LocalDateTime.now();
        createdAt=now;
        updatedAt=now;
    }

    @PreUpdate
    protected void onUpdate(){
        updatedAt=LocalDateTime.now();
    }
}
