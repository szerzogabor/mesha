package com.mesha.connector.context;

import com.mesha.connector.config.TaskContextProperties;
import com.mesha.connector.session.dto.SessionContextResponse;
import com.mesha.connector.session.dto.SessionContextResponse.CommentSummary;
import com.mesha.connector.session.dto.SessionContextResponse.RelatedIssueSummary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SessionContextBuilderTest {

    private final SessionContextBuilder builder = new SessionContextBuilder(new TaskContextProperties(20_000));

    @Test
    void build_includesTitleStatusAndPriority() {
        SessionContextResponse context = context("MES-123", "Fix the login bug", "Open", "High", null, null, null, null);

        String markdown = builder.build(context);

        assertThat(markdown).contains("# MES-123: Fix the login bug");
        assertThat(markdown).contains("**Status:** Open");
        assertThat(markdown).contains("**Priority:** High");
    }

    @Test
    void build_parsesHeadingSectionsFromDescription() {
        String description = "Description:\nUsers can't log in.\n\nAcceptance Criteria:\n- Login succeeds\n- Error shown on failure\n";
        SessionContextResponse context = context("MES-123", "Fix login", "Open", "High", description, null, null, null);

        String markdown = builder.build(context);

        assertThat(markdown).contains("## Description\n\nUsers can't log in.");
        assertThat(markdown).contains("## Acceptance Criteria\n\n- Login succeeds\n- Error shown on failure");
    }

    @Test
    void build_noDescription_addsPlaceholderAndAcceptanceCriteria() {
        SessionContextResponse context = context("MES-123", "Fix login", "Open", "High", null, null, null, null);

        String markdown = builder.build(context);

        assertThat(markdown).contains("## Description\n\n_No description provided._");
        assertThat(markdown).contains("## Acceptance Criteria\n\n_Not specified._");
    }

    @Test
    void build_descriptionWithoutAcceptanceCriteria_stillAddsPlaceholderSection() {
        SessionContextResponse context = context("MES-123", "Fix login", "Open", "High", "Just some free text.", null, null, null);

        String markdown = builder.build(context);

        assertThat(markdown).contains("## Description\n\nJust some free text.");
        assertThat(markdown).contains("## Acceptance Criteria\n\n_Not specified._");
    }

    @Test
    void build_includesInstructionsWhenPresent() {
        SessionContextResponse context = context("MES-123", "Fix login", "Open", "High", null, "Use the existing auth module.", null, null);

        String markdown = builder.build(context);

        assertThat(markdown).contains("## Additional Instructions\n\nUse the existing auth module.");
    }

    @Test
    void build_includesComments() {
        List<CommentSummary> comments = List.of(new CommentSummary("alice", "Looks good\nto me", null));
        SessionContextResponse context = context("MES-123", "Fix login", "Open", "High", null, null, comments, null);

        String markdown = builder.build(context);

        assertThat(markdown).contains("## Comments\n\n- **alice**: Looks good to me");
    }

    @Test
    void build_noComments_showsPlaceholder() {
        SessionContextResponse context = context("MES-123", "Fix login", "Open", "High", null, null, null, null);

        String markdown = builder.build(context);

        assertThat(markdown).contains("## Comments\n\n_No comments._");
    }

    @Test
    void build_includesRelatedIssues() {
        List<RelatedIssueSummary> related = List.of(new RelatedIssueSummary("MES-124", "Related bug", "Open", "BLOCKS"));
        SessionContextResponse context = context("MES-123", "Fix login", "Open", "High", null, null, null, related);

        String markdown = builder.build(context);

        assertThat(markdown).contains("## Related Tickets\n\n- **MES-124** — Related bug (BLOCKS, Open)");
    }

    @Test
    void build_truncatesLongDescription() {
        SessionContextBuilder shortLimitBuilder = new SessionContextBuilder(new TaskContextProperties(10));
        String longDescription = "a".repeat(50);
        SessionContextResponse context = context("MES-123", "Fix login", "Open", "High", longDescription, null, null, null);

        String markdown = shortLimitBuilder.build(context);

        assertThat(markdown).contains("description truncated — 40 characters omitted");
    }

    @Test
    void build_usesIssueIdWhenIdentifierMissing() {
        UUID issueId = UUID.randomUUID();
        SessionContextResponse context = new SessionContextResponse(
                UUID.randomUUID(), issueId, null, "Fix login", null, "Open", "High", null, null, null, null);

        String markdown = builder.build(context);

        assertThat(markdown).contains("# " + issueId + ": Fix login");
    }

    private static SessionContextResponse context(String identifier, String title, String status, String priority,
                                                    String description, String instructions,
                                                    List<CommentSummary> comments, List<RelatedIssueSummary> related) {
        return new SessionContextResponse(
                UUID.randomUUID(), UUID.randomUUID(), identifier, title, description, status, priority,
                instructions, comments, related, null);
    }
}
