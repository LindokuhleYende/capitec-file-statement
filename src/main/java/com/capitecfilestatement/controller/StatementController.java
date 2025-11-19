package com.capitecfilestatement.controller;
import com.capitecfilestatement.dto.*;
import com.capitecfilestatement.service.StatementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.view.RedirectView;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import com.capitecfilestatement.dto.GenerateDownloadLinkRequest;


@Slf4j
@RestController
@RequestMapping("/api/statements")
@RequiredArgsConstructor
public class StatementController {

    private final StatementService statementService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StatementUploadResponse> uploadStatement(
            @RequestParam("file") MultipartFile file,
            @RequestParam("statementPeriod") String statementPeriod,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest request) throws IOException {

        UUID customerId = UUID.fromString(userDetails.getUsername());
        String ipAddress = getClientIpAddress(request);

        StatementUploadResponse response = statementService.uploadStatement(
                customerId, file, statementPeriod, ipAddress);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<StatementListResponse>> getStatements(
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID customerId = UUID.fromString(userDetails.getUsername());
        List<StatementListResponse> statements = statementService.getCustomerStatements(customerId);

        return ResponseEntity.ok(statements);
    }

    @PostMapping("/generate-link")
    public ResponseEntity<DownloadLinkResponse> generateDownloadLink(
            @Valid @RequestBody GenerateDownloadLinkRequest request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {

        UUID customerId = UUID.fromString(userDetails.getUsername());
        String ipAddress = getClientIpAddress(httpRequest);

        DownloadLinkResponse response = statementService.generateDownloadLink(
                customerId, request.getStatementId(), ipAddress);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/download/{token}")
    public RedirectView downloadStatement(
            @PathVariable String token,
            HttpServletRequest request) {

        String ipAddress = getClientIpAddress(request);
        String presignedUrl = statementService.downloadStatement(token, ipAddress);

        return new RedirectView(presignedUrl);
    }

    @DeleteMapping("/{statementId}")
    public ResponseEntity<Void> deleteStatement(
            @PathVariable UUID statementId,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest request) {

        UUID customerId = UUID.fromString(userDetails.getUsername());
        String ipAddress = getClientIpAddress(request);

        statementService.deleteStatement(customerId, statementId, ipAddress);

        return ResponseEntity.noContent().build();
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }
}