-- Seed system transaction categories and initial app configuration

INSERT INTO transaction_categories (id, user_id, name, kind, is_essential, color_token, is_system) VALUES
('11111111-1111-1111-1111-111111111101', NULL, 'Salary & Wages', 'INCOME', FALSE, 'accent', TRUE),
('11111111-1111-1111-1111-111111111102', NULL, 'Freelance & Consulting', 'INCOME', FALSE, 'accent-soft', TRUE),
('11111111-1111-1111-1111-111111111103', NULL, 'Housing & Rent', 'EXPENSE', TRUE, 'slate', TRUE),
('11111111-1111-1111-1111-111111111104', NULL, 'Utilities & Telecom', 'EXPENSE', TRUE, 'slate', TRUE),
('11111111-1111-1111-1111-111111111105', NULL, 'Groceries & Essentials', 'EXPENSE', TRUE, 'emerald', TRUE),
('11111111-1111-1111-1111-111111111106', NULL, 'Healthcare & Insurance', 'EXPENSE', TRUE, 'warning', TRUE),
('11111111-1111-1111-1111-111111111107', NULL, 'Loan & EMI Payment', 'EXPENSE', TRUE, 'critical', TRUE),
('11111111-1111-1111-1111-111111111108', NULL, 'Transport & Fuel', 'EXPENSE', TRUE, 'slate', TRUE),
('11111111-1111-1111-1111-111111111109', NULL, 'Dining & Discretionary', 'EXPENSE', FALSE, 'warning', TRUE),
('11111111-1111-1111-1111-111111111110', NULL, 'Subscriptions & Digital', 'EXPENSE', FALSE, 'secondary', TRUE),
('11111111-1111-1111-1111-111111111111', NULL, 'Emergency Savings', 'SAVINGS', TRUE, 'accent', TRUE),
('11111111-1111-1111-1111-111111111112', NULL, 'Account Transfer', 'TRANSFER', FALSE, 'secondary', TRUE),
('11111111-1111-1111-1111-111111111113', NULL, 'Refund & Reversal', 'REFUND', FALSE, 'accent', TRUE);

INSERT INTO app_configurations (config_key, config_value, description) VALUES
('risk.policy.version', 'risk-policy-v1.0', 'Active deterministic explainable risk policy version'),
('forecast.model.version', 'cashflow-deterministic-v1.0', 'Active deterministic cash-flow forecast engine version'),
('ai.rate_limit.per_minute', '20', 'Maximum AI chat requests per authenticated user per minute'),
('ai.max_input_chars', '2000', 'Maximum prompt length accepted by AI assistant'),
('auth.rate_limit.per_minute', '15', 'Maximum auth attempts per IP per minute');
