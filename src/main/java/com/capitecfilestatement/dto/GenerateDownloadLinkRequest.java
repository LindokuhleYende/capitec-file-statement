package com.capitecfilestatement.dto;
import lombok.Data;
import java.util.UUID;
import jakarta.validation.constraints.*;


@Data
public class GenerateDownloadLinkRequest {
    @NotNull(message = "Statement ID is required")
    private UUID statementId;
}