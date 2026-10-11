package com.example.bugtracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A project. Issues belong to exactly one project, and project membership is
 * what decides who can see them — see {@code AccessControlService}.
 *
 * <p>The human-facing prefix is stored in {@code projectKey} rather than
 * {@code key}, because {@code KEY} is a reserved word in MySQL.
 */
@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Prefix for generated issue keys, e.g. BUGTRK in BUGTRK-14. Uppercase, max 10. */
    @Column(name = "project_key", nullable = false, unique = true, length = 10)
    private String projectKey;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Project() {
        // for JPA
    }

    public Project(String projectKey, String name, String description) {
        this.projectKey = projectKey;
        this.name = name;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Project{id=%d, key='%s', name='%s'}".formatted(id, projectKey, name);
    }
}