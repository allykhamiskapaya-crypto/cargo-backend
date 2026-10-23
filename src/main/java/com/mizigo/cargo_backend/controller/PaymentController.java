package com.mizigo.cargo_backend.controller;

import com.mizigo.cargo_backend.model.Payment;
import com.mizigo.cargo_backend.model.PaymentMethod;
import com.mizigo.cargo_backend.model.PaymentStatus;
import com.mizigo.cargo_backend.model.PayoutStatus;
import com.mizigo.cargo_backend.model.Shipment;
import com.mizigo.cargo_backend.model.ShipmentStatus;
import com.mizigo.cargo_backend.model.UserRole;
import com.mizigo.cargo_backend.repository.PaymentRepository;
import com.mizigo.cargo_backend.repository.ShipmentRepository;
import com.mizigo.cargo_backend.security.SecurityIdentity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentRepository paymentRepository;
    private final ShipmentRepository shipmentRepository;

    public PaymentController(PaymentRepository paymentRepository,
            ShipmentRepository shipmentRepository) {
        this.paymentRepository = paymentRepository;
        this.shipmentRepository = shipmentRepository;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ResponseEntity<?> createPayment(@Valid @RequestBody PaymentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Shipment shipment = shipmentRepository.findById(request.shipmentId()).orElse(null);
        if (shipment == null) return ResponseEntity.notFound().build();
        if (!canPayForShipment(jwt, shipment)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (shipment.getPrice() == null || shipment.getPrice().signum() <= 0
                || request.amount().compareTo(shipment.getPrice()) != 0) {
            return ResponseEntity.badRequest().body("Payment amount must match shipment price");
        }
        if (paymentRepository.findByShipmentId(shipment.getId()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Payment already exists");
        }

        Payment payment = new Payment();
        payment.setShipment(shipment);
        payment.setAmount(shipment.getPrice());
        payment.setMethod(request.method());
        payment.setStatus(PaymentStatus.PENDING);
        if (shipment.getStatus() == com.mizigo.cargo_backend.model.ShipmentStatus.DELIVERED) {
            payment.setPayoutStatus(PayoutStatus.WAITING_FOR_PAYMENT);
        }
        return ResponseEntity.ok(paymentRepository.save(payment));
    }

    @GetMapping("/shipment/{shipmentId}")
    public ResponseEntity<?> getPaymentByShipment(@PathVariable Long shipmentId,
            @AuthenticationPrincipal Jwt jwt) {
        Shipment shipment = shipmentRepository.findById(shipmentId).orElse(null);
        if (shipment == null) return ResponseEntity.notFound().build();
        if (!canViewPayment(jwt, shipment)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return paymentRepository.findByShipmentId(shipmentId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{paymentId}/confirm")
    public ResponseEntity<?> confirmPayment(@PathVariable Long paymentId,
            @AuthenticationPrincipal Jwt jwt) {
        return updatePaymentStatus(paymentId, jwt, PaymentStatus.PAID);
    }

    @PutMapping("/{paymentId}/fail")
    public ResponseEntity<?> failPayment(@PathVariable Long paymentId,
            @AuthenticationPrincipal Jwt jwt) {
        return updatePaymentStatus(paymentId, jwt, PaymentStatus.FAILED);
    }

    private ResponseEntity<?> updatePaymentStatus(Long paymentId, Jwt jwt,
            PaymentStatus status) {
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null) return ResponseEntity.notFound().build();
        Shipment shipment = payment.getShipment();
        if (!canViewPayment(jwt, shipment)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Payment is not pending");
        }

        payment.setStatus(status);
        if (status == PaymentStatus.PAID) {
            payment.setPaidAt(LocalDateTime.now());
            payment.setPayoutStatus(shipment.getStatus() == ShipmentStatus.DELIVERED
                    ? PayoutStatus.READY_FOR_PAYOUT
                    : PayoutStatus.WAITING_FOR_DELIVERY);
        } else {
            payment.setPayoutStatus(PayoutStatus.FAILED);
        }
        return ResponseEntity.ok(paymentRepository.save(payment));
    }

    private boolean canPayForShipment(Jwt jwt, Shipment shipment) {
        return SecurityIdentity.isAdmin(jwt)
                || (SecurityIdentity.hasRole(jwt, UserRole.CUSTOMER.name())
                && shipment.getCustomer().getId().equals(SecurityIdentity.userId(jwt)));
    }

    private boolean canViewPayment(Jwt jwt, Shipment shipment) {
        return SecurityIdentity.isAdmin(jwt)
                || (SecurityIdentity.hasRole(jwt, UserRole.CUSTOMER.name())
                && shipment.getCustomer().getId().equals(SecurityIdentity.userId(jwt)));
    }

    public record PaymentRequest(
            @NotNull Long shipmentId,
            @NotNull @Positive java.math.BigDecimal amount,
            @NotNull PaymentMethod method) {}
}