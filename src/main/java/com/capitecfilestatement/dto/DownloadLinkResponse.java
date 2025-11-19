package com.capitecfilestatement.dto;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DownloadLinkResponse {
    private String downloadUrl;
    private LocalDateTime expiresAt;
    private Integer validForMinutes;
}