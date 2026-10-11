package com.example.bugtracker.repository;

import com.example.bugtracker.domain.IssueHistory;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IssueHistoryRepository extends JpaRepository<IssueHistory, Long> {

    /**
     * Scoped read, same shape as comments: the history of an issue is visible to
     * whoever can see the issue.
     */
    @Query("""
            select h from IssueHistory h
            where h.issue.project.id in :visibleProjectIds
              and (:issueId is null or h.issue.id = :issueId)
            order by h.createdAt desc
            """)
    List<IssueHistory> findVisible(@Param("visibleProjectIds") Set<Long> visibleProjectIds,
                                   @Param("issueId") Long issueId);

    void deleteByIssueId(Long issueId);
}