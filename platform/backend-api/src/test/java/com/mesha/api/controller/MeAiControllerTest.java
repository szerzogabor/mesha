package com.mesha.api.controller;

import com.mesha.api.ai.AIDraftContent;
import com.mesha.api.ai.OpenAiAIDraftGenerator;
import com.mesha.api.ai.OpenAiCredential;
import com.mesha.api.dto.MeAiCompletionResponse;
import com.mesha.api.dto.MeAiPromptRequest;
import com.mesha.api.model.OpenAiAuthMode;
import com.mesha.api.model.User;
import com.mesha.api.service.OpenAiConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MeAiControllerTest {

    @Mock private OpenAiConfigService openAiConfigService;
    @Mock private OpenAiAIDraftGenerator openAiGenerator;

    private MeAiController controller;
    private User user;
    private UUID userId;
    private OpenAiCredential credential;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new MeAiController(openAiConfigService, openAiGenerator);
        userId = UUID.randomUUID();
        user = new User();
        ReflectionTestUtils.setField(user, "id", userId);
        credential = new OpenAiCredential(OpenAiAuthMode.API_KEY, "sk-x", null, null, null);
    }

    @Test
    void draft_resolvesCredentialAndGenerates() {
        AIDraftContent content = new AIDraftContent("t", "d", "- a", "[]", "MEDIUM", "", "", "");
        when(openAiConfigService.resolveCredential(userId)).thenReturn(credential);
        when(openAiGenerator.generate(eq("build login"), any())).thenReturn(content);

        var response = controller.draft(user, new MeAiPromptRequest("build login"));

        assertThat(response.getBody()).isSameAs(content);
        verify(openAiConfigService).resolveCredential(userId);
        verify(openAiGenerator).generate("build login", credential);
    }

    @Test
    void complete_resolvesCredentialAndReturnsText() {
        when(openAiConfigService.resolveCredential(userId)).thenReturn(credential);
        when(openAiGenerator.complete(eq("hello"), any())).thenReturn("world");

        var response = controller.complete(user, new MeAiPromptRequest("hello"));

        assertThat(response.getBody()).isEqualTo(new MeAiCompletionResponse("world"));
        verify(openAiGenerator).complete("hello", credential);
    }

    @Test
    void draft_notConfigured_propagates412() {
        when(openAiConfigService.resolveCredential(userId))
                .thenThrow(new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "not connected"));

        assertThatThrownBy(() -> controller.draft(user, new MeAiPromptRequest("x")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not connected");
        verifyNoInteractions(openAiGenerator);
    }
}
