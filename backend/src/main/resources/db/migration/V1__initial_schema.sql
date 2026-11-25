-- FinSentinel AI Initial PostgreSQL Schema
-- Covers all core domain, risk, simulation, payment, AI, notification, reporting, and audit tables.

CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(160) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    is_demo_user BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE auth_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    user_agent VARCHAR(512),
    ip_address VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_auth_sessions_user ON auth_sessions(user_id);

CREATE TABLE verification_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE user_preferences (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    theme VARCHAR(16) NOT NULL DEFAULT 'system',
    language VARCHAR(8) NOT NULL DEFAULT 'en',
    currency VARCHAR(8) NOT NULL DEFAULT 'INR',
    locale VARCHAR(16) NOT NULL DEFAULT 'en-IN',
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Kolkata',
    ai_external_consent BOOLEAN NOT NULL DEFAULT FALSE,
    reduced_motion BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE financial_profiles (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    monthly_income NUMERIC(19, 4),
    other_income NUMERIC(19, 4),
    regular_expenses NUMERIC(19, 4),
    liquid_funds NUMERIC(19, 4),
    reserve_threshold NUMERIC(19, 4) NOT NULL DEFAULT 15000.0000,
    income_frequency VARCHAR(32) NOT NULL DEFAULT 'MONTHLY',
    onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE,
    onboarding_skipped BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    account_type VARCHAR(32) NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'INR',
    opening_balance NUMERIC(19, 4) NOT NULL DEFAULT 0.0000,
    opening_balance_date DATE NOT NULL,
    is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_accounts_user ON accounts(user_id);

CREATE TABLE transaction_categories (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(80) NOT NULL,
    kind VARCHAR(24) NOT NULL,
    is_essential BOOLEAN NOT NULL DEFAULT FALSE,
    color_token VARCHAR(32),
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_categories_user ON transaction_categories(user_id);

CREATE TABLE income_sources (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    account_id UUID REFERENCES accounts(id) ON DELETE SET NULL,
    name VARCHAR(140) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL CHECK (amount >= 0),
    frequency VARCHAR(24) NOT NULL,
    expected_day INTEGER,
    next_expected_date DATE,
    stability_score NUMERIC(5, 4),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_income_sources_user ON income_sources(user_id);

CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    transfer_account_id UUID REFERENCES accounts(id) ON DELETE SET NULL,
    category_id UUID REFERENCES transaction_categories(id) ON DELETE SET NULL,
    tx_type VARCHAR(24) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency VARCHAR(8) NOT NULL DEFAULT 'INR',
    occurred_on DATE NOT NULL,
    merchant VARCHAR(160),
    description VARCHAR(500),
    is_essential BOOLEAN NOT NULL DEFAULT FALSE,
    is_recurring BOOLEAN NOT NULL DEFAULT FALSE,
    recurrence_rule VARCHAR(32),
    dedup_hash VARCHAR(64),
    anomaly_status VARCHAR(24) NOT NULL DEFAULT 'NONE',
    suggested_category_id UUID REFERENCES transaction_categories(id) ON DELETE SET NULL,
    linked_payment_record_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_transactions_user_date ON transactions(user_id, occurred_on DESC);
CREATE INDEX idx_transactions_account ON transactions(account_id);
CREATE INDEX idx_transactions_dedup ON transactions(user_id, dedup_hash);

CREATE TABLE expenses (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id UUID REFERENCES transaction_categories(id) ON DELETE SET NULL,
    title VARCHAR(160) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL CHECK (amount >= 0),
    frequency VARCHAR(24) NOT NULL,
    is_essential BOOLEAN NOT NULL DEFAULT TRUE,
    next_due_date DATE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_expenses_user ON expenses(user_id);

CREATE TABLE savings_goals (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    target_amount NUMERIC(19, 4) NOT NULL CHECK (target_amount > 0),
    current_amount NUMERIC(19, 4) NOT NULL DEFAULT 0.0000 CHECK (current_amount >= 0),
    target_date DATE,
    monthly_contribution NUMERIC(19, 4) NOT NULL DEFAULT 0.0000,
    priority INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_savings_goals_user ON savings_goals(user_id);

CREATE TABLE financial_obligations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    account_id UUID REFERENCES accounts(id) ON DELETE SET NULL,
    title VARCHAR(180) NOT NULL,
    category VARCHAR(64) NOT NULL,
    total_amount NUMERIC(19, 4) NOT NULL CHECK (total_amount > 0),
    paid_amount NUMERIC(19, 4) NOT NULL DEFAULT 0.0000 CHECK (paid_amount >= 0),
    due_date DATE NOT NULL,
    frequency VARCHAR(24) NOT NULL DEFAULT 'ONE_TIME',
    is_essential BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(24) NOT NULL DEFAULT 'UPCOMING',
    late_fee NUMERIC(19, 4) NOT NULL DEFAULT 0.0000,
    grace_days INTEGER NOT NULL DEFAULT 0,
    interest_rate NUMERIC(7, 4) NOT NULL DEFAULT 0.0000,
    notes VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_obligations_user_due ON financial_obligations(user_id, due_date ASC);

CREATE TABLE payment_schedules (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    scenario_name VARCHAR(140) NOT NULL,
    strategy VARCHAR(48) NOT NULL,
    horizon_days INTEGER NOT NULL,
    feasible BOOLEAN NOT NULL,
    shortfall_amount NUMERIC(19, 4) NOT NULL DEFAULT 0.0000,
    min_projected_balance NUMERIC(19, 4) NOT NULL,
    payload_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_payment_schedules_user ON payment_schedules(user_id);

CREATE TABLE payment_records (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    obligation_id UUID NOT NULL REFERENCES financial_obligations(id) ON DELETE CASCADE,
    account_id UUID REFERENCES accounts(id) ON DELETE SET NULL,
    transaction_id UUID REFERENCES transactions(id) ON DELETE SET NULL,
    amount NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    paid_on DATE NOT NULL,
    payment_method VARCHAR(32) NOT NULL,
    reference_note VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_payment_records_user ON payment_records(user_id);
CREATE INDEX idx_payment_records_obligation ON payment_records(obligation_id);

CREATE TABLE risk_assessments (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    policy_version VARCHAR(32) NOT NULL,
    raw_score NUMERIC(7, 4) NOT NULL,
    display_score INTEGER NOT NULL,
    category VARCHAR(24) NOT NULL,
    data_completeness NUMERIC(5, 4) NOT NULL,
    insufficient_data BOOLEAN NOT NULL DEFAULT FALSE,
    assumptions_json TEXT NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_risk_assessments_user_time ON risk_assessments(user_id, calculated_at DESC);

CREATE TABLE risk_factor_scores (
    id UUID PRIMARY KEY,
    assessment_id UUID NOT NULL REFERENCES risk_assessments(id) ON DELETE CASCADE,
    factor_key VARCHAR(48) NOT NULL,
    factor_label VARCHAR(120) NOT NULL,
    weight NUMERIC(5, 4) NOT NULL,
    normalized_value NUMERIC(7, 4),
    weighted_contribution NUMERIC(7, 4),
    measured_value VARCHAR(120),
    unit VARCHAR(40),
    data_available BOOLEAN NOT NULL DEFAULT TRUE,
    explanation TEXT NOT NULL
);
CREATE INDEX idx_risk_factor_assessment ON risk_factor_scores(assessment_id);

CREATE TABLE forecast_snapshots (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    model_version VARCHAR(32) NOT NULL,
    horizon_days INTEGER NOT NULL,
    opening_balance NUMERIC(19, 4) NOT NULL,
    reserve_threshold NUMERIC(19, 4) NOT NULL,
    min_projected_balance NUMERIC(19, 4) NOT NULL,
    shortage_date DATE,
    reserve_breach_date DATE,
    series_json TEXT NOT NULL,
    assumptions_json TEXT NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_forecast_snapshots_user ON forecast_snapshots(user_id, generated_at DESC);

CREATE TABLE scenario_simulations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    scenario_type VARCHAR(32) NOT NULL,
    template_key VARCHAR(64),
    parameters_json TEXT NOT NULL,
    horizon_days INTEGER NOT NULL DEFAULT 90,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_scenarios_user ON scenario_simulations(user_id);

CREATE TABLE scenario_results (
    id UUID PRIMARY KEY,
    simulation_id UUID NOT NULL REFERENCES scenario_simulations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    model_version VARCHAR(32) NOT NULL,
    baseline_min_balance NUMERIC(19, 4) NOT NULL,
    scenario_min_balance NUMERIC(19, 4) NOT NULL,
    shortage_date DATE,
    recovery_date DATE,
    risk_before INTEGER,
    risk_after INTEGER,
    result_json TEXT NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_scenario_results_sim ON scenario_results(simulation_id);

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    obligation_id UUID REFERENCES financial_obligations(id) ON DELETE SET NULL,
    dedup_key VARCHAR(160) NOT NULL UNIQUE,
    notif_type VARCHAR(40) NOT NULL,
    severity VARCHAR(24) NOT NULL,
    title VARCHAR(180) NOT NULL,
    body TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    delivery_channel VARCHAR(24) NOT NULL DEFAULT 'IN_APP',
    delivery_status VARCHAR(24) NOT NULL DEFAULT 'DELIVERED',
    retry_count INTEGER NOT NULL DEFAULT 0,
    scheduled_for TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_notifications_user_read ON notifications(user_id, is_read, created_at DESC);

CREATE TABLE notification_preferences (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    in_app_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    email_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    reminder_days_before INTEGER NOT NULL DEFAULT 3,
    shortfall_alerts_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    quiet_hours_start VARCHAR(8),
    quiet_hours_end VARCHAR(8),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE ai_conversations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    provider_used VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_ai_conversations_user ON ai_conversations(user_id, updated_at DESC);

CREATE TABLE ai_messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES ai_conversations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(24) NOT NULL,
    content TEXT NOT NULL,
    tool_calls_json TEXT,
    citations_json TEXT,
    tokens_used INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_ai_messages_conv ON ai_messages(conversation_id, created_at ASC);

CREATE TABLE generated_reports (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    report_type VARCHAR(48) NOT NULL,
    title VARCHAR(200) NOT NULL,
    file_name VARCHAR(200) NOT NULL,
    status VARCHAR(24) NOT NULL,
    policy_version VARCHAR(32),
    file_bytes BYTEA,
    error_message VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_reports_user ON generated_reports(user_id, created_at DESC);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    actor_role VARCHAR(24),
    action VARCHAR(80) NOT NULL,
    resource_type VARCHAR(64),
    resource_id VARCHAR(64),
    request_id VARCHAR(64),
    outcome VARCHAR(24) NOT NULL,
    metadata_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_logs_created ON audit_logs(created_at DESC);

CREATE TABLE app_configurations (
    config_key VARCHAR(80) PRIMARY KEY,
    config_value VARCHAR(500) NOT NULL,
    description VARCHAR(300),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
