package com.capitecfilestatement.service;
import com.capitecfilestatement.entity.*;
import com.capitecfilestatement.repository.*;
import com.capitecfilestatement.dto.*;
import com.capitecfilestatement.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatementService {

    private final AccountStatementRepository statementRepository;
    private final CustomerRepository customerRepository;
    private final DownloadTokenRepository downloadTokenRepository;
    private final AuditLogRepository auditLogRepository;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${security.download-link.expiration-minutes}")
    private int downloadLinkExpirationMinutes;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("application/pdf");
    private static final int MAX_ACTIVE_TOKENS_PER_CUSTOMER = 5;

    @Transactional
    public StatementUploadResponse uploadStatement(
            UUID customerId,
            MultipartFile file,
            String statementPeriod,
            String ipAddress) throws IOException {

        // Validate customer
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        if (!customer.getActive()) {
            throw new BusinessException("Customer account is inactive");
        }

        // Validate file
        validateFile(file);

        // Check for duplicate period
        if (statementRepository.findByCustomerIdAndPeriod(customerId, statementPeriod).isPresent()) {
            throw new BusinessException("Statement already exists for period: " + statementPeriod);
        }

        // Generate S3 key
        String s3Key = generateS3Key(customerId, statementPeriod, file.getOriginalFilename());

        // Calculate checksum
        byte[] fileBytes = file.getBytes();
        String checksum = calculateSHA256(fileBytes);

        // Upload to S3 with encryption
        uploadToS3(s3Key, fileBytes, file.getContentType());

        // Save metadata
        AccountStatement statement = AccountStatement.builder()
                .customer(customer)
                .s3Key(s3Key)
                .fileName(file.getOriginalFilename())
                .fileSizeBytes(file.getSize())
                .statementPeriod(statementPeriod)
                .contentType(file.getContentType())
                .checksumSha256(checksum)
                .encrypted(true)
                .build();

        statement = statementRepository.save(statement);

        // Audit log
        auditLogRepository.save(AuditLog.builder()
                .customer(customer)
                .action("UPLOAD")
                .resourceType("ACCOUNT_STATEMENT")
                .resourceId(statement.getId())
                .ipAddress(ipAddress)
                .details("Uploaded statement for period: " + statementPeriod)
                .build());

        log.info("Statement uploaded successfully: {} for customer: {}", statement.getId(), customerId);

        return mapToUploadResponse(statement);
    }

    @Transactional
    public DownloadLinkResponse generateDownloadLink(
            UUID customerId,
            UUID statementId,
            String ipAddress) {

        // Validate statement belongs to customer
        AccountStatement statement = statementRepository
                .findByIdAndCustomerId(statementId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Statement not found"));

        // Check active token limit
        long activeTokens = downloadTokenRepository
                .countActiveTokensByCustomer(customerId, LocalDateTime.now());

        if (activeTokens >= MAX_ACTIVE_TOKENS_PER_CUSTOMER) {
            throw new BusinessException("Maximum number of active download links reached");
        }

        // Generate secure token
        String token = generateSecureToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(downloadLinkExpirationMinutes);

        // Save token
        Customer customer = statement.getCustomer();
        DownloadToken downloadToken = DownloadToken.builder()
                .token(token)
                .statement(statement)
                .customer(customer)
                .expiresAt(expiresAt)
                .used(false)
                .build();

        downloadTokenRepository.save(downloadToken);

        // Audit log
        auditLogRepository.save(AuditLog.builder()
                .customer(customer)
                .action("GENERATE_LINK")
                .resourceType("ACCOUNT_STATEMENT")
                .resourceId(statementId)
                .ipAddress(ipAddress)
                .details("Generated download link for statement: " + statement.getFileName())
                .build());

        log.info("Download link generated for statement: {} customer: {}", statementId, customerId);

        String downloadUrl = "/api/statements/download/" + token;

        return new DownloadLinkResponse() {{
            setDownloadUrl(downloadUrl);
            setExpiresAt(expiresAt);
            setValidForMinutes(downloadLinkExpirationMinutes);
        }};
    }

    @Transactional
    public String downloadStatement(String token, String ipAddress) {

        // Validate token
        DownloadToken downloadToken = downloadTokenRepository
                .findValidToken(token, LocalDateTime.now())
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired download link"));

        // Mark token as used
        downloadToken.setUsed(true);
        downloadToken.setUsedAt(LocalDateTime.now());
        downloadTokenRepository.save(downloadToken);

        AccountStatement statement = downloadToken.getStatement();

        // Generate presigned URL
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(statement.getS3Key())
                .responseContentDisposition("attachment; filename=\"" + statement.getFileName() + "\"")
                .responseContentType(statement.getContentType())
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5))
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        String presignedUrl = presignedRequest.url().toString();

        // Audit log
        auditLogRepository.save(AuditLog.builder()
                .customer(downloadToken.getCustomer())
                .action("DOWNLOAD")
                .resourceType("ACCOUNT_STATEMENT")
                .resourceId(statement.getId())
                .ipAddress(ipAddress)
                .details("Downloaded statement: " + statement.getFileName())
                .build());

        log.info("Statement downloaded: {} by customer: {}", statement.getId(), downloadToken.getCustomer().getId());

        return presignedUrl;
    }

    @Transactional(readOnly = true)
    public List<StatementListResponse> getCustomerStatements(UUID customerId) {
        return statementRepository.findByCustomerIdOrderByStatementPeriodDesc(customerId)
                .stream()
                .map(this::mapToListResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteStatement(UUID customerId, UUID statementId, String ipAddress) {

        AccountStatement statement = statementRepository
                .findByIdAndCustomerId(statementId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Statement not found"));

        // Delete from S3
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(statement.getS3Key())
                    .build();

            s3Client.deleteObject(deleteRequest);
        } catch (Exception e) {
            log.error("Failed to delete S3 object: {}", statement.getS3Key(), e);
            throw new BusinessException("Failed to delete statement from storage");
        }

        // Delete from database
        statementRepository.delete(statement);

        // Audit log
        auditLogRepository.save(AuditLog.builder()
                .customer(statement.getCustomer())
                .action("DELETE")
                .resourceType("ACCOUNT_STATEMENT")
                .resourceId(statementId)
                .ipAddress(ipAddress)
                .details("Deleted statement: " + statement.getFileName())
                .build());

        log.info("Statement deleted: {} by customer: {}", statementId, customerId);
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ValidationException("File is empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ValidationException("File size exceeds maximum allowed size");
        }

        String contentType = file.getContentType();
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ValidationException("Only PDF files are allowed");
        }

        // Additional PDF validation
        try {
            byte[] header = new byte[4];
            file.getInputStream().read(header);
            String headerString = new String(header);
            if (!headerString.equals("%PDF")) {
                throw new ValidationException("Invalid PDF file");
            }
        } catch (IOException e) {
            throw new ValidationException("Failed to validate file");
        }
    }

    private String generateS3Key(UUID customerId, String period, String filename) {
        String sanitizedFilename = filename.replaceAll("[^a-zA-Z0-9.-]", "_");
        return String.format("statements/%s/%s/%s_%s",
                customerId,
                period,
                UUID.randomUUID(),
                sanitizedFilename);
    }

    private void uploadToS3(String key, byte[] data, String contentType) {
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .serverSideEncryption(ServerSideEncryption.AES256)
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(data));
        } catch (Exception e) {
            log.error("Failed to upload to S3: {}", key, e);
            throw new BusinessException("Failed to upload file to storage");
        }
    }

    private String calculateSHA256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException("Failed to calculate checksum");
        }
    }

    private String generateSecureToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private StatementUploadResponse mapToUploadResponse(AccountStatement statement) {
        StatementUploadResponse response = new StatementUploadResponse();
        response.setId(statement.getId());
        response.setFileName(statement.getFileName());
        response.setStatementPeriod(statement.getStatementPeriod());
        response.setFileSizeBytes(statement.getFileSizeBytes());
        response.setUploadedAt(statement.getCreatedAt());
        return response;
    }

    private StatementListResponse mapToListResponse(AccountStatement statement) {
        StatementListResponse response = new StatementListResponse();
        response.setId(statement.getId());
        response.setFileName(statement.getFileName());
        response.setStatementPeriod(statement.getStatementPeriod());
        response.setFileSizeBytes(statement.getFileSizeBytes());
        response.setCreatedAt(statement.getCreatedAt());
        return response;
    }
}
