package com.mizigo.cargo_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mizigo.cargo_backend.model.CommissionPayment;
import com.mizigo.cargo_backend.model.DriverProfile;
import com.mizigo.cargo_backend.model.Shipment;
import com.mizigo.cargo_backend.model.ShipmentStatus;
import com.mizigo.cargo_backend.model.User;
import com.mizigo.cargo_backend.repository.CommissionPaymentRepository;
import com.mizigo.cargo_backend.repository.DriverProfileRepository;
import com.mizigo.cargo_backend.repository.ShipmentRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DriverCommissionServiceTest {

    private final DriverProfileRepository driverProfileRepository = mock(DriverProfileRepository.class);
    private final CommissionPaymentRepository commissionPaymentRepository = mock(CommissionPaymentRepository.class);
    private final ShipmentRepository shipmentRepository = mock(ShipmentRepository.class);
    private final DriverCommissionService service = new DriverCommissionService(
            driverProfileRepository, commissionPaymentRepository, shipmentRepository);

    @Test
    void backfillsAllDeliveredJobsAndRestrictsAtThree() {
        DriverProfile profile = profile(12L);
        List<Shipment> delivered = List.of(
                shipment(1L, "200000.00", LocalDateTime.now().minusDays(3)),
                shipment(2L, "100000.00", LocalDateTime.now().minusDays(2)),
                shipment(3L, "50000.00", LocalDateTime.now().minusDays(1)));
        when(driverProfileRepository.findByDriverIdForUpdate(12L)).thenReturn(Optional.of(profile));
        when(commissionPaymentRepository.findFirstByDriverProfileIdAndVerifiedTrueOrderByVerifiedAtDesc(72L))
                .thenReturn(Optional.empty());
        when(shipmentRepository.findByDriverIdAndStatus(12L, ShipmentStatus.DELIVERED)).thenReturn(delivered);
        when(driverProfileRepository.save(profile)).thenReturn(profile);

        DriverProfile reconciled = service.reconcile(12L);

        assertEquals(3, reconciled.getUnpaidJobCount());
        assertEquals(35000.0, reconciled.getCommissionBalance());
        assertTrue(reconciled.isRestricted());
    }

    @Test
    void recalculatesOnlyDeliveriesAfterLatestVerifiedCommissionPayment() {
        DriverProfile profile = profile(12L);
        LocalDateTime verifiedAt = LocalDateTime.now().minusDays(1);
        CommissionPayment payment = new CommissionPayment();
        payment.setVerifiedAt(verifiedAt);
        List<Shipment> delivered = List.of(
                shipment(1L, "200000.00", verifiedAt.minusHours(1)),
                shipment(2L, "120000.00", verifiedAt.plusHours(1)));
        when(driverProfileRepository.findByDriverIdForUpdate(12L)).thenReturn(Optional.of(profile));
        when(commissionPaymentRepository.findFirstByDriverProfileIdAndVerifiedTrueOrderByVerifiedAtDesc(72L))
                .thenReturn(Optional.of(payment));
        when(shipmentRepository.findByDriverIdAndStatus(12L, ShipmentStatus.DELIVERED)).thenReturn(delivered);
        when(driverProfileRepository.save(profile)).thenReturn(profile);

        DriverProfile reconciled = service.reconcile(12L);

        assertEquals(1, reconciled.getUnpaidJobCount());
        assertEquals(12000.0, reconciled.getCommissionBalance());
        assertFalse(reconciled.isRestricted());
    }

    private DriverProfile profile(Long driverId) {
        User driver = new User();
        driver.setId(driverId);
        DriverProfile profile = new DriverProfile();
        profile.setId(72L);
        profile.setDriver(driver);
        profile.setMaxUnpaidJobs(3);
        return profile;
    }

    private Shipment shipment(Long id, String price, LocalDateTime deliveredAt) {
        Shipment shipment = new Shipment();
        shipment.setId(id);
        shipment.setStatus(ShipmentStatus.DELIVERED);
        shipment.setPrice(new BigDecimal(price));
        shipment.setDeliveredAt(deliveredAt);
        return shipment;
    }
}