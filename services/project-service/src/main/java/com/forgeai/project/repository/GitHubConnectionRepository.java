package com.forgeai.project.repository;

//   This is our database access layer.
//
//  It handles:
//
//  GitHubConnection
//       ↓
//  PostgreSQL

import com.forgeai.project.entity.GitHubConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GitHubConnectionRepository extends JpaRepository<GitHubConnection, Long> {

    Optional<GitHubConnection> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    void deleteByUserId(Long userId);
}
