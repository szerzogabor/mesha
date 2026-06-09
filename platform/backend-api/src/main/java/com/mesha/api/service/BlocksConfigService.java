package com.mesha.api.service;

import com.mesha.api.config.BlocksEncryptionProperties;
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

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class BlocksConfigService {

    private static final Logger log = LoggerFactory.getLogger(BlocksConfigService.class);
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";

    private final WorkspaceBlocksConfigRepository configRepository;
    private final WorkspaceRepository workspaceRepository;
    private final BlocksEncryptionProperties encryptionProps;

    public BlocksConfigService(WorkspaceBlocksConfigRepository configRepository,
                               WorkspaceRepository workspaceRepository,
                               BlocksEncryptionProperties encryptionProps) {
        this.configRepository = configRepository;
        this.workspaceRepository = workspaceRepository;
        this.encryptionProps = encryptionProps;
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

        config.setApiKeyEnc(encrypt(apiKey));
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

    private byte[] deriveKey() {
        try {
            String secret = encryptionProps.getSecret();
            if (secret == null || secret.isBlank()) {
                secret = "default-insecure-key-change-me!!";
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Arrays.copyOf(digest.digest(secret.getBytes(StandardCharsets.UTF_8)), 32);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive encryption key", e);
        }
    }

    private String encrypt(String plaintext) {
        try {
            byte[] key = deriveKey();
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] iv = Arrays.copyOfRange(combined, 0, 16);
            byte[] encrypted = Arrays.copyOfRange(combined, 16, combined.length);

            byte[] key = deriveKey();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));

            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }
}
