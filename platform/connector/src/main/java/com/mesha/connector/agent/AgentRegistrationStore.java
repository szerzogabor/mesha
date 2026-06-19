package com.mesha.connector.agent;

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
 * Persists the locally registered agent's identity to a local file (default
 * {@code ~/.mesha/connector/agent.json}) restricted to owner-only read/write permissions,
 * so a restarted connector can reuse the same agent identity for heartbeats.
 */
@Component
public class AgentRegistrationStore {

    private final Path registrationPath;
    private final ObjectMapper objectMapper;

    public AgentRegistrationStore(ConnectorProperties properties, ObjectMapper objectMapper) {
        this.registrationPath = Path.of(properties.agentRegistrationPath());
        this.objectMapper = objectMapper;
    }

    public synchronized void save(AgentRegistration registration) {
        Path tempFile = null;
        try {
            Path parent = registrationPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            tempFile = Files.createTempFile(parent, "agent", ".tmp");
            restrictToOwner(tempFile);
            objectMapper.writeValue(tempFile.toFile(), registration);
            Files.move(tempFile, registrationPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            tempFile = null;
        } catch (IOException e) {
            throw new AgentRegistrationException("Failed to persist agent registration", e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // best-effort cleanup; the primary failure is already surfaced above
                }
            }
        }
    }

    public synchronized Optional<AgentRegistration> load() {
        if (!Files.exists(registrationPath)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(registrationPath.toFile(), AgentRegistration.class));
        } catch (IOException e) {
            throw new AgentRegistrationException("Failed to read agent registration", e);
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
