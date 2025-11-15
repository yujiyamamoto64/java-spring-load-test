package com.yujiyamamoto64.java_spring_load_test.payment;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import reactor.test.StepVerifier;

@SpringBootTest
@TestPropertySource(properties = {
		"spring.sql.init.mode=always",
		"spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
		"spring.datasource.driverClassName=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"payments.default-balance-in-cents=1000000"
})
class PaymentServiceTest {

	@Autowired
	private PaymentService paymentService;

	@Autowired
	private NamedParameterJdbcTemplate jdbcTemplate;

	@AfterEach
	void clean() {
		jdbcTemplate.update("DELETE FROM transfers", java.util.Collections.emptyMap());
		jdbcTemplate.update("DELETE FROM accounts", java.util.Collections.emptyMap());
	}

	@Test
	void shouldTransferBetweenAccounts() {
		var request = new TransferRequest("key-1", "A-1", "A-2", 500, "brl");

		StepVerifier.create(paymentService.transfer(request))
			.assertNext(response -> {
				org.junit.jupiter.api.Assertions.assertEquals(TransferStatus.COMPLETED, response.status());
				org.junit.jupiter.api.Assertions.assertEquals(500, response.amountInCents());
			})
			.verifyComplete();
	}

	@Test
	void shouldRejectWhenBalanceIsMissing() {
		var request = new TransferRequest("key-2", "B-1", "B-2", 5_000_000, "brl");

		StepVerifier.create(paymentService.transfer(request))
			.assertNext(response -> org.junit.jupiter.api.Assertions
				.assertEquals(TransferStatus.FAILED_INSUFFICIENT_FUNDS, response.status()))
			.verifyComplete();
	}
}
