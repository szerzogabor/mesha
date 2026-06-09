-- Support multiple AI sessions per issue and conversation-style messaging

-- Add message role to differentiate user vs AI vs system messages
ALTER TABLE blocks_messages ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'AI';

-- Add execution timestamps for better session tracking
ALTER TABLE blocks_sessions ADD COLUMN started_at TIMESTAMP;
ALTER TABLE blocks_sessions ADD COLUMN completed_at TIMESTAMP;
