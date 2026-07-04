package com.mesha.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesha.api.model.Issue;
import com.mesha.api.model.Project;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class IssueSseServiceTest {

    private UUID projectId;
    private Issue issue;
    private ObjectProvider<IssueSseService> selfProvider;
    private IssueSseService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        projectId = UUID.randomUUID();

        Project project = new Project();
        ReflectionTestUtils.setField(project, "id", projectId);
        project.setKey("MES");

        issue = new Issue();
        ReflectionTestUtils.setField(issue, "id", UUID.randomUUID());
        issue.setProject(project);
        issue.setNumber(1);
        issue.setTitle("Board should refresh");

        selfProvider = mock(ObjectProvider.class);
        // Mirror the Spring-configured mapper (JSR-310 for Instant fields).
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        service = spy(new IssueSseService(objectMapper, selfProvider));
        when(selfProvider.getObject()).thenReturn(service);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void broadcastUpdate_withoutTransaction_sendsImmediately() {
        service.subscribe(projectId);

        service.broadcastUpdate(issue);

        verify(service).sendToSubscribers(eq(projectId), any());
    }

    @Test
    void broadcastUpdate_withActiveTransaction_defersUntilAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.subscribe(projectId);

            service.broadcastUpdate(issue);

            // Nothing pushed while the transaction is still open — a subscriber's
            // refetch must not race ahead of the committed data.
            verify(service, never()).sendToSubscribers(any(), any());

            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }

            verify(service).sendToSubscribers(eq(projectId), any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void broadcastUpdate_withNoSubscribers_doesNotSend() {
        service.broadcastUpdate(issue);

        verify(service, never()).sendToSubscribers(any(), any());
    }
}
