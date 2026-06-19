package com.mesha.connector.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesha.connector.config.ConnectorProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectorTokenStoreTest {

    @TempDir
    Path tempDir;

    private ConnectorTokenStore newStore() {
        Path credentialsPath = tempDir.resolve("nested/credentials.json");
        ConnectorProperties properties = new ConnectorProperties("mesha-connector", "test", "http://localhost:8080", credentialsPath.toString(), "0.0.1-SNAPSHOT", tempDir.resolve("nested/agent.json").toString());
        return new ConnectorTokenStore(properties, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void save_thenLoad_roundTrips() {
        ConnectorTokenStore store = newStore();
        ConnectorCredentials credentials = new ConnectorCredentials(
                "mcat_abc", Instant.now().truncatedTo(ChronoUnit.SECONDS), "mcrt_def");

        store.save(credentials);
        Optional<ConnectorCredentials> loaded = store.load();

        assertThat(loaded).contains(credentials);
    }

    @Test
    void load_whenNoFileExists_returnsEmpty() {
        ConnectorTokenStore store = newStore();

        assertThat(store.load()).isEmpty();
    }

    @Test
    void save_createsFileWithOwnerOnlyPermissions() throws Exception {
        ConnectorTokenStore store = newStore();
        store.save(new ConnectorCredentials("mcat_abc", Instant.now(), "mcrt_def"));

        Path credentialsPath = tempDir.resolve("nested/credentials.json");
        assertThat(Files.isReadable(credentialsPath)).isTrue();
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(credentialsPath))).isEqualTo("rw-------");
    }

    @Test
    void clear_removesStoredCredentials() {
        ConnectorTokenStore store = newStore();
        store.save(new ConnectorCredentials("mcat_abc", Instant.now(), "mcrt_def"));

        store.clear();

        assertThat(store.load()).isEmpty();
    }
}
