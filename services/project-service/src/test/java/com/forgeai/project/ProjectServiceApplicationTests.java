package com.forgeai.project;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

// Starts the whole application against an empty database: every migration
// applies in order and the JPA entities match the resulting schema.

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ProjectServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
