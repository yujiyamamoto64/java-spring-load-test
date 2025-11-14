package com.yujiyamamoto64.java_spring_load_test.payment;

import org.junit.jupiter.api.Test;

import reactor.test.StepVerifier;

class PaymentServiceTest {

	@Test
	void shouldTransferBetweenAccounts() {
		var store = new InMemoryAccountStore(1_000_000L, 0);
		var service = new PaymentService(store, 60);
		var request = new TransferRequest("key-1", "A-1", "A-2", 500, "brl");

		StepVerifier.create(service.transfer(request))
			.assertNext(response -> {
				org.junit.jupiter.api.Assertions.assertEquals(TransferStatus.COMPLETED, response.status());
				org.junit.jupiter.api.Assertions.assertEquals(500, response.amountInCents());
			})
			.verifyComplete();
	}

	@Test
	void shouldRejectWhenBalanceIsMissing() {
		var store = new InMemoryAccountStore(100L, 0);
		var service = new PaymentService(store, 60);
		var request = new TransferRequest("key-2", "B-1", "B-2", 5_000, "brl");

		StepVerifier.create(service.transfer(request))
			.assertNext(response -> {
				org.junit.jupiter.api.Assertions.assertEquals(TransferStatus.FAILED_INSUFFICIENT_FUNDS, response.status());
			})
			.verifyComplete();
	}
}
