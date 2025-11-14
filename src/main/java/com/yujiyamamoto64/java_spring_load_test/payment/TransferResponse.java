package com.yujiyamamoto64.java_spring_load_test.payment;

public record TransferResponse(
		String transferId,
		TransferStatus status,
		String message,
		long processingMicros,
		long fromBalanceInCents,
		long toBalanceInCents,
		long amountInCents) {
}
