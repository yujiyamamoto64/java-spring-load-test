package com.yujiyamamoto64.java_spring_load_test.payment;

public record TransferStats(
		long processed,
		long rejected,
		long totalVolumeInCents,
		double avgLatencyMicros,
		double requestsPerSecond,
		double requestsPerMinute,
		long uptimeSeconds,
		long cachedIdempotencyKeys,
		long provisionedAccounts) {
}
