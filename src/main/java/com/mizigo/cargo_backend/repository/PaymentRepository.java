package com.mizigo.cargo_backend.repository;

import com.mizigo.cargo_backend.model.Payment;
import com.mizigo.cargo_backend.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByShipmentId(Long shipmentId);
    List<Payment> findByStatus(PaymentStatus status);
}