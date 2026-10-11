package com.forgeai.project.entity;

//   This is our database entity.
//
//   It represents:
//
//   A ForgeAI user's connection to a GitHub account.

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "github_connections")
@Getter
@Setter
public class GitHubConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id",nullable=false)
    private Long userId;

    @Column(name = "github_user_id", nullable = false, length = 100)
    private Long githubUserId;

    @Column(name = "github_username", nullable = false, length = 255)
    private String githubUsername;

    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }
    @PreUpdate
    protected void onUpdate() {
        LocalDateTime now = LocalDateTime.now();
        this.updatedAt = now;
    }
}
