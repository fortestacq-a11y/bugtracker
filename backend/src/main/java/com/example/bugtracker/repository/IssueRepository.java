package com.example.bugtracker.repository;

import com.example.bugtracker.domain.Issue;
import com.example.bugtracker.domain.IssueStatus;
import com.example.bugtracker.domain.IssueType;
import com.example.bugtracker.domain.Priority;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IssueRepository extends JpaRepository<Issue, Long> {

    /**
     * The one query the board, the list view and the filter bar all share.
     *
     * <p>Scoping happens here, in SQL: {@code i.project.id in :visibleProjectIds}.
     * An empty set matches nothing, which is exactly right — a non-member sees an
     * empty board, not everyone's issues.
     *
     * <p>Every filter is a nullable parameter compared with {@code is null or ...},
     * so one query covers every combination instead of a combinatorial pile of
     * derived-query methods.
     */
    @Query("""
            select i from Issue i
            where i.project.id in :visibleProjectIds
              and (:projectId is null or i.project.id = :projectId)
              and (:status is null or i.status = :status)
              and (:priority is null or i.priority = :priority)
              and (:type is null or i.type = :type)
              and (:assigneeId is null or i.assignee.id = :assigneeId)
              and (:q is null or lower(i.title) like lower(concat('%', :q, '%'))
                             or lower(coalesce(i.description, '')) like lower(concat('%', :q, '%')))
            order by i.updatedAt desc
            """)
    List<Issue> findVisible(@Param("visibleProjectIds") Set<Long> visibleProjectIds,
                            @Param("projectId") Long projectId,
                            @Param("status") IssueStatus status,
                            @Param("priority") Priority priority,
                            @Param("type") IssueType type,
                            @Param("assigneeId") Long assigneeId,
                            @Param("q") String q);

    /** Single scoped read — the caller sees 404, not 403, for a project they aren't in. */
    @Query("select i from Issue i where i.id = :id and i.project.id in :visibleProjectIds")
    Optional<Issue> findVisibleById(@Param("visibleProjectIds") Set<Long> visibleProjectIds,
                                    @Param("id") Long id);

    /**
     * Highest numeric suffix among a project's issue keys, so the next one is
     * max + 1. The key format is {@code PREFIX-N}, hence the +2 for the hyphen.
     */
    @Query("select coalesce(max(cast(substring(i.issueKey, :prefixLength + 2) as integer)), 0) "
            + "from Issue i where i.project.id = :projectId")
    int findMaxIssueNumber(@Param("projectId") Long projectId, @Param("prefixLength") int prefixLength);

    long countByProjectId(Long projectId);
}