-- Store the Blocks workspace ID so session dashboard URLs can be constructed
-- in the format: {dashboardUrl}/app/{blocksWorkspaceId}/sessions/{providerSessionId}
ALTER TABLE workspace_blocks_config ADD COLUMN blocks_workspace_id VARCHAR(255);
