package com.yujiyamamoto64.java_spring_load_test.payment;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class AccountPersistenceService {

	private final NamedParameterJdbcTemplate jdbcTemplate;
	private final TransactionTemplate transactionTemplate;
	private final long defaultBalanceInCents;

	AccountPersistenceService(NamedParameterJdbcTemplate jdbcTemplate,
			PlatformTransactionManager txManager,
			@Value("${payments.default-balance-in-cents:500000000}") long defaultBalanceInCents) {
		this.jdbcTemplate = jdbcTemplate;
		this.transactionTemplate = new TransactionTemplate(txManager);
		this.transactionTemplate.setTimeout((int) Duration.ofSeconds(5).getSeconds());
		this.defaultBalanceInCents = defaultBalanceInCents;
	}

	TransferRecord processTransfer(TransferRequest request) {
		var existing = findTransfer(request.idempotencyKey());
		if (existing != null) {
			return existing;
		}

		return transactionTemplate.execute(status -> executeNewTransfer(request));
	}

	private TransferRecord executeNewTransfer(TransferRequest request) {
		var start = System.nanoTime();
		ensureAccountExists(request.fromAccount());
		ensureAccountExists(request.toAccount());

		var debitParams = Map.of(
				"id", request.fromAccount(),
				"amount", request.amountInCents());

		var debited = jdbcTemplate.update(
				"""
						UPDATE accounts
						SET balance_in_cents = balance_in_cents - :amount,
						    updated_at = CURRENT_TIMESTAMP
						WHERE id = :id AND balance_in_cents >= :amount
						""",
				debitParams);

		final TransferStatus status;
		final String message;

		if (debited == 0) {
			status = TransferStatus.FAILED_INSUFFICIENT_FUNDS;
			message = "Saldo insuficiente";
		}
		else {
			status = TransferStatus.COMPLETED;
			message = "Transferencia concluida";

			var creditParams = Map.of(
					"id", request.toAccount(),
					"amount", request.amountInCents());

			jdbcTemplate.update(
					"""
							UPDATE accounts
							SET balance_in_cents = balance_in_cents + :amount,
							    updated_at = CURRENT_TIMESTAMP
							WHERE id = :id
							""",
					creditParams);
		}

		var fromBalanceAfter = currentBalance(request.fromAccount());
		var toBalanceAfter = currentBalance(request.toAccount());
		var processingMicros = (System.nanoTime() - start) / 1_000;

		saveTransfer(request, status, message, processingMicros, fromBalanceAfter, toBalanceAfter);

		return new TransferRecord(
				request.idempotencyKey(),
				status,
				message,
				processingMicros,
				fromBalanceAfter,
				toBalanceAfter,
				request.amountInCents());
	}

	private void ensureAccountExists(String accountId) {
		var params = Map.of(
				"id", accountId,
				"balance", defaultBalanceInCents);

		jdbcTemplate.update(
				"""
						INSERT INTO accounts(id, balance_in_cents)
						VALUES(:id, :balance)
						ON CONFLICT (id) DO NOTHING
						""",
				params);
	}

	private long currentBalance(String accountId) {
		return jdbcTemplate.queryForObject(
				"SELECT balance_in_cents FROM accounts WHERE id = :id",
				Map.of("id", accountId),
				Long.class);
	}

	private void saveTransfer(TransferRequest request, TransferStatus status, String message,
			long processingMicros, long fromBalanceAfter, long toBalanceAfter) {
		var keyHolder = new GeneratedKeyHolder();

		var params = new MapSqlParameterSource()
			.addValue("key", request.idempotencyKey())
			.addValue("fromAccount", request.fromAccount())
			.addValue("toAccount", request.toAccount())
			.addValue("amount", request.amountInCents())
			.addValue("status", status.name())
			.addValue("message", message)
			.addValue("processingMicros", processingMicros)
			.addValue("fromBalanceAfter", fromBalanceAfter)
			.addValue("toBalanceAfter", toBalanceAfter);

		jdbcTemplate.update(
				"""
						INSERT INTO transfers(idempotency_key, from_account, to_account, amount_in_cents,
						                      status, message, processing_micros,
						                      from_balance_after, to_balance_after)
						VALUES(:key, :fromAccount, :toAccount, :amount, :status, :message,
						       :processingMicros, :fromBalanceAfter, :toBalanceAfter)
						""",
				params, keyHolder);
	}

	private TransferRecord findTransfer(String idempotencyKey) {
		var results = jdbcTemplate.query(
				"""
						SELECT idempotency_key, status, message, processing_micros,
						       from_balance_after, to_balance_after, amount_in_cents
						FROM transfers WHERE idempotency_key = :key
						""",
				Map.of("key", idempotencyKey),
				(rs, rowNum) -> new TransferRecord(
						rs.getString("idempotency_key"),
						TransferStatus.valueOf(rs.getString("status")),
						rs.getString("message"),
						rs.getLong("processing_micros"),
						rs.getLong("from_balance_after"),
						rs.getLong("to_balance_after"),
						rs.getLong("amount_in_cents")));

		return results.isEmpty() ? null : results.getFirst();
	}

	long totalAccounts() {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM accounts", Map.of(), Long.class);
	}

	record TransferRecord(
			String transferId,
			TransferStatus status,
			String message,
			long processingMicros,
			long fromBalanceAfter,
			long toBalanceAfter,
			long amountInCents) {
	}
}
