package com.forgeai.project.repository;

import com.forgeai.project.entity.AgentRun;
import com.forgeai.project.entity.AgentRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentRunRepository extends JpaRepository<AgentRun, UUID> {

    // Scoped by owner: a run that belongs to someone else is simply not found.
    Optional<AgentRun> findByIdAndUserId(UUID id, Long userId);

    List<AgentRun> findByProjectIdAndUserIdOrderByCreatedAtDesc(Long projectId, Long userId);

    boolean existsByProjectIdAndStatusIn(Long projectId, Collection<AgentRunStatus> statuses);

    List<AgentRun> findByStatusIn(Collection<AgentRunStatus> statuses);

    long countByUserIdAndCreatedAtGreaterThanEqual(Long userId, LocalDateTime since);
}
