package com.mesha.connector.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesha.connector.config.ConnectorProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;

/**
 * Persists connector credentials to a local file (default {@code ~/.mesha/connector/credentials.json})
 * restricted to owner-only read/write permissions.
 */
@Component
public class ConnectorTokenStore {

    private final Path credentialsPath;
    private final ObjectMapper objectMapper;

    public ConnectorTokenStore(ConnectorProperties properties, ObjectMapper objectMapper) {
        this.credentialsPath = Path.of(properties.credentialsPath());
        this.objectMapper = objectMapper;
    }

    public synchronized void save(ConnectorCredentials credentials) {
        try {
            Path parent = credentialsPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tempFile = Files.createTempFile(parent, "credentials", ".tmp");
            objectMapper.writeValue(tempFile.toFile(), credentials);
            restrictToOwner(tempFile);
            Files.move(tempFile, credentialsPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new ConnectorAuthException("Failed to persist connector credentials", e);
        }
    }

    public synchronized Optional<ConnectorCredentials> load() {
        if (!Files.exists(credentialsPath)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(credentialsPath.toFile(), ConnectorCredentials.class));
        } catch (IOException e) {
            throw new ConnectorAuthException("Failed to read connector credentials", e);
        }
    }

    public synchronized void clear() {
        try {
            Files.deleteIfExists(credentialsPath);
        } catch (IOException e) {
            throw new ConnectorAuthException("Failed to clear connector credentials", e);
        }
    }

    private void restrictToOwner(Path file) throws IOException {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException e) {
            file.toFile().setReadable(false, false);
            file.toFile().setReadable(true, true);
            file.toFile().setWritable(false, false);
            file.toFile().setWritable(true, true);
        }
    }
}
