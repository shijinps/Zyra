// Zyra Sync API - Node.js Backend for Coolify
// Handles sync between Android app and PostgreSQL

const express = require('express');
const { Pool } = require('pg');
const app = express();

app.use(express.json({ limit: '10mb' }));

// PostgreSQL connection pool
const pool = new Pool({
    host: process.env.POSTGRES_HOST || 'localhost',
    port: process.env.POSTGRES_PORT || 5432,
    database: process.env.POSTGRES_DB || 'zyra_memory',
    user: process.env.POSTGRES_USER || 'postgres',
    password: process.env.POSTGRES_PASSWORD,
    max: 20,
    idleTimeoutMillis: 30000,
    connectionTimeoutMillis: 2000,
});

// API Key authentication middleware
const authenticate = (req, res, next) => {
    const apiKey = req.headers.authorization?.replace('Bearer ', '');
    if (apiKey !== process.env.API_KEY) {
        return res.status(401).json({ error: 'Unauthorized' });
    }
    next();
};

// Health check
app.get('/health', (req, res) => {
    res.json({ status: 'ok', timestamp: Date.now() });
});

// Sync conversations to cloud
app.post('/api/memory/sync', authenticate, async (req, res) => {
    const { device_id, conversations } = req.body;

    if (!device_id || !conversations || !Array.isArray(conversations)) {
        return res.status(400).json({ error: 'Invalid request' });
    }

    const client = await pool.connect();

    try {
        await client.query('BEGIN');

        let synced = 0;
        for (const conv of conversations) {
            const result = await client.query(`
                INSERT INTO zyra_conversations 
                (device_id, timestamp, user_input, zyra_response, intent_type, success, context)
                VALUES ($1, $2, $3, $4, $5, $6, $7)
                ON CONFLICT (device_id, timestamp) DO NOTHING
                RETURNING id
            `, [
                device_id,
                conv.timestamp,
                conv.user_input,
                conv.zyra_response,
                conv.intent_type,
                conv.success,
                conv.context || null
            ]);

            if (result.rowCount > 0) synced++;
        }

        await client.query('COMMIT');

        res.json({
            success: true,
            synced: synced,
            total: conversations.length
        });

    } catch (error) {
        await client.query('ROLLBACK');
        console.error('Sync error:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    } finally {
        client.release();
    }
});

// Pull conversations from cloud
app.get('/api/memory/pull', authenticate, async (req, res) => {
    const { device_id, since } = req.query;

    if (!device_id) {
        return res.status(400).json({ error: 'device_id required' });
    }

    try {
        const result = await pool.query(`
            SELECT 
                timestamp,
                user_input,
                zyra_response,
                intent_type,
                success,
                context
            FROM zyra_conversations
            WHERE device_id = $1 AND timestamp > $2
            ORDER BY timestamp DESC
            LIMIT 1000
        `, [device_id, since || 0]);

        res.json({
            success: true,
            conversations: result.rows,
            count: result.rows.length
        });

    } catch (error) {
        console.error('Pull error:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

// Sync actions to cloud
app.post('/api/memory/sync-actions', authenticate, async (req, res) => {
    const { device_id, actions } = req.body;

    if (!device_id || !actions || !Array.isArray(actions)) {
        return res.status(400).json({ error: 'Invalid request' });
    }

    const client = await pool.connect();

    try {
        await client.query('BEGIN');

        for (const action of actions) {
            await client.query(`
                INSERT INTO zyra_actions 
                (device_id, timestamp, action_type, action_data, location, success)
                VALUES ($1, $2, $3, $4, $5, $6)
            `, [
                device_id,
                action.timestamp,
                action.action_type,
                action.action_data,
                action.location || null,
                action.success
            ]);
        }

        await client.query('COMMIT');

        res.json({
            success: true,
            synced: actions.length
        });

    } catch (error) {
        await client.query('ROLLBACK');
        console.error('Action sync error:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    } finally {
        client.release();
    }
});

// Get statistics
app.get('/api/memory/stats', authenticate, async (req, res) => {
    const { device_id } = req.query;

    try {
        const conversationsCount = await pool.query(
            'SELECT COUNT(*) FROM zyra_conversations WHERE device_id = $1',
            [device_id]
        );

        const actionsCount = await pool.query(
            'SELECT COUNT(*) FROM zyra_actions WHERE device_id = $1',
            [device_id]
        );

        res.json({
            success: true,
            stats: {
                conversations: parseInt(conversationsCount.rows[0].count),
                actions: parseInt(actionsCount.rows[0].count)
            }
        });

    } catch (error) {
        console.error('Stats error:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
    console.log(`Zyra Sync API running on port ${PORT}`);
});
