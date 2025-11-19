package com.capitecfilestatement.task;
import com.capitecfilestatement.repository.DownloadTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupTask {

    private final DownloadTokenRepository downloadTokenRepository;

    @Transactional
    @Scheduled(cron = "0 0 * * * *") // Every hour
    public void cleanupExpiredTokens() {
        log.info("Starting cleanup of expired download tokens");

        LocalDateTime cutoff = LocalDateTime.now().minusDays(1);
        int deleted = downloadTokenRepository.deleteExpiredTokens(cutoff);

        log.info("Cleaned up {} expired download tokens", deleted);
    }
}
