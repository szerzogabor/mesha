package com.mesha.api.service;

import com.mesha.api.dto.BlocksConfigDto;
import com.mesha.api.model.Workspace;
import com.mesha.api.model.WorkspaceBlocksConfig;
import com.mesha.api.repository.WorkspaceBlocksConfigRepository;
import com.mesha.api.repository.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

@Service
public class BlocksConfigService {

    private static final Logger log = LoggerFactory.getLogger(BlocksConfigService.class);

    private final WorkspaceBlocksConfigRepository configRepository;
    private final WorkspaceRepository workspaceRepository;
    private final SecretCipher secretCipher;

    public BlocksConfigService(WorkspaceBlocksConfigRepository configRepository,
                               WorkspaceRepository workspaceRepository,
                               SecretCipher secretCipher) {
        this.configRepository = configRepository;
        this.workspaceRepository = workspaceRepository;
        this.secretCipher = secretCipher;
    }

    public Optional<BlocksConfigDto> getConfig(UUID workspaceId) {
        return configRepository.findByWorkspaceId(workspaceId)
                .map(BlocksConfigDto::from);
    }

    public boolean isConnected(UUID workspaceId) {
        return configRepository.existsByWorkspaceId(workspaceId);
    }

    @Transactional
    public BlocksConfigDto saveConfig(UUID workspaceId, String apiKey, String blocksWorkspaceId) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "apiKey must not be blank");
        }

        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));

        WorkspaceBlocksConfig config = configRepository.findByWorkspaceId(workspaceId)
                .orElseGet(() -> {
                    WorkspaceBlocksConfig c = new WorkspaceBlocksConfig();
                    c.setWorkspace(workspace);
                    return c;
                });

        config.setApiKeyEnc(secretCipher.encrypt(apiKey));
        config.setStatus("connected");
        if (blocksWorkspaceId != null && !blocksWorkspaceId.isBlank()) {
            config.setBlocksWorkspaceId(blocksWorkspaceId.trim());
        }
        config = configRepository.save(config);

        log.info("Blocks config saved workspaceId={} blocksWorkspaceId={}", workspaceId,
                config.getBlocksWorkspaceId() != null ? config.getBlocksWorkspaceId() : "not provided");
        return BlocksConfigDto.from(config);
    }

    @Transactional
    public void deleteConfig(UUID workspaceId) {
        if (!configRepository.existsByWorkspaceId(workspaceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No Blocks config for workspace");
        }
        configRepository.deleteByWorkspaceId(workspaceId);
        log.info("Blocks config deleted workspaceId={}", workspaceId);
    }

    public Optional<String> getApiKey(UUID workspaceId) {
        return configRepository.findByWorkspaceId(workspaceId)
                .map(config -> secretCipher.decrypt(config.getApiKeyEnc()));
    }

    public String decrypt(String encoded) {
        return secretCipher.decrypt(encoded);
    }
}
