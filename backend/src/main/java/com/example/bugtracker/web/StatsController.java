package com.example.bugtracker.web;

import com.example.bugtracker.security.CurrentUser;
import com.example.bugtracker.service.StatsService;
import com.example.bugtracker.web.dto.StatsDtos.StatsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/stats — dashboard counters.
 *
 * <p>Every figure is scoped to the caller's visible projects, so two users in
 * different projects see genuinely different totals rather than one global number.
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;
    private final CurrentUser currentUser;

    public StatsController(StatsService statsService, CurrentUser currentUser) {
        this.statsService = statsService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public StatsResponse stats() {
        return statsService.statsFor(currentUser.require());
    }
}