package com.yujiyamamoto64.java_spring_load_test.payment;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import jakarta.annotation.PostConstruct;

@Component
class InMemoryAccountStore {

	private final ConcurrentMap<String, AccountBalance> accounts = new ConcurrentHashMap<>();
	private final long defaultBalanceInCents;
	private final int preloadAccounts;

	InMemoryAccountStore(
			@Value("${payments.default-balance-in-cents:500000000}") long defaultBalanceInCents,
			@Value("${payments.preload-accounts:200000}") int preloadAccounts) {
		Assert.isTrue(defaultBalanceInCents > 0, "O saldo inicial deve ser maior que zero.");
		Assert.isTrue(preloadAccounts >= 0, "Quantidade de contas pre-carregadas invalida.");
		this.defaultBalanceInCents = defaultBalanceInCents;
		this.preloadAccounts = preloadAccounts;
	}

	@PostConstruct
	void preloadAccounts() {
		if (preloadAccounts == 0) {
			return;
		}

		IntStream.range(0, preloadAccounts)
			.parallel()
			.forEach(i -> accounts.computeIfAbsent("ACC-%08d".formatted(i), this::buildAccount));
	}

	AccountBalance accountOf(String accountId) {
		Assert.hasText(accountId, "accountId nao pode ser vazio");
		return accounts.computeIfAbsent(accountId, this::buildAccount);
	}

	long totalAccounts() {
		return accounts.size();
	}

	private AccountBalance buildAccount(String accountId) {
		return new AccountBalance(accountId, defaultBalanceInCents);
	}
}
