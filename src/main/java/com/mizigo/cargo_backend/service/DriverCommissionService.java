package com.mizigo.cargo_backend.service;

import com.mizigo.cargo_backend.model.CommissionPayment;
import com.mizigo.cargo_backend.model.DriverProfile;
import com.mizigo.cargo_backend.model.Shipment;
import com.mizigo.cargo_backend.model.ShipmentStatus;
import com.mizigo.cargo_backend.repository.CommissionPaymentRepository;
import com.mizigo.cargo_backend.repository.DriverProfileRepository;
import com.mizigo.cargo_backend.repository.ShipmentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriverCommissionService {

    private static final BigDecimal COMMISSION_RATE = new BigDecimal("0.10");

    private final DriverProfileRepository driverProfileRepository;
    private final CommissionPaymentRepository commissionPaymentRepository;
    private final ShipmentRepository shipmentRepository;

    public DriverCommissionService(DriverProfileRepository driverProfileRepository,
            CommissionPaymentRepository commissionPaymentRepository,
            ShipmentRepository shipmentRepository) {
        this.driverProfileRepository = driverProfileRepository;
        this.commissionPaymentRepository = commissionPaymentRepository;
        this.shipmentRepository = shipmentRepository;
    }

    @Transactional
    public DriverProfile reconcile(Long driverId) {
        DriverProfile profile = driverProfileRepository.findByDriverIdForUpdate(driverId).orElse(null);
        if (profile == null) return null;

        LocalDateTime paidThrough = commissionPaymentRepository
                .findFirstByDriverProfileIdAndVerifiedTrueOrderByVerifiedAtDesc(profile.getId())
                .map(CommissionPayment::getVerifiedAt)
                .orElse(null);
        BigDecimal balance = BigDecimal.ZERO.setScale(2);
        int unpaidJobs = 0;
        for (Shipment shipment : shipmentRepository.findByDriverIdAndStatus(driverId, ShipmentStatus.DELIVERED)) {
            LocalDateTime completedAt = shipment.getDeliveredAt() == null
                    ? shipment.getUpdatedAt() : shipment.getDeliveredAt();
            if (paidThrough != null && completedAt != null && !completedAt.isAfter(paidThrough)) continue;

            unpaidJobs++;
            if (shipment.getPrice() != null) {
                balance = balance.add(shipment.getPrice().multiply(COMMISSION_RATE)
                        .setScale(2, RoundingMode.HALF_UP));
            }
        }

        profile.setCommissionBalance(balance.setScale(2, RoundingMode.HALF_UP).doubleValue());
        profile.setUnpaidJobCount(unpaidJobs);
        profile.setRestricted(unpaidJobs >= profile.getMaxUnpaidJobs());
        return driverProfileRepository.save(profile);
    }
}