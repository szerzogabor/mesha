package com.mesha.api.controller;

import com.mesha.api.dto.OpenAiConfigDto;
import com.mesha.api.dto.SaveOpenAiConfigRequest;
import com.mesha.api.model.OpenAiAuthMode;
import com.mesha.api.service.OpenAiConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ConnectorOpenAiConfigControllerTest {

    @Mock private OpenAiConfigService openAiConfigService;

    private ConnectorOpenAiConfigController controller;
    private UUID userId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new ConnectorOpenAiConfigController(openAiConfigService);
        userId = UUID.randomUUID();
    }

    @Test
    void save_delegatesToServiceByUserId() {
        SaveOpenAiConfigRequest req = new SaveOpenAiConfigRequest(
                OpenAiAuthMode.CHATGPT_TOKEN, null, "access-xyz", "refresh-xyz", "acct-1", "gpt-5");
        OpenAiConfigDto dto = new OpenAiConfigDto(
                UUID.randomUUID(), userId, OpenAiAuthMode.CHATGPT_TOKEN, "acct-1", "gpt-5",
                "connected", Instant.now(), Instant.now());
        when(openAiConfigService.saveConfig(eq(userId), eq(req))).thenReturn(dto);

        var response = controller.save(userId, req);

        assertThat(response.getBody()).isSameAs(dto);
        verify(openAiConfigService).saveConfig(userId, req);
    }
}
