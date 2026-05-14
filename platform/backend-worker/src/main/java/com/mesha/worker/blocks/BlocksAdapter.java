package com.mesha.worker.blocks;

import com.mesha.worker.orchestration.ProviderAdapter;
import com.mesha.worker.orchestration.SessionRequest;
import com.mesha.worker.orchestration.SessionResult;
import org.springframework.stereotype.Component;

@Component
public class BlocksAdapter implements ProviderAdapter {

    @Override
    public String providerName() {
        return "blocks";
    }

    @Override
    public SessionResult createSession(SessionRequest request) {
        // TODO: implement Blocks API session creation
        throw new UnsupportedOperationException("Blocks session creation not yet implemented");
    }

    @Override
    public SessionResult pollSession(String providerSessionId) {
        // TODO: implement Blocks API session polling (final_message)
        throw new UnsupportedOperationException("Blocks session polling not yet implemented");
    }
}
