package com.example.bugtracker.repository;

import com.example.bugtracker.domain.Comment;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * Scoped read: comments are readable by anyone who can read their issue, so
     * visibility is inherited from {@code issue.project.id}.
     */
    @Query("""
            select c from Comment c
            where c.issue.project.id in :visibleProjectIds
              and (:issueId is null or c.issue.id = :issueId)
            order by c.createdAt asc
            """)
    List<Comment> findVisible(@Param("visibleProjectIds") Set<Long> visibleProjectIds,
                              @Param("issueId") Long issueId);

    List<Comment> findByIssueIdOrderByCreatedAtAsc(Long issueId);

    void deleteByIssueId(Long issueId);

    long countByIssueId(Long issueId);
}