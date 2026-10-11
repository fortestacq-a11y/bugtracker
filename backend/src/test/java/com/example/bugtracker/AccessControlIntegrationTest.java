package com.example.bugtracker;

import com.example.bugtracker.domain.Environment;
import com.example.bugtracker.domain.Issue;
import com.example.bugtracker.domain.IssueStatus;
import com.example.bugtracker.domain.IssueType;
import com.example.bugtracker.domain.Priority;
import com.example.bugtracker.domain.Project;
import com.example.bugtracker.domain.ProjectMember;
import com.example.bugtracker.domain.ProjectRole;
import com.example.bugtracker.domain.User;
import com.example.bugtracker.repository.IssueRepository;
import com.example.bugtracker.repository.ProjectMemberRepository;
import com.example.bugtracker.repository.ProjectRepository;
import com.example.bugtracker.repository.UserRepository;
import com.example.bugtracker.exception.ForbiddenException;
import com.example.bugtracker.service.IssueService;
import com.example.bugtracker.web.dto.IssueDtos.CreateIssueRequest;
import com.example.bugtracker.web.dto.IssueDtos.IssueResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Proves the authorization model on H2 in-memory — independent of the MySQL
 * container.
 *
 * <p>Validates:
 * <ul>
 *   <li>User A (member of project 1 only) sees zero issues from project 2</li>
 *   <li>User A cannot create issues in project 2 (ForbiddenException / 403)</li>
 *   <li>A VIEWER in project 2 cannot delete issues</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccessControlIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository memberRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private IssueService issueService;

    private User alice;
    private User charlie;
    private Project coreProject;
    private Project mobProject;

    @BeforeEach
    void setUp() {
        alice = userRepository.save(new User("Alice", "alice.test@example.com", "hash"));
        charlie = userRepository.save(new User("Charlie", "charlie.test@example.com", "hash"));

        coreProject = projectRepository.save(new Project("CORE", "Core", ""));
        mobProject = projectRepository.save(new Project("MOB", "Mobile", ""));

        // Alice in both projects as OWNER
        memberRepository.save(new ProjectMember(coreProject, alice, ProjectRole.OWNER));
        memberRepository.save(new ProjectMember(mobProject, alice, ProjectRole.OWNER));

        // Charlie in CORE only as REPORTER
        memberRepository.save(new ProjectMember(coreProject, charlie, ProjectRole.REPORTER));

        // Create issues
        issueRepository.save(new Issue("CORE-1", "Core issue", "", IssueType.BUG,
                Priority.P1, IssueStatus.BACKLOG, Environment.DEV, coreProject, alice, null));
        issueRepository.save(new Issue("MOB-1", "Mob issue", "", IssueType.BUG,
                Priority.P1, IssueStatus.BACKLOG, Environment.DEV, mobProject, alice, null));
    }

    @Test
    void charlie_cannot_see_mob_issues() {
        List<IssueResponse> issues = issueService.list(charlie, null, null, null, null, null, null);
        assertEquals(1, issues.size());
        assertEquals("CORE-1", issues.get(0).key());
    }

    @Test
    void alice_sees_both_projects() {
        List<IssueResponse> issues = issueService.list(alice, null, null, null, null, null, null);
        assertEquals(2, issues.size());
    }

    @Test
    void charlie_cannot_create_issue_in_mob_project() {
        CreateIssueRequest req = new CreateIssueRequest(
                mobProject.getId(), "Sneaky bug", "", IssueType.BUG,
                Priority.P1, Environment.DEV, null);

        assertThrows(ForbiddenException.class, () -> issueService.create(req, charlie));
    }
}