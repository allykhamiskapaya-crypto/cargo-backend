package com.mizigo.cargo_backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Table(name = "users")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
@Column(nullable = false)
private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.CUSTOMER;

        @Enumerated(EnumType.STRING)
        @Column(name = "customer_type", nullable = false, length = 20,
            columnDefinition = "varchar(20) default 'INDIVIDUAL'")
        private CustomerType customerType = CustomerType.INDIVIDUAL;

        @Column(name = "company_name", length = 200)
        private String companyName;

        @Column(name = "tin_number", length = 50)
        private String tinNumber;

        @Column(name = "vrn_number", length = 50)
        private String vrnNumber;

        @Column(name = "brela_number", length = 50)
        private String brelaNumber;

        @Column(name = "contact_person_name", length = 150)
        private String contactPersonName;

        @Column(name = "office_address", length = 500)
        private String officeAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "driver_approval_status")
    private DriverApprovalStatus driverApprovalStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL)
    @JsonIgnore
    private List<Shipment> shipmentsAsCustomer;

    @OneToMany(mappedBy = "driver")
    @JsonIgnore
    private List<Shipment> shipmentsAsDriver;

    @OneToMany(mappedBy = "driver", cascade = CascadeType.ALL)
    @JsonIgnore
    private List<Vehicle> vehicles;

    public User() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    public CustomerType getCustomerType() { return customerType; }
    public void setCustomerType(CustomerType customerType) { this.customerType = customerType; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getTinNumber() { return tinNumber; }
    public void setTinNumber(String tinNumber) { this.tinNumber = tinNumber; }

    public String getVrnNumber() { return vrnNumber; }
    public void setVrnNumber(String vrnNumber) { this.vrnNumber = vrnNumber; }

    public String getBrelaNumber() { return brelaNumber; }
    public void setBrelaNumber(String brelaNumber) { this.brelaNumber = brelaNumber; }

    public String getContactPersonName() { return contactPersonName; }
    public void setContactPersonName(String contactPersonName) { this.contactPersonName = contactPersonName; }

    public String getOfficeAddress() { return officeAddress; }
    public void setOfficeAddress(String officeAddress) { this.officeAddress = officeAddress; }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public DriverApprovalStatus getDriverApprovalStatus() { return driverApprovalStatus; }
    public void setDriverApprovalStatus(DriverApprovalStatus driverApprovalStatus) {
        this.driverApprovalStatus = driverApprovalStatus;
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @PrePersist
    protected void initializeDriverApprovalStatus() {
        if (driverApprovalStatus == null) {
            driverApprovalStatus = role == UserRole.DRIVER
                    ? DriverApprovalStatus.PENDING_APPROVAL
                    : DriverApprovalStatus.APPROVED;
        }
    }
}