package com.capitecfilestatement.dto;
import lombok.Data;
import java.util.UUID;
import java.time.LocalDateTime;


@Data
public class StatementListResponse {
    private UUID id;
    private String fileName;
    private String statementPeriod;
    private Long fileSizeBytes;
    private LocalDateTime createdAt;
}