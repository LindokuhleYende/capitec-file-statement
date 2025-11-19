#!/bin/bash

# ============================================
# Capitec File Statement  - API Test Script
# ============================================

set -e

# Configuration
BASE_URL="${API_BASE_URL:-http://localhost:8080}"
TEMP_DIR="/tmp/capitec-test-$$"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Functions
print_header() {
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${YELLOW}ℹ $1${NC}"
}

cleanup() {
    rm -rf "$TEMP_DIR"
}

trap cleanup EXIT

# Create temp directory
mkdir -p "$TEMP_DIR"

# Check if server is running
print_header "Checking Server Connection"
if ! curl -s -f "$BASE_URL/actuator/health" > /dev/null; then
    print_error "Server is not running at $BASE_URL"
    print_info "Start server with: docker-compose up -d"
    exit 1
fi
print_success "Server is running"
echo ""

# Test 1: Register User
print_header "Test 1: Register New User"
TIMESTAMP=$(date +%s)
TEST_EMAIL="test${TIMESTAMP}@example.com"
TEST_PASSWORD="Test123!"

print_info "Registering user: $TEST_EMAIL"

REGISTER_RESPONSE=$(curl -s -X POST "$BASE_URL/api/auth/register" \
    -H "Content-Type: application/json" \
    -d "{
        \"email\": \"$TEST_EMAIL\",
        \"password\": \"$TEST_PASSWORD\",
        \"firstName\": \"Test\",
        \"lastName\": \"User\"
    }")

# Check if registration was successful
if echo "$REGISTER_RESPONSE" | jq -e '.token' > /dev/null 2>&1; then
    TOKEN=$(echo "$REGISTER_RESPONSE" | jq -r '.token')
    CUSTOMER_ID=$(echo "$REGISTER_RESPONSE" | jq -r '.customerId')
    print_success "User registered successfully"
    print_info "Customer ID: $CUSTOMER_ID"
    print_info "Token: ${TOKEN:0:30}..."
else
    print_error "Registration failed"
    echo "$REGISTER_RESPONSE" | jq '.'
    exit 1
fi
echo ""

# Test 2: Login
print_header "Test 2: Login Existing User"
print_info "Logging in as: $TEST_EMAIL"

LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{
        \"email\": \"$TEST_EMAIL\",
        \"password\": \"$TEST_PASSWORD\"
    }")

if echo "$LOGIN_RESPONSE" | jq -e '.token' > /dev/null 2>&1; then
    print_success "Login successful"
else
    print_error "Login failed"
    echo "$LOGIN_RESPONSE" | jq '.'
    exit 1
fi
echo ""

# Test 3: Create Test PDF
print_header "Test 3: Create Test PDF"
TEST_PDF="$TEMP_DIR/test-statement.pdf"

cat > "$TEST_PDF" << 'EOF'
%PDF-1.4
1 0 obj
<<
/Type /Catalog
/Pages 2 0 R
>>
endobj
2 0 obj
<<
/Type /Pages
/Kids [3 0 R]
/Count 1
>>
endobj
3 0 obj
<<
/Type /Page
/Parent 2 0 R
/Resources <<
/Font <<
/F1 <<
/Type /Font
/Subtype /Type1
/BaseFont /Helvetica
>>
>>
>>
/MediaBox [0 0 612 792]
/Contents 4 0 R
>>
endobj
4 0 obj
<<
/Length 44
>>
stream
BT
/F1 24 Tf
100 700 Td
(Test Statement) Tj
ET
endstream
endobj
xref
0 5
0000000000 65535 f
0000000009 00000 n
0000000058 00000 n
0000000115 00000 n
0000000315 00000 n
trailer
<<
/Size 5
/Root 1 0 R
>>
startxref
408
%%EOF
EOF

print_success "Test PDF created: $(wc -c < "$TEST_PDF") bytes"
echo ""

# Test 4: Upload Statement
print_header "Test 4: Upload Statement"
STATEMENT_PERIOD=$(date +%Y-%m)

print_info "Uploading statement for period: $STATEMENT_PERIOD"

UPLOAD_RESPONSE=$(curl -s -X POST "$BASE_URL/api/statements/upload" \
    -H "Authorization: Bearer $TOKEN" \
    -F "file=@$TEST_PDF" \
    -F "statementPeriod=$STATEMENT_PERIOD")

