package com.mizigo.cargo_backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.mizigo.cargo_backend.config.CorsConfig;
import com.mizigo.cargo_backend.config.SecurityConfig;
import com.mizigo.cargo_backend.model.Shipment;
import com.mizigo.cargo_backend.model.ShipmentStatus;
import com.mizigo.cargo_backend.model.TrackingHistory;
import com.mizigo.cargo_backend.model.DriverApprovalStatus;
import com.mizigo.cargo_backend.model.DriverProfile;
import com.mizigo.cargo_backend.model.NotificationPriority;
import com.mizigo.cargo_backend.model.User;
import com.mizigo.cargo_backend.model.UserRole;
import com.mizigo.cargo_backend.model.Vehicle;
import com.mizigo.cargo_backend.security.DeliveryCodeService;
import com.mizigo.cargo_backend.service.AdminAuditService;
import com.mizigo.cargo_backend.service.AdminNotificationService;
import com.mizigo.cargo_backend.service.DriverCommissionService;
import com.mizigo.cargo_backend.repository.ShipmentRepository;
import com.mizigo.cargo_backend.repository.TrackingHistoryRepository;
import com.mizigo.cargo_backend.repository.PaymentRepository;
import com.mizigo.cargo_backend.repository.DriverProfileRepository;
import com.mizigo.cargo_backend.repository.UserRepository;
import com.mizigo.cargo_backend.repository.VehicleRepository;
import com.mizigo.cargo_backend.security.DeliveryCodeService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;

