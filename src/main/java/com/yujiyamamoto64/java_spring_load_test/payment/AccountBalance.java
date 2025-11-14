package com.yujiyamamoto64.java_spring_load_test.payment;

import java.util.concurrent.atomic.AtomicLong;

final class AccountBalance {

	private final String accountId;
	private final AtomicLong balanceInCents;

	AccountBalance(String accountId, long initialBalanceInCents) {
		this.accountId = accountId;
		this.balanceInCents = new AtomicLong(initialBalanceInCents);
	}

	boolean tryDebit(long amountInCents) {
		long current;
		do {
			current = balanceInCents.get();
			if (current < amountInCents) {
				return false;
			}
		}
		while (!balanceInCents.compareAndSet(current, current - amountInCents));
		return true;
	}

	long credit(long amountInCents) {
		return balanceInCents.addAndGet(amountInCents);
	}

	long current() {
		return balanceInCents.get();
	}

	String accountId() {
		return accountId;
	}
}
