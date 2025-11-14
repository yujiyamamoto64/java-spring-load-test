package com.yujiyamamoto64.java_spring_load_test.payment;

import java.util.Locale;

import org.springframework.util.Assert;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record TransferRequest(
		@NotBlank(message = "idempotencyKey e obrigatorio") String idempotencyKey,
		@NotBlank(message = "fromAccount e obrigatorio") String fromAccount,
		@NotBlank(message = "toAccount e obrigatorio") String toAccount,
		@Positive(message = "amountInCents deve ser maior que zero") long amountInCents,
		@NotBlank(message = "currency e obrigatorio") String currency) {

	public TransferRequest {
		Assert.hasText(idempotencyKey, "idempotencyKey e obrigatorio");
		Assert.hasText(fromAccount, "fromAccount e obrigatorio");
		Assert.hasText(toAccount, "toAccount e obrigatorio");
		Assert.isTrue(!fromAccount.equals(toAccount), "fromAccount e toAccount precisam ser diferentes.");
		Assert.isTrue(amountInCents > 0, "amountInCents deve ser maior que zero");
		Assert.hasText(currency, "currency e obrigatorio");
		Assert.isTrue(currency.length() == 3, "currency deve seguir padrao ISO-4217");
		currency = currency.toUpperCase(Locale.ROOT);
	}
}
