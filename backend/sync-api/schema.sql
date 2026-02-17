-- Zyra Memory Database Schema for PostgreSQL
-- Deploy this in your Coolify PostgreSQL instance

-- conversations table
CREATE TABLE IF NOT EXISTS zyra_conversations (
    id SERIAL PRIMARY KEY,
    device_id VARCHAR(255) NOT NULL,
    timestamp BIGINT NOT NULL,
    user_input TEXT NOT NULL,
    zyra_response TEXT NOT NULL,
    intent_type VARCHAR(100),
    success BOOLEAN DEFAULT TRUE,
    context TEXT,
    synced_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(device_id, timestamp)
);

-- actions table
CREATE TABLE IF NOT EXISTS zyra_actions (
    id SERIAL PRIMARY KEY,
    device_id VARCHAR(255) NOT NULL,
    timestamp BIGINT NOT NULL,
    action_type VARCHAR(100) NOT NULL,
    action_data TEXT,
    location TEXT,
    success BOOLEAN DEFAULT TRUE,
    synced_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- searches table
CREATE TABLE IF NOT EXISTS zyra_searches (
    id SERIAL PRIMARY KEY,
    device_id VARCHAR(255) NOT NULL,
    timestamp BIGINT NOT NULL,
    search_type VARCHAR(100) NOT NULL,
    query TEXT NOT NULL,
    result TEXT,
    synced_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- patterns table (aggregated from all devices)
CREATE TABLE IF NOT EXISTS zyra_patterns (
    id SERIAL PRIMARY KEY,
    device_id VARCHAR(255) NOT NULL,
    pattern_type VARCHAR(100) NOT NULL,
    pattern_data TEXT NOT NULL,
    frequency INTEGER DEFAULT 1,
    last_occurrence BIGINT,
    synced_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- preferences table
CREATE TABLE IF NOT EXISTS zyra_preferences (
    id SERIAL PRIMARY KEY,
    device_id VARCHAR(255) NOT NULL,
    preference_key VARCHAR(255) NOT NULL,
    preference_value TEXT NOT NULL,
    confidence REAL DEFAULT 0.5,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(device_id, preference_key)
);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_conversations_device ON zyra_conversations(device_id);
CREATE INDEX IF NOT EXISTS idx_conversations_timestamp ON zyra_conversations(timestamp);
CREATE INDEX IF NOT EXISTS idx_conversations_device_timestamp ON zyra_conversations(device_id, timestamp);

CREATE INDEX IF NOT EXISTS idx_actions_device ON zyra_actions(device_id);
CREATE INDEX IF NOT EXISTS idx_actions_timestamp ON zyra_actions(timestamp);

CREATE INDEX IF NOT EXISTS idx_searches_device ON zyra_searches(device_id);
CREATE INDEX IF NOT EXISTS idx_searches_timestamp ON zyra_searches(timestamp);

CREATE INDEX IF NOT EXISTS idx_patterns_device ON zyra_patterns(device_id);

CREATE INDEX IF NOT EXISTS idx_preferences_device ON zyra_preferences(device_id);

-- Create view for recent conversations
CREATE OR REPLACE VIEW recent_conversations AS
SELECT 
    device_id,
    timestamp,
    user_input,
    zyra_response,
    intent_type,
    success
FROM zyra_conversations
ORDER BY timestamp DESC
LIMIT 1000;

-- Create view for conversation statistics
CREATE OR REPLACE VIEW conversation_stats AS
SELECT 
    device_id,
    COUNT(*) as total_conversations,
    COUNT(DISTINCT DATE(to_timestamp(timestamp/1000))) as active_days,
    MAX(timestamp) as last_conversation
FROM zyra_conversations
GROUP BY device_id;