@WebMvcTest(ShipmentController.class)
@Import({SecurityConfig.class, CorsConfig.class})
@TestPropertySource(properties = "app.security.jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
class ShipmentSecurityTest {

    private static final String TEST_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockBean
    private ShipmentRepository shipmentRepository;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private TrackingHistoryRepository trackingHistoryRepository;

    @MockBean
    private VehicleRepository vehicleRepository;

    @MockBean
    private PaymentRepository paymentRepository;

    @MockBean
    private DriverProfileRepository driverProfileRepository;

    @MockBean
    private DeliveryCodeService deliveryCodeService;

    @MockBean
    private AdminAuditService auditService;

    @MockBean
    private AdminNotificationService notificationService;

    @MockBean
    private DriverCommissionService driverCommissionService;

    @Test
    void shipmentEndpointsRejectRequestsWithoutBearerToken() throws Exception {
        mockMvc.perform(get("/api/shipments/customer/17"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCanReadOwnShipmentList() throws Exception {
        Shipment shipment = shipment(76L, 17L, 28L, ShipmentStatus.ASSIGNED);
        when(shipmentRepository.findByCustomerId(17L)).thenReturn(List.of(shipment));

        mockMvc.perform(get("/api/shipments/customer/17")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(17L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(76L))
                .andExpect(jsonPath("$[0].customer.id").value(17L))
                .andExpect(jsonPath("$[0].driver.id").value(28L));
    }

            @Test
            void customerCanCancelAssignedShipmentAndReleaseDriver() throws Exception {
            Shipment shipment = shipment(80L, 17L, 28L, ShipmentStatus.ASSIGNED);
            when(shipmentRepository.findByIdForUpdate(80L)).thenReturn(Optional.of(shipment));
            when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(trackingHistoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            mockMvc.perform(post("/api/shipments/80/cancel")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(17L, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty())
                .andExpect(jsonPath("$.driver.id").value(28L));

            verify(notificationService).notifyOnce(
                org.mockito.ArgumentMatchers.eq("SHIPMENT_CANCELLED"),
                org.mockito.ArgumentMatchers.eq(NotificationPriority.MEDIUM),
                org.mockito.ArgumentMatchers.contains("released"),
                org.mockito.ArgumentMatchers.eq("SHIPMENT"),
                org.mockito.ArgumentMatchers.eq("80"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("SHIPMENT_CANCELLED:80"));
            }

    @Test
    void customerCanCancelPendingShipment() throws Exception {
        Shipment shipment = shipment(82L, 17L, null, ShipmentStatus.PENDING);
        when(shipmentRepository.findByIdForUpdate(82L)).thenReturn(Optional.of(shipment));
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(trackingHistoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/shipments/82/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(17L, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty());
    }

            @Test
            void customerCannotCancelShipmentAlreadyInTransit() throws Exception {
            Shipment shipment = shipment(81L, 17L, 28L, ShipmentStatus.IN_TRANSIT);
            when(shipmentRepository.findByIdForUpdate(81L)).thenReturn(Optional.of(shipment));

            mockMvc.perform(post("/api/shipments/81/cancel")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(17L, "CUSTOMER")))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                    .string("Huwezi kughairi mzigo uliopo njiani au uliokamilika."));
            }

    @Test
    void shipmentOwnerCanReadAssignedDriverDetails() throws Exception {
        Shipment shipment = shipment(77L, 17L, 28L, ShipmentStatus.ASSIGNED);
        shipment.getDriver().setFullName("Asha Driver");
        shipment.getDriver().setPhone("+255700000028");
        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNumber("T123ABC");
        when(shipmentRepository.findById(77L)).thenReturn(Optional.of(shipment));
        when(vehicleRepository.findByDriverId(28L)).thenReturn(List.of(vehicle));

        mockMvc.perform(get("/api/shipments/77/driver-info")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(17L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverName").value("Asha Driver"))
                .andExpect(jsonPath("$.phoneNumber").value("+255700000028"))
                .andExpect(jsonPath("$.vehicleRegistrationNumber").value("T123ABC"));

        mockMvc.perform(get("/api/shipments/77/driver-info")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(18L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotReadAnotherCustomersShipmentList() throws Exception {
        mockMvc.perform(get("/api/shipments/customer/18")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(17L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shipmentCreationCalculatesPriceOnTheServer() throws Exception {
        User customer = new User();
        customer.setId(17L);
        when(userRepository.findById(17L)).thenReturn(Optional.of(customer));
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> {
            Shipment shipment = invocation.getArgument(0);
            shipment.setId(90L);
            return shipment;
        });
        when(trackingHistoryRepository.save(any(TrackingHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/shipments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(17L))
                        .contentType("application/json")
                        .content("{\"customerId\":17,\"origin\":\"Dar es Salaam\","
                            + "\"destination\":\"Mwanza\",\"cargoType\":\"Furniture\","
                            + "\"vehicleType\":\"GARI_KUBWA\","
                                + "\"weightKg\":2,\"distanceKm\":100,\"price\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(90L))
                .andExpect(jsonPath("$.distanceKm").value(100))
                .andExpect(jsonPath("$.price").value(205000));
        verify(notificationService).notifyOnce(
                "SHIPMENT_CREATED", NotificationPriority.MEDIUM,
                "Shipment 90 is available for driver assignment.",
                "SHIPMENT", "90", "/admin-dashboard?section=shipments&shipmentId=90",
                "SHIPMENT_CREATED:90");
    }

    @Test
    void shipmentCreationDefaultsWeightWhenRequestOmitsIt() throws Exception {
        User customer = new User();
        customer.setId(17L);
        when(userRepository.findById(17L)).thenReturn(Optional.of(customer));
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> {
            Shipment shipment = invocation.getArgument(0);
            shipment.setId(91L);
            return shipment;
        });
        when(trackingHistoryRepository.save(any(TrackingHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/shipments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(17L))
                        .contentType("application/json")
                        .content("{\"customerId\":17,\"origin\":\"Dar es Salaam\","
                            + "\"destination\":\"Mwanza\",\"cargoType\":\"Furniture\","
                            + "\"vehicleType\":\"KIRIKUU\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(91L))
                .andExpect(jsonPath("$.weightKg").value(1));
    }

        @Test
        void assignedDriverCanUpdateLocationForInTransitShipment() throws Exception {
        Shipment shipment = shipment(74L, 17L, 28L, ShipmentStatus.IN_TRANSIT);
        when(shipmentRepository.findById(74L)).thenReturn(Optional.of(shipment));
        when(userRepository.findById(28L)).thenReturn(Optional.of(approvedDriver(28L)));
        when(trackingHistoryRepository
            .findFirstByShipmentIdAndLatitudeIsNotNullAndLongitudeIsNotNullOrderByTimestampDesc(74L))
            .thenReturn(Optional.empty());
        when(trackingHistoryRepository.save(any(TrackingHistory.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/shipments/74/location")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(28L, "DRIVER"))
                .contentType("application/json")
                .content("{\"latitude\":-6.8,\"longitude\":39.2}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.latitude").value(-6.8))
            .andExpect(jsonPath("$.longitude").value(39.2));
        }

        @Test
        void assignedDriverCanUpdateLocationBeforeTransit() throws Exception {
        Shipment shipment = shipment(78L, 17L, 28L, ShipmentStatus.ASSIGNED);
        when(shipmentRepository.findById(78L)).thenReturn(Optional.of(shipment));
        when(userRepository.findById(28L)).thenReturn(Optional.of(approvedDriver(28L)));
        when(trackingHistoryRepository
            .findFirstByShipmentIdAndLatitudeIsNotNullAndLongitudeIsNotNullOrderByTimestampDesc(78L))
            .thenReturn(Optional.empty());
        when(trackingHistoryRepository.save(any(TrackingHistory.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/shipments/78/location")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(28L, "DRIVER"))
                .contentType("application/json")
                .content("{\"latitude\":-6.8123,\"longitude\":39.2789}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ASSIGNED"))
            .andExpect(jsonPath("$.latitude").value(-6.8123))
            .andExpect(jsonPath("$.longitude").value(39.2789));
        }

        @Test
        void customerCanReadLatestShipmentLocationButNotAnotherCustomers() throws Exception {
        Shipment shipment = shipment(75L, 17L, 28L, ShipmentStatus.IN_TRANSIT);
        TrackingHistory latestLocation = new TrackingHistory();
        latestLocation.setLatitude(new java.math.BigDecimal("-6.8"));
        latestLocation.setLongitude(new java.math.BigDecimal("39.2"));
        when(shipmentRepository.findById(75L)).thenReturn(Optional.of(shipment));
        when(trackingHistoryRepository
            .findFirstByShipmentIdAndLatitudeIsNotNullAndLongitudeIsNotNullOrderByTimestampDesc(75L))
            .thenReturn(Optional.of(latestLocation));

        mockMvc.perform(get("/api/shipments/75/location")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(17L, "CUSTOMER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.driverLatitude").value(-6.8))
            .andExpect(jsonPath("$.driverLongitude").value(39.2));

        mockMvc.perform(get("/api/shipments/75/location")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(18L, "CUSTOMER")))
            .andExpect(status().isForbidden());
        }

        @Test
        void customerReadsLatestCoordinatesPostedByDriver() throws Exception {
        Shipment shipment = shipment(79L, 17L, 28L, ShipmentStatus.IN_TRANSIT);
        AtomicReference<TrackingHistory> latestLocation = new AtomicReference<>();
        when(shipmentRepository.findById(79L)).thenReturn(Optional.of(shipment));
        when(userRepository.findById(28L)).thenReturn(Optional.of(approvedDriver(28L)));
        when(trackingHistoryRepository
            .findFirstByShipmentIdAndLatitudeIsNotNullAndLongitudeIsNotNullOrderByTimestampDesc(79L))
            .thenAnswer(invocation -> Optional.ofNullable(latestLocation.get()));
        when(trackingHistoryRepository.save(any(TrackingHistory.class)))
            .thenAnswer(invocation -> {
                TrackingHistory saved = invocation.getArgument(0);
                latestLocation.set(saved);
                return saved;
            });

        mockMvc.perform(post("/api/shipments/79/location")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(28L, "DRIVER"))
                .contentType("application/json")
                .content("{\"latitude\":-6.79,\"longitude\":39.24}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/shipments/79/location")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(17L, "CUSTOMER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.driverLatitude").value(-6.79))
            .andExpect(jsonPath("$.driverLongitude").value(39.24));
        }

            @Test
            void onlyShipmentOwnerCanReadDeliveryCode() throws Exception {
            Shipment shipment = shipment(71L, 17L, 28L, ShipmentStatus.IN_TRANSIT);
            shipment.setDeliveryOtpEncrypted("encrypted-code");
            shipment.setDeliveryOtpExpiresAt(LocalDateTime.now().plusHours(1));
            when(shipmentRepository.findById(71L)).thenReturn(Optional.of(shipment));
            when(deliveryCodeService.reveal("encrypted-code")).thenReturn("123456");

            mockMvc.perform(get("/api/shipments/71/delivery-code")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(17L, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("123456"));

            mockMvc.perform(get("/api/shipments/71/delivery-code")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(18L, "CUSTOMER")))
                .andExpect(status().isForbidden());
            }

                @Test
                void customerCanGenerateAndRepeatedlyRevealActiveDeliveryCode() throws Exception {
                Shipment shipment = shipment(76L, 17L, 28L, ShipmentStatus.ASSIGNED);
                DeliveryCodeService.IssuedCode issuedCode = new DeliveryCodeService.IssuedCode(
                    "123456", "hashed-code", "encrypted-code", LocalDateTime.now().plusHours(8));
                when(shipmentRepository.findByIdForUpdate(76L)).thenReturn(Optional.of(shipment));
                when(deliveryCodeService.issue()).thenReturn(issuedCode);
                when(deliveryCodeService.reveal("encrypted-code")).thenReturn("123456");
                when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> invocation.getArgument(0));

                mockMvc.perform(post("/api/shipments/76/delivery-code")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(17L, "CUSTOMER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("123456"));

                    shipment.setDeliveryOtpAttempts(2);
                mockMvc.perform(post("/api/shipments/76/delivery-code")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(17L, "CUSTOMER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("123456"));

                mockMvc.perform(post("/api/shipments/76/delivery-code")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(18L, "CUSTOMER")))
                    .andExpect(status().isForbidden());

                verify(deliveryCodeService).issue();
                org.junit.jupiter.api.Assertions.assertEquals(2, shipment.getDeliveryOtpAttempts());
                }

                @Test
                void deliveryCodeIsIssuedWhenShipmentEntersTransit() throws Exception {
                Shipment shipment = shipment(75L, 17L, 28L, ShipmentStatus.PICKED_UP);
                DeliveryCodeService.IssuedCode issuedCode = new DeliveryCodeService.IssuedCode(
                    "123456", "hashed-code", "encrypted-code", LocalDateTime.now().plusHours(8));
                when(userRepository.findById(28L)).thenReturn(Optional.of(approvedDriver(28L)));
                when(shipmentRepository.findByIdForUpdate(75L)).thenReturn(Optional.of(shipment));
                when(deliveryCodeService.issue()).thenReturn(issuedCode);
                when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
                when(trackingHistoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

                mockMvc.perform(put("/api/shipments/75/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(28L, "DRIVER"))
                        .contentType("application/json")
                        .content("{\"status\":\"IN_TRANSIT\",\"location\":\"On the way\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("IN_TRANSIT"))
                    .andExpect(jsonPath("$.deliveryOtpHash").doesNotExist())
                    .andExpect(jsonPath("$.deliveryOtpEncrypted").doesNotExist());

                org.junit.jupiter.api.Assertions.assertEquals("hashed-code", shipment.getDeliveryOtpHash());
                org.junit.jupiter.api.Assertions.assertEquals("encrypted-code", shipment.getDeliveryOtpEncrypted());
                }

            @Test
            void deliveryCannotCompleteWithInvalidOtp() throws Exception {
            Shipment shipment = shipment(72L, 17L, 28L, ShipmentStatus.IN_TRANSIT);
            shipment.setDeliveryOtpHash("otp-hash");
            shipment.setDeliveryOtpExpiresAt(LocalDateTime.now().plusHours(1));
            when(userRepository.findById(28L)).thenReturn(Optional.of(approvedDriver(28L)));
            when(shipmentRepository.findByIdForUpdate(72L)).thenReturn(Optional.of(shipment));
            when(deliveryCodeService.matches("000000", "otp-hash", shipment.getDeliveryOtpExpiresAt(), 0))
                .thenReturn(false);
            when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> invocation.getArgument(0));

            mockMvc.perform(put("/api/shipments/72/status")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(28L, "DRIVER"))
                    .contentType("application/json")
                    .content("{\"status\":\"DELIVERED\",\"location\":\"Current address\",\"deliveryCode\":\"000000\"}"))
                .andExpect(status().isBadRequest());

            verify(shipmentRepository).save(any(Shipment.class));
            }

        @Test
        void validOtpCompletesAssignedInTransitShipment() throws Exception {
            Shipment shipment = shipment(73L, 17L, 28L, ShipmentStatus.IN_TRANSIT);
            shipment.setDeliveryOtpHash("otp-hash");
            shipment.setDeliveryOtpExpiresAt(LocalDateTime.now().plusHours(1));
            when(userRepository.findById(28L)).thenReturn(Optional.of(approvedDriver(28L)));
            when(shipmentRepository.findByIdForUpdate(73L)).thenReturn(Optional.of(shipment));
            when(deliveryCodeService.matches("123456", "otp-hash", shipment.getDeliveryOtpExpiresAt(), 0))
                    .thenReturn(true);
            when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(trackingHistoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(paymentRepository.findByShipmentId(73L)).thenReturn(Optional.empty());

            mockMvc.perform(put("/api/shipments/73/status")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(28L, "DRIVER"))
                            .contentType("application/json")
                            .content("{\"status\":\"DELIVERED\",\"location\":\"Customer-provided address\",\"deliveryCode\":\"123456\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DELIVERED"));

            verify(paymentRepository).findByShipmentId(73L);
        }

            @Test
            void deliveredShipmentTriggersCommissionReconciliation() throws Exception {
                Shipment shipment = shipment(74L, 17L, 28L, ShipmentStatus.IN_TRANSIT);
                shipment.setPrice(new BigDecimal("200000.00"));
                shipment.setDeliveryOtpHash("otp-hash");
                shipment.setDeliveryOtpExpiresAt(LocalDateTime.now().plusHours(1));
                when(userRepository.findById(28L)).thenReturn(Optional.of(approvedDriver(28L)));
                when(shipmentRepository.findByIdForUpdate(74L)).thenReturn(Optional.of(shipment));
                when(deliveryCodeService.matches("123456", "otp-hash", shipment.getDeliveryOtpExpiresAt(), 0))
                    .thenReturn(true);
                when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
                when(trackingHistoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
                when(paymentRepository.findByShipmentId(74L)).thenReturn(Optional.empty());

                mockMvc.perform(put("/api/shipments/74/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(28L, "DRIVER"))
                        .contentType("application/json")
                        .content("{\"status\":\"DELIVERED\",\"location\":\"Drop off\",\"deliveryCode\":\"123456\"}"))
                    .andExpect(status().isOk());

                verify(driverCommissionService).reconcile(28L);
            }

    private String token(Long userId) {
        return token(userId, "CUSTOMER");
    }

    private String token(Long userId, String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("mizigo-api")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .subject("customer@example.com")
                .claim("userId", userId)
                .claim("role", role)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private Shipment shipment(Long id, Long customerId, Long driverId, ShipmentStatus status) {
        User customer = new User();
        customer.setId(customerId);
        User driver = new User();
        driver.setId(driverId);
        Shipment shipment = new Shipment();
        shipment.setId(id);
        shipment.setCustomer(customer);
        shipment.setDriver(driver);
        shipment.setStatus(status);
        return shipment;
    }

    private User approvedDriver(Long id) {
        User driver = new User();
        driver.setId(id);
        driver.setRole(UserRole.DRIVER);
        driver.setDriverApprovalStatus(DriverApprovalStatus.APPROVED);
        return driver;
    }
}
