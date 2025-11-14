package com.yujiyamamoto64.java_spring_load_test.payment;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

@Service
public class PaymentService {

	private final InMemoryAccountStore accountStore;
	private final Instant startInstant = Instant.now();
	private final LongAdder totalRequests = new LongAdder();
	private final LongAdder successfulRequests = new LongAdder();
	private final LongAdder rejectedRequests = new LongAdder();
	private final LongAdder totalVolumeInCents = new LongAdder();
	private final LongAdder totalLatencyMicros = new LongAdder();
	private final Duration idempotencyTtl;
	private final ConcurrentMap<String, CachedResponse> idempotencyCache = new ConcurrentHashMap<>();

	public PaymentService(InMemoryAccountStore accountStore,
			@Value("${payments.idempotency-ttl-seconds:120}") long idempotencyTtlSeconds) {
		this.accountStore = accountStore;
		this.idempotencyTtl = Duration.ofSeconds(idempotencyTtlSeconds);
	}

	public Mono<TransferResponse> transfer(TransferRequest request) {
		return Mono.defer(() -> {
			var cached = idempotencyCache.get(request.idempotencyKey());
			var now = System.nanoTime();
			if (cached != null && !cached.isExpired(now)) {
				return Mono.just(cached.response());
			}

			var response = executeTransfer(request, now);
			idempotencyCache.put(request.idempotencyKey(),
					new CachedResponse(response, now + idempotencyTtl.toNanos()));
			return Mono.just(response);
		});
	}

	public Mono<TransferStats> stats() {
		return Mono.fromSupplier(this::snapshot);
	}

	private TransferResponse executeTransfer(TransferRequest request, long startTimeNanos) {
		totalRequests.increment();

		var sourceAccount = accountStore.accountOf(request.fromAccount());
		var targetAccount = accountStore.accountOf(request.toAccount());
		TransferStatus status;
		String message;
		long sourceBalanceAfter;
		long targetBalanceAfter;

		if (!sourceAccount.tryDebit(request.amountInCents())) {
			status = TransferStatus.FAILED_INSUFFICIENT_FUNDS;
			message = "Saldo insuficiente";
			sourceBalanceAfter = sourceAccount.current();
			targetBalanceAfter = targetAccount.current();
			rejectedRequests.increment();
		}
		else {
			status = TransferStatus.COMPLETED;
			message = "Transferencia concluida";
			targetBalanceAfter = targetAccount.credit(request.amountInCents());
			sourceBalanceAfter = sourceAccount.current();
			successfulRequests.increment();
			totalVolumeInCents.add(request.amountInCents());
		}

		var processingMicros = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - startTimeNanos);
		totalLatencyMicros.add(processingMicros);

		return new TransferResponse(
				request.idempotencyKey(),
				status,
				message,
				processingMicros,
				sourceBalanceAfter,
				targetBalanceAfter,
				request.amountInCents());
	}

	private TransferStats snapshot() {
		cleanExpiredIdempotencyEntries();

		var uptimeSeconds = Math.max(1,
				Duration.between(startInstant, Instant.now()).getSeconds());
		var total = totalRequests.sum();
		var avgLatency = total == 0 ? 0 : (double) totalLatencyMicros.sum() / total;
		var rps = total == 0 ? 0 : (double) total / uptimeSeconds;

		return new TransferStats(
				successfulRequests.sum(),
				rejectedRequests.sum(),
				totalVolumeInCents.sum(),
				avgLatency,
				rps,
				rps * 60,
				uptimeSeconds,
				idempotencyCache.size(),
				accountStore.totalAccounts());
	}

	private void cleanExpiredIdempotencyEntries() {
		var threshold = System.nanoTime();
		idempotencyCache.entrySet().removeIf(entry -> entry.getValue().isExpired(threshold));
	}

	private record CachedResponse(TransferResponse response, long expiresAtNanos) {
		CachedResponse {
			Objects.requireNonNull(response, "response");
		}

		boolean isExpired(long threshold) {
			return threshold >= expiresAtNanos;
		}
	}
}
