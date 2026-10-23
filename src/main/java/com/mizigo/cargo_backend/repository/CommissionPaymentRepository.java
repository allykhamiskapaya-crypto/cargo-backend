package com.mizigo.cargo_backend.repository;

import com.mizigo.cargo_backend.model.CommissionPayment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommissionPaymentRepository extends JpaRepository<CommissionPayment, Long> {
    Optional<CommissionPayment> findFirstByDriverProfileIdAndVerifiedFalseAndRejectedFalseOrderByCreatedAtDesc(Long profileId);
    Optional<CommissionPayment> findFirstByDriverProfileIdAndVerifiedTrueOrderByVerifiedAtDesc(Long profileId);
    List<CommissionPayment> findByVerifiedFalseAndRejectedFalseOrderByCreatedAtAsc();
    boolean existsByReference(String reference);
}