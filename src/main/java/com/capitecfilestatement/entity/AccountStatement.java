package com.capitecfilestatement.entity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "account_statements", indexes = {
        @Index(name = "idx_statement_customer", columnList = "customer_id"),
        @Index(name = "idx_statement_period", columnList = "statement_period"),
        @Index(name = "idx_statement_s3_key", columnList = "s3_key")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountStatement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false)
    private String s3Key;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private Long fileSizeBytes;

    @Column(nullable = false)
    private String statementPeriod; // e.g., "2024-01"

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private String checksumSha256;

    @Column(nullable = false)
    private Boolean encrypted = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

