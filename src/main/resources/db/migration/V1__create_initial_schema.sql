-- Customers table
CREATE TABLE customers (
                           id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                           email VARCHAR(255) NOT NULL UNIQUE,
                           first_name VARCHAR(100) NOT NULL,
                           last_name VARCHAR(100) NOT NULL,
                           password_hash VARCHAR(255) NOT NULL,
                           active BOOLEAN NOT NULL DEFAULT true,
                           created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_customer_email ON customers(email);

-- Account statements table
CREATE TABLE account_statements (
                                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                    customer_id UUID NOT NULL,
                                    s3_key VARCHAR(500) NOT NULL UNIQUE,
                                    file_name VARCHAR(255) NOT NULL,
                                    file_size_bytes BIGINT NOT NULL,
                                    statement_period VARCHAR(20) NOT NULL,
                                    content_type VARCHAR(50) NOT NULL,
                                    checksum_sha256 VARCHAR(255) NOT NULL,
                                    encrypted BOOLEAN NOT NULL DEFAULT true,
                                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                    CONSTRAINT fk_statement_customer FOREIGN KEY (customer_id)
                                        REFERENCES customers(id) ON DELETE CASCADE,
                                    CONSTRAINT unique_customer_period UNIQUE (customer_id, statement_period)
);

CREATE INDEX idx_statement_customer ON account_statements(customer_id);
CREATE INDEX idx_statement_period ON account_statements(statement_period);
CREATE INDEX idx_statement_s3_key ON account_statements(s3_key);

-- Download tokens table
CREATE TABLE download_tokens (
                                 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 token VARCHAR(255) NOT NULL UNIQUE,
                                 statement_id UUID NOT NULL,
                                 customer_id UUID NOT NULL,
                                 expires_at TIMESTAMP NOT NULL,
                                 used BOOLEAN NOT NULL DEFAULT false,
                                 used_at TIMESTAMP,
                                 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 CONSTRAINT fk_token_statement FOREIGN KEY (statement_id)
                                     REFERENCES account_statements(id) ON DELETE CASCADE,
                                 CONSTRAINT fk_token_customer FOREIGN KEY (customer_id)
                                     REFERENCES customers(id) ON DELETE CASCADE
);

CREATE INDEX idx_token_value ON download_tokens(token);
CREATE INDEX idx_token_expires ON download_tokens(expires_at);
CREATE INDEX idx_token_statement ON download_tokens(statement_id);
CREATE INDEX idx_token_customer_used ON download_tokens(customer_id, used, expires_at);

-- Audit logs table
CREATE TABLE audit_logs (
                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            customer_id UUID,
                            action VARCHAR(50) NOT NULL,
                            resource_type VARCHAR(50) NOT NULL,
                            resource_id UUID,
                            ip_address VARCHAR(45),
                            user_agent VARCHAR(500),
                            details VARCHAR(1000),
                            timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            CONSTRAINT fk_audit_customer FOREIGN KEY (customer_id)
                                REFERENCES customers(id) ON DELETE SET NULL
);

CREATE INDEX idx_audit_customer ON audit_logs(customer_id);
CREATE INDEX idx_audit_timestamp ON audit_logs(timestamp);
CREATE INDEX idx_audit_action ON audit_logs(action);

-- Trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_customers_updated_at
    BEFORE UPDATE ON customers
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_statements_updated_at
    BEFORE UPDATE ON account_statements
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();