package com.example.bugtracker.web;

import com.example.bugtracker.domain.IssueStatus;
import com.example.bugtracker.domain.IssueType;
import com.example.bugtracker.domain.Priority;
import com.example.bugtracker.security.CurrentUser;
import com.example.bugtracker.service.IssueService;
import com.example.bugtracker.web.dto.IssueDtos.CommentResponse;
import com.example.bugtracker.web.dto.IssueDtos.CreateCommentRequest;
import com.example.bugtracker.web.dto.IssueDtos.CreateIssueRequest;
import com.example.bugtracker.web.dto.IssueDtos.IssueDetailResponse;
import com.example.bugtracker.web.dto.IssueDtos.IssueResponse;
import com.example.bugtracker.web.dto.IssueDtos.UpdateIssueRequest;
import com.example.bugtracker.web.dto.IssueDtos.UpdateStatusRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/issues — the board, the list view and the detail drawer.
 *
 * <p>Scoping happens in the repository query, driven by the caller's visible
 * project IDs. This controller never filters a result set.
 */
@RestController
@RequestMapping("/api/issues")
public class IssueController {

    private final IssueService issueService;
    private final CurrentUser currentUser;

    public IssueController(IssueService issueService, CurrentUser currentUser) {
        this.issueService = issueService;
        this.currentUser = currentUser;
    }

    /**
     * Scoped list. Every filter is optional:
     * {@code ?status=&priority=&type=&projectId=&assigneeId=&q=}
     */
    @GetMapping
    public List<IssueResponse> list(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) IssueStatus status,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) IssueType type,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) String q) {
        return issueService.list(currentUser.require(), projectId, status, priority, type, assigneeId, q);
    }

    /** Issue plus its comments and audit trail. */
    @GetMapping("/{id}")
    public IssueDetailResponse get(@PathVariable Long id) {
        return issueService.getDetail(id, currentUser.require());
    }

    /** REPORTER and above. The creator is recorded as the reporter. */
    @PostMapping
    public ResponseEntity<IssueResponse> create(@Valid @RequestBody CreateIssueRequest request) {
        IssueResponse created = issueService.create(request, currentUser.require());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Partial update — omitted fields are left alone. Status, priority and
     * assignee changes require DEVELOPER; other fields accept a REPORTER editing
     * their own issue.
     */
    @PutMapping("/{id}")
    public IssueResponse update(@PathVariable Long id, @Valid @RequestBody UpdateIssueRequest request) {
        return issueService.update(id, request, currentUser.require());
    }

    /** Status-only, for Kanban drag-and-drop. DEVELOPER and above. */
    @PatchMapping("/{id}/status")
    public IssueResponse updateStatus(@PathVariable Long id,
                                      @Valid @RequestBody UpdateStatusRequest request) {
        return issueService.updateStatus(id, request.status(), currentUser.require());
    }

    /** MAINTAINER and above. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        issueService.delete(id, currentUser.require());
        return ResponseEntity.noContent().build();
    }

    // ---- comments -----------------------------------------------------------

    @GetMapping("/{id}/comments")
    public List<CommentResponse> listComments(@PathVariable Long id) {
        return issueService.listComments(id, currentUser.require());
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<CommentResponse> addComment(@PathVariable Long id,
                                                      @Valid @RequestBody CreateCommentRequest request) {
        CommentResponse comment = issueService.addComment(id, request, currentUser.require());
        return ResponseEntity.status(HttpStatus.CREATED).body(comment);
    }
}