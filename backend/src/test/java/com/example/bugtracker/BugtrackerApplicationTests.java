package com.example.bugtracker;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Context-loads smoke test.
 *
 * <p>Decision: {@code @ActiveProfiles("test")} is required here. Without it this
 * test resolves the default {@code application.yml} datasource, which points at the
 * real MySQL on localhost:3306 — so {@code mvn test} would fail (or silently require
 * a running database) on any machine without the container up. With the profile it
 * uses the in-memory H2 config and the suite stays self-contained, which is what the
 * H2 test dependency and {@code application-test.yml} were both added for.
 */
@SpringBootTest
@ActiveProfiles("test")
class BugtrackerApplicationTests {

	@Test
	void contextLoads() {
	}

}
