package com.mesha.api.ai;

public interface AIProvider {
    AIDraftContent generateTicketDraft(String prompt);
}
