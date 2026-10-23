package com.mizigo.cargo_backend.repository;

import com.mizigo.cargo_backend.model.Complaint;
import com.mizigo.cargo_backend.model.ComplaintStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    List<Complaint> findAllByOrderByCreatedAtDesc();
    List<Complaint> findByStatusOrderByCreatedAtDesc(ComplaintStatus status);
    List<Complaint> findByReporterIdOrderByCreatedAtDesc(Long reporterId);
}
