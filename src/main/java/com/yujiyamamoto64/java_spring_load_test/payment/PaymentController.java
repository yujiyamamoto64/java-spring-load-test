package com.yujiyamamoto64.java_spring_load_test.payment;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/transfers")
public class PaymentController {

	private final PaymentService paymentService;

	public PaymentController(PaymentService paymentService) {
		this.paymentService = paymentService;
	}

	@PostMapping
	public Mono<ResponseEntity<TransferResponse>> transfer(@Valid @RequestBody TransferRequest request) {
		return paymentService.transfer(request)
			.map(response -> {
				if (response.status() == TransferStatus.COMPLETED) {
					return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
				}
				return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
			});
	}

	@GetMapping("/stats")
	public Mono<TransferStats> stats() {
		return paymentService.stats();
	}
}
