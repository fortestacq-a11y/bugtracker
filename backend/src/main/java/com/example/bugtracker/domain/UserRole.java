package com.example.bugtracker.domain;

/**
 * System-level role. Deliberately only two values: this column answers
 * "can this account see everything?", while everything project-shaped lives in
 * {@link ProjectRole}. Keeping them separate stops a per-project model from
 * polluting a global one.
 */
public enum UserRole {
    ADMIN,
    MEMBER
}