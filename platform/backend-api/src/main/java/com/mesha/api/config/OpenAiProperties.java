package com.mesha.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the OpenAI / ChatGPT AI-draft provider. Endpoints, default
 * models, and the OAuth refresh parameters are overridable via environment
 * variables so they can be corrected without a redeploy — the ChatGPT-backend
 * and OAuth values are OpenAI/Codex internals and may change over time.
 */
@Component
@ConfigurationProperties(prefix = "ai.openai")
public class OpenAiProperties {

    /** Standard OpenAI REST API base (API_KEY mode). */
    private String apiBaseUrl = "https://api.openai.com";
    /** Default model for API_KEY mode. */
    private String apiModel = "gpt-4o-mini";

    /** ChatGPT backend base used by Codex (CHATGPT_TOKEN mode). */
    private String chatgptBaseUrl = "https://chatgpt.com/backend-api";
    /** Default model for CHATGPT_TOKEN mode. */
    private String chatgptModel = "gpt-5";

    /** OAuth token endpoint used to refresh an expired ChatGPT access token. */
    private String oauthTokenUrl = "https://auth.openai.com/oauth/token";
    /** Codex desktop's public OAuth client id (used for the refresh grant). */
    private String oauthClientId = "app_EMoamEEZ73f0CkXaXp7hrann";

    public String getApiBaseUrl() { return apiBaseUrl; }
    public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }
    public String getApiModel() { return apiModel; }
    public void setApiModel(String apiModel) { this.apiModel = apiModel; }
    public String getChatgptBaseUrl() { return chatgptBaseUrl; }
    public void setChatgptBaseUrl(String chatgptBaseUrl) { this.chatgptBaseUrl = chatgptBaseUrl; }
    public String getChatgptModel() { return chatgptModel; }
    public void setChatgptModel(String chatgptModel) { this.chatgptModel = chatgptModel; }
    public String getOauthTokenUrl() { return oauthTokenUrl; }
    public void setOauthTokenUrl(String oauthTokenUrl) { this.oauthTokenUrl = oauthTokenUrl; }
    public String getOauthClientId() { return oauthClientId; }
    public void setOauthClientId(String oauthClientId) { this.oauthClientId = oauthClientId; }
}
