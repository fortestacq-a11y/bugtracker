package com.example.bugtracker.repository;

import com.example.bugtracker.domain.Project;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByProjectKeyIgnoreCase(String projectKey);

    boolean existsByProjectKeyIgnoreCase(String projectKey);

    /**
     * Scoped read: the database only ever returns projects the caller can see.
     * {@code AccessControlService} short-circuits the ADMIN case before calling,
     * because an empty IN () list matches nothing — passing an empty set here
     * correctly means "no projects visible", not "all projects".
     */
    @Query("select p from Project p where p.id in :visibleIds order by p.name")
    List<Project> findAllVisible(@Param("visibleIds") Set<Long> visibleIds);
}