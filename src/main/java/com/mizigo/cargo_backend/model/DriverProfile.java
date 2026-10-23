package com.mizigo.cargo_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "driver_profiles", uniqueConstraints = {
        @UniqueConstraint(name = "uk_driver_profile_driver", columnNames = "driver_id")
})
public class DriverProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false, unique = true)
    private User driver;

    @Column(name = "nida_encrypted", nullable = false, length = 512)
    private String nidaEncrypted;

    @Column(name = "license_number_encrypted", nullable = false, length = 512)
    private String licenseNumberEncrypted;

    @Column(name = "license_expiry", nullable = false)
    private LocalDate licenseExpiry;

    @Column(name = "license_document_encrypted", columnDefinition = "text")
    private String licenseDocumentEncrypted;

    @Column(name = "license_document_content_type", length = 64)
    private String licenseDocumentContentType;

    @Column(name = "nida_document_encrypted", columnDefinition = "text")
    private String nidaDocumentEncrypted;

    @Column(name = "nida_document_content_type", length = 32)
    private String nidaDocumentContentType;

    @Column(name = "face_photo_encrypted", columnDefinition = "text")
    private String facePhotoEncrypted;

    @Column(name = "face_photo_content_type", length = 32)
    private String facePhotoContentType;

    @Column(name = "is_available", nullable = false)
    private boolean available;

    @Column(name = "commission_balance")
    private Double commissionBalance = 0.0;

    @Column(name = "unpaid_job_count")
    private Integer unpaidJobCount = 0;

    @Column(name = "max_unpaid_jobs")
    private Integer maxUnpaidJobs = 3;

    @Column(name = "is_restricted")
    private Boolean restricted = false;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public DriverProfile() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getDriver() { return driver; }
    public void setDriver(User driver) { this.driver = driver; }
    public String getNidaEncrypted() { return nidaEncrypted; }
    public void setNidaEncrypted(String nidaEncrypted) { this.nidaEncrypted = nidaEncrypted; }
    public String getLicenseNumberEncrypted() { return licenseNumberEncrypted; }
    public void setLicenseNumberEncrypted(String licenseNumberEncrypted) { this.licenseNumberEncrypted = licenseNumberEncrypted; }
    public LocalDate getLicenseExpiry() { return licenseExpiry; }
    public void setLicenseExpiry(LocalDate licenseExpiry) { this.licenseExpiry = licenseExpiry; }
    public String getLicenseDocumentEncrypted() { return licenseDocumentEncrypted; }
    public void setLicenseDocumentEncrypted(String licenseDocumentEncrypted) { this.licenseDocumentEncrypted = licenseDocumentEncrypted; }
    public String getLicenseDocumentContentType() { return licenseDocumentContentType; }
    public void setLicenseDocumentContentType(String licenseDocumentContentType) { this.licenseDocumentContentType = licenseDocumentContentType; }
    public String getNidaDocumentEncrypted() { return nidaDocumentEncrypted; }
    public void setNidaDocumentEncrypted(String nidaDocumentEncrypted) { this.nidaDocumentEncrypted = nidaDocumentEncrypted; }
    public String getNidaDocumentContentType() { return nidaDocumentContentType; }
    public void setNidaDocumentContentType(String nidaDocumentContentType) { this.nidaDocumentContentType = nidaDocumentContentType; }
    public String getFacePhotoEncrypted() { return facePhotoEncrypted; }
    public void setFacePhotoEncrypted(String facePhotoEncrypted) { this.facePhotoEncrypted = facePhotoEncrypted; }
    public String getFacePhotoContentType() { return facePhotoContentType; }
    public void setFacePhotoContentType(String facePhotoContentType) { this.facePhotoContentType = facePhotoContentType; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
    public Double getCommissionBalance() { return commissionBalance == null ? 0.0 : commissionBalance; }
    public void setCommissionBalance(Double commissionBalance) { this.commissionBalance = commissionBalance; }
    public Integer getUnpaidJobCount() { return unpaidJobCount == null ? 0 : unpaidJobCount; }
    public void setUnpaidJobCount(Integer unpaidJobCount) { this.unpaidJobCount = unpaidJobCount; }
    public Integer getMaxUnpaidJobs() { return maxUnpaidJobs == null ? 3 : maxUnpaidJobs; }
    public void setMaxUnpaidJobs(Integer maxUnpaidJobs) { this.maxUnpaidJobs = maxUnpaidJobs; }
    public boolean isRestricted() { return Boolean.TRUE.equals(restricted); }
    public void setRestricted(Boolean restricted) { this.restricted = restricted; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @jakarta.persistence.PreUpdate
    @jakarta.persistence.PrePersist
    protected void updateTimestamp() {
        updatedAt = LocalDateTime.now();
        if (commissionBalance == null) commissionBalance = 0.0;
        if (unpaidJobCount == null) unpaidJobCount = 0;
        if (maxUnpaidJobs == null) maxUnpaidJobs = 3;
        if (restricted == null) restricted = false;
    }
}