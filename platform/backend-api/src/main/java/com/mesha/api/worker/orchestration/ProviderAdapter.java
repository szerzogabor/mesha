package com.mesha.api.worker.orchestration;

public interface ProviderAdapter {

    String providerName();

    SessionResult createSession(SessionRequest request);

    SessionResult pollSession(String providerSessionId);
}
