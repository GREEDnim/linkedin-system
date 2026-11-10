package com.linkedin.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * The dev profile is required, not cosmetic: SecretGuard refuses to boot on the committed
 * development signing key unless it is active, and a context test with no profile is exactly
 * the "forgot to configure it" deployment the guard exists to catch.
 */
@SpringBootTest
@ActiveProfiles("dev")
class ApiGatewayApplicationTests {

	@Test
	void contextLoads() {
	}

}
