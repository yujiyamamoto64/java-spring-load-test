package com.yujiyamamoto64.java_spring_load_test.payment;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class PaymentService {

	private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
	private static final long LOG_CHUNK = 100_000;

	private final AccountPersistenceService persistenceService;
	private final Instant startInstant = Instant.now();
	private final LongAdder totalRequests = new LongAdder();
	private final LongAdder successfulRequests = new LongAdder();
	private final LongAdder rejectedRequests = new LongAdder();
	private final LongAdder totalVolumeInCents = new LongAdder();
	private final LongAdder totalLatencyMicros = new LongAdder();
	private final AtomicLong nextLogAt = new AtomicLong(LOG_CHUNK);

	public PaymentService(AccountPersistenceService persistenceService) {
		this.persistenceService = persistenceService;
	}

	public Mono<TransferResponse> transfer(TransferRequest request) {
		return Mono.fromCallable(() -> executeTransfer(request))
			.subscribeOn(Schedulers.boundedElastic());
	}

	public Mono<TransferStats> stats() {
		return Mono.fromSupplier(this::snapshot);
	}

	private TransferResponse executeTransfer(TransferRequest request) {
		totalRequests.increment();

		var record = persistenceService.processTransfer(request);
		if (record.status() == TransferStatus.COMPLETED) {
			successfulRequests.increment();
			totalVolumeInCents.add(record.amountInCents());
		}
		else {
			rejectedRequests.increment();
		}

		totalLatencyMicros.add(record.processingMicros());
		logProgressIfNeeded();

		return new TransferResponse(
				record.transferId(),
				record.status(),
				record.message(),
				record.processingMicros(),
				record.fromBalanceAfter(),
				record.toBalanceAfter(),
				record.amountInCents());
	}

	private TransferStats snapshot() {
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
				0,
				persistenceService.totalAccounts());
	}

	private void logProgressIfNeeded() {
		var target = nextLogAt.get();
		var processed = totalRequests.sum();
		if (processed >= target && nextLogAt.compareAndSet(target, target + LOG_CHUNK)) {
			var success = successfulRequests.sum();
			var failed = rejectedRequests.sum();
			log.info("{} requisicoes - success: {} - failed: {}", processed, success, failed);
		}
	}
}
