package com.mesha.api.worker.scheduling;

import com.mesha.api.repository.WorkspaceBlocksConfigRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves and decrypts the per-workspace Blocks API key so that the polling
 * scheduler can authenticate session-creation requests with the correct credential.
 * Uses the same AES/CBC/PKCS5 scheme as BlocksConfigService.
 */
@Service
class BlocksApiKeyService {

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";

    private final WorkspaceBlocksConfigRepository configRepo;
    private final String encryptionSecret;

    BlocksApiKeyService(WorkspaceBlocksConfigRepository configRepo,
                        @Value("${blocks.encryption.secret:}") String encryptionSecret) {
        this.configRepo = configRepo;
        this.encryptionSecret = encryptionSecret;
    }

    Optional<String> resolveApiKey(UUID issueId) {
        return configRepo.findApiKeyEncByIssueId(issueId)
                .map(this::decrypt);
    }

    Optional<String> resolveBlocksWorkspaceId(UUID issueId) {
        return configRepo.findBlocksWorkspaceIdByIssueId(issueId)
                .filter(id -> id != null && !id.isBlank());
    }

    private String decrypt(String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] iv = Arrays.copyOfRange(combined, 0, 16);
            byte[] encrypted = Arrays.copyOfRange(combined, 16, combined.length);

            byte[] key = deriveKey();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt Blocks API key", e);
        }
    }

    private byte[] deriveKey() throws Exception {
        String secret = (encryptionSecret == null || encryptionSecret.isBlank())
                ? "default-insecure-key-change-me!!"
                : encryptionSecret;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return Arrays.copyOf(digest.digest(secret.getBytes(StandardCharsets.UTF_8)), 32);
    }
}
