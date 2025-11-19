package com.capitecfilestatement.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.capitecfilestatement.entity.AuditLog;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByCustomerIdOrderByTimestampDesc(UUID customerId);

    @Query("SELECT al FROM AuditLog al WHERE al.customer.id = :customerId " +
            "AND al.timestamp BETWEEN :start AND :end " +
            "ORDER BY al.timestamp DESC")
    List<AuditLog> findByCustomerAndDateRange(
            @Param("customerId") UUID customerId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}