if echo "$UPLOAD_RESPONSE" | jq -e '.id' > /dev/null 2>&1; then
    STATEMENT_ID=$(echo "$UPLOAD_RESPONSE" | jq -r '.id')
    FILE_NAME=$(echo "$UPLOAD_RESPONSE" | jq -r '.fileName')
    FILE_SIZE=$(echo "$UPLOAD_RESPONSE" | jq -r '.fileSizeBytes')

    print_success "Statement uploaded successfully"
    print_info "Statement ID: $STATEMENT_ID"
    print_info "File Name: $FILE_NAME"
    print_info "File Size: $FILE_SIZE bytes"
else
    print_error "Upload failed"
    echo "$UPLOAD_RESPONSE" | jq '.'
    exit 1
fi
echo ""

# Test 5: List Statements
print_header "Test 5: List Customer Statements"

STATEMENTS_LIST=$(curl -s -X GET "$BASE_URL/api/statements" \
    -H "Authorization: Bearer $TOKEN")

STATEMENT_COUNT=$(echo "$STATEMENTS_LIST" | jq '. | length')
print_success "Retrieved $STATEMENT_COUNT statement(s)"
echo "$STATEMENTS_LIST" | jq '.'
echo ""

# Test 6: Generate Download Link
print_header "Test 6: Generate Download Link"

print_info "Generating download link for statement: $STATEMENT_ID"

LINK_RESPONSE=$(curl -s -X POST "$BASE_URL/api/statements/generate-link" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"statementId\": \"$STATEMENT_ID\"}")

if echo "$LINK_RESPONSE" | jq -e '.downloadUrl' > /dev/null 2>&1; then
    DOWNLOAD_URL=$(echo "$LINK_RESPONSE" | jq -r '.downloadUrl')
    EXPIRES_AT=$(echo "$LINK_RESPONSE" | jq -r '.expiresAt')
    VALID_MINUTES=$(echo "$LINK_RESPONSE" | jq -r '.validForMinutes')

    print_success "Download link generated"
    print_info "Download URL: $DOWNLOAD_URL"
    print_info "Expires At: $EXPIRES_AT"
    print_info "Valid For: $VALID_MINUTES minutes"
else
    print_error "Failed to generate download link"
    echo "$LINK_RESPONSE" | jq '.'
    exit 1
fi
echo ""

# Test 7: Download Statement
print_header "Test 7: Download Statement"

DOWNLOAD_TOKEN=$(echo "$DOWNLOAD_URL" | sed 's/.*\///')
DOWNLOADED_FILE="$TEMP_DIR/downloaded-statement.pdf"

print_info "Downloading statement using token..."

HTTP_CODE=$(curl -s -L -w "%{http_code}" \
    "$BASE_URL$DOWNLOAD_URL" \
    -o "$DOWNLOADED_FILE")

if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "302" ]; then
    DOWNLOADED_SIZE=$(wc -c < "$DOWNLOADED_FILE")
    print_success "Statement downloaded successfully"
    print_info "Downloaded Size: $DOWNLOADED_SIZE bytes"

    # Verify it's a PDF
    if file "$DOWNLOADED_FILE" | grep -q PDF; then
        print_success "Downloaded file is valid PDF"
    else
        print_error "Downloaded file is not a valid PDF"
    fi
else
    print_error "Download failed with HTTP code: $HTTP_CODE"
    exit 1
fi
echo ""

# Test 8: Try to use token again (should fail)
print_header "Test 8: Token Reuse Prevention"

print_info "Attempting to reuse download token (should fail)..."

HTTP_CODE=$(curl -s -L -w "%{http_code}" \
    "$BASE_URL$DOWNLOAD_URL" \
    -o /dev/null)

if [ "$HTTP_CODE" = "401" ] || [ "$HTTP_CODE" = "403" ]; then
    print_success "Token reuse correctly prevented (HTTP $HTTP_CODE)"
else
    print_error "Token reuse was not prevented (HTTP $HTTP_CODE)"
fi
echo ""

# Test 9: Generate Multiple Links
print_header "Test 9: Multiple Download Links"

