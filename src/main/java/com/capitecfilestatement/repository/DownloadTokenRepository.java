package com.capitecfilestatement.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import com.capitecfilestatement.entity.DownloadToken;


@Repository
public interface DownloadTokenRepository extends JpaRepository<DownloadToken, UUID> {

    Optional<DownloadToken> findByToken(String token);

    @Query("SELECT dt FROM DownloadToken dt " +
            "WHERE dt.token = :token " +
            "AND dt.used = false " +
            "AND dt.expiresAt > :now")
    Optional<DownloadToken> findValidToken(
            @Param("token") String token,
            @Param("now") LocalDateTime now
    );

    @Modifying
    @Query("DELETE FROM DownloadToken dt WHERE dt.expiresAt < :cutoff")
    int deleteExpiredTokens(@Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT COUNT(dt) FROM DownloadToken dt " +
            "WHERE dt.customer.id = :customerId " +
            "AND dt.used = false " +
            "AND dt.expiresAt > :now")
    long countActiveTokensByCustomer(
            @Param("customerId") UUID customerId,
            @Param("now") LocalDateTime now
    );
}
