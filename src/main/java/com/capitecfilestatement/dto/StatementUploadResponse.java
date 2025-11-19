package com.capitecfilestatement.dto;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class StatementUploadResponse {
    private UUID id;
    private String fileName;
    private String statementPeriod;
    private Long fileSizeBytes;
    private LocalDateTime uploadedAt;
}