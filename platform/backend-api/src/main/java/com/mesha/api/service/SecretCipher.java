package com.mesha.api.service;

import com.mesha.api.config.BlocksEncryptionProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Reusable AES/CBC symmetric encryption for secrets stored at rest.
 *
 * <p>The scheme (SHA-256-derived 256-bit key, random 16-byte IV prepended to the
 * ciphertext, Base64-encoded) matches the original inline implementation in
 * {@code BlocksConfigService} so existing {@code workspace_blocks_config.api_key_enc}
 * values remain decryptable. The key is derived from {@code BLOCKS_ENCRYPTION_SECRET}.
 */
@Component
public class SecretCipher {

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String INSECURE_DEFAULT_SECRET = "default-insecure-key-change-me!!";

    private final BlocksEncryptionProperties encryptionProps;

    public SecretCipher(BlocksEncryptionProperties encryptionProps) {
        this.encryptionProps = encryptionProps;
    }

    private byte[] deriveKey() {
        try {
            String secret = encryptionProps.getSecret();
            if (secret == null || secret.isBlank()) {
                secret = INSECURE_DEFAULT_SECRET;
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Arrays.copyOf(digest.digest(secret.getBytes(StandardCharsets.UTF_8)), 32);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive encryption key", e);
        }
    }

    public String encrypt(String plaintext) {
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
