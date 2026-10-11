package com.example.bugtracker.repository;

import com.example.bugtracker.domain.ProjectMember;
import com.example.bugtracker.domain.ProjectRole;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    Optional<ProjectMember> findByProjectIdAndUserId(Long projectId, Long userId);

    boolean existsByProjectIdAndUserId(Long projectId, Long userId);

    List<ProjectMember> findByProjectIdOrderByAddedAt(Long projectId);

    List<ProjectMember> findByUserId(Long userId);

    /**
     * The set that drives every scoped query downstream: which projects this user
     * belongs to. ADMINs bypass this entirely — see {@code AccessControlService}.
     */
    @Query("select pm.project.id from ProjectMember pm where pm.user.id = :userId")
    Set<Long> findProjectIdsForUser(@Param("userId") Long userId);

    @Query("select pm.project.id from ProjectMember pm where pm.project.id in :projectIds and pm.user.id = :userId")
    Set<Long> findProjectIdsForUserIn(@Param("userId") Long userId,
                                      @Param("projectIds") Set<Long> projectIds);

    @Query("select pm from ProjectMember pm where pm.project.id in :projectIds")
    List<ProjectMember> findAllInProjects(@Param("projectIds") Set<Long> projectIds);

    void deleteByProjectIdAndUserId(Long projectId, Long userId);

    long countByProjectIdAndProjectRole(Long projectId, ProjectRole role);

    long countByProjectId(Long projectId);
}