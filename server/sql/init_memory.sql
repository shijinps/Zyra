-- Enable Extensions for "Ultra Logic"
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS postgis;

-- 1. Semantic Memory (Vector DB) for n8n
CREATE TABLE IF NOT EXISTS n8n_chat_histories (
    id SERIAL PRIMARY KEY,
    session_id TEXT NOT NULL,
    message JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Spatio-Temporal Memory (Incidents) for Proactive Alerts
CREATE TABLE IF NOT EXISTS kochi_incidents (
    id SERIAL PRIMARY KEY,
    description TEXT NOT NULL,
    severity VARCHAR(50), -- 'High', 'Medium', 'Low'
    geom GEOMETRY(Point, 4326), -- Stores Lat/Lon
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Index for fast spatial queries (Find incidents within 3km)
CREATE INDEX IF NOT EXISTS incidents_geom_idx ON kochi_incidents USING GIST (geom);
