package com.capitecfilestatement.repository;
import com.capitecfilestatement.entity.AccountStatement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountStatementRepository extends JpaRepository<AccountStatement, UUID> {

    List<AccountStatement> findByCustomerIdOrderByStatementPeriodDesc(UUID customerId);

    Optional<AccountStatement> findByIdAndCustomerId(UUID id, UUID customerId);

    @Query("SELECT s FROM AccountStatement s WHERE s.customer.id = :customerId " +
            "AND s.statementPeriod = :period")
    Optional<AccountStatement> findByCustomerIdAndPeriod(
            @Param("customerId") UUID customerId,
            @Param("period") String period
    );

    boolean existsByS3Key(String s3Key);
}