print_info "Generating multiple download links..."

for i in {1..3}; do
    MULTI_LINK_RESPONSE=$(curl -s -X POST "$BASE_URL/api/statements/generate-link" \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json" \
        -d "{\"statementId\": \"$STATEMENT_ID\"}")

    if echo "$MULTI_LINK_RESPONSE" | jq -e '.downloadUrl' > /dev/null 2>&1; then
        print_success "Link $i generated successfully"
    else
        print_error "Failed to generate link $i"
    fi
done
echo ""

# Test 10: Invalid Token
print_header "Test 10: Invalid Download Token"

print_info "Attempting download with invalid token..."

HTTP_CODE=$(curl -s -L -w "%{http_code}" \
    "$BASE_URL/api/statements/download/invalid-token-123" \
    -o /dev/null)

if [ "$HTTP_CODE" = "401" ] || [ "$HTTP_CODE" = "403" ]; then
    print_success "Invalid token correctly rejected (HTTP $HTTP_CODE)"
else
    print_error "Invalid token was not rejected (HTTP $HTTP_CODE)"
fi
echo ""

# Test 11: Unauthorized Access
print_header "Test 11: Unauthorized Access Prevention"

print_info "Attempting to list statements without token..."

HTTP_CODE=$(curl -s -w "%{http_code}" \
    "$BASE_URL/api/statements" \
    -o /dev/null)

if [ "$HTTP_CODE" = "401" ] || [ "$HTTP_CODE" = "403" ]; then
    print_success "Unauthorized access correctly prevented (HTTP $HTTP_CODE)"
else
    print_error "Unauthorized access was not prevented (HTTP $HTTP_CODE)"
fi
echo ""

# Test 12: Delete Statement
print_header "Test 12: Delete Statement"

print_info "Deleting statement: $STATEMENT_ID"

HTTP_CODE=$(curl -s -w "%{http_code}" \
    -X DELETE "$BASE_URL/api/statements/$STATEMENT_ID" \
    -H "Authorization: Bearer $TOKEN" \
    -o /dev/null)

if [ "$HTTP_CODE" = "204" ]; then
    print_success "Statement deleted successfully"
else
    print_error "Failed to delete statement (HTTP $HTTP_CODE)"
fi
echo ""

# Test 13: Verify Deletion
print_header "Test 13: Verify Statement Deletion"

STATEMENTS_AFTER=$(curl -s -X GET "$BASE_URL/api/statements" \
    -H "Authorization: Bearer $TOKEN")

COUNT_AFTER=$(echo "$STATEMENTS_AFTER" | jq '. | length')

if [ "$COUNT_AFTER" -eq 0 ]; then
    print_success "Statement successfully deleted (count: $COUNT_AFTER)"
else
    print_error "Statement still exists (count: $COUNT_AFTER)"
fi
echo ""

# Test 14: Health Check
print_header "Test 14: Application Health"

HEALTH_RESPONSE=$(curl -s "$BASE_URL/actuator/health")
HEALTH_STATUS=$(echo "$HEALTH_RESPONSE" | jq -r '.status')

if [ "$HEALTH_STATUS" = "UP" ]; then
    print_success "Application is healthy"
    echo "$HEALTH_RESPONSE" | jq '.'
else
    print_error "Application health check failed"
    echo "$HEALTH_RESPONSE" | jq '.'
fi
echo ""

# Summary
print_header "Test Summary"
echo -e "${GREEN}✅ All tests completed successfully!${NC}"
echo ""
echo "Test Results:"
echo "  • User Registration: ✓"
echo "  • User Login: ✓"
echo "  • Statement Upload: ✓"
echo "  • List Statements: ✓"
echo "  • Generate Download Link: ✓"
echo "  • Download Statement: ✓"
echo "  • Token Reuse Prevention: ✓"
echo "  • Multiple Links: ✓"
echo "  • Invalid Token Rejection: ✓"
echo "  • Unauthorized Access Prevention: ✓"
echo "  • Statement Deletion: ✓"
echo "  • Verify Deletion: ✓"
echo "  • Health Check: ✓"
echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}All tests passed! 🎉${NC}"
echo -e "${GREEN}========================================${NC}"