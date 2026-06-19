package com.mesha.connector.context;

import com.mesha.connector.config.TaskContextProperties;
import com.mesha.connector.session.dto.SessionContextResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the {@code task.md} brief a connector writes into a session's workspace before handing
 * it off for execution: title, a parsed breakdown of the ticket description (description,
 * requirements, acceptance criteria, ...), comments, and related tickets.
 */
@Component
public class SessionContextBuilder {

    private static final Pattern HEADING = Pattern.compile("^([A-Za-z][A-Za-z ]{1,40}):\\s*$");

    private final int maxDescriptionChars;

    public SessionContextBuilder(TaskContextProperties properties) {
        this.maxDescriptionChars = properties.maxDescriptionChars();
    }

    public String build(SessionContextResponse context) {
        StringBuilder md = new StringBuilder();
        String identifier = context.issueIdentifier() != null ? context.issueIdentifier() : context.issueId().toString();
        md.append("# ").append(identifier).append(": ").append(orEmpty(context.issueTitle())).append("\n\n");
        md.append("**Status:** ").append(orEmpty(context.issueStatus())).append("  \n");
        md.append("**Priority:** ").append(orEmpty(context.issuePriority())).append("\n\n");

        String description = truncate(context.issueDescription());
        List<Section> sections = parseSections(description);
        appendDescriptionSections(md, sections);

        if (context.instructions() != null && !context.instructions().isBlank()) {
            md.append("## Additional Instructions\n\n").append(context.instructions().trim()).append("\n\n");
        }

        appendComments(md, context);
        appendRelatedIssues(md, context);
        return md.toString();
    }

    private void appendDescriptionSections(StringBuilder md, List<Section> sections) {
        if (sections.isEmpty()) {
            md.append("## Description\n\n_No description provided._\n\n");
        } else {
            for (Section section : sections) {
                md.append("## ").append(section.label()).append("\n\n");
                md.append(section.body().isBlank() ? "_Not specified._" : section.body()).append("\n\n");
            }
        }
        boolean hasAcceptanceCriteria = sections.stream().anyMatch(s -> s.label().equalsIgnoreCase("Acceptance Criteria"));
        if (!hasAcceptanceCriteria) {
            md.append("## Acceptance Criteria\n\n_Not specified._\n\n");
        }
    }

    private void appendComments(StringBuilder md, SessionContextResponse context) {
        md.append("## Comments\n\n");
        if (context.comments() == null || context.comments().isEmpty()) {
            md.append("_No comments._\n\n");
            return;
        }
        for (var comment : context.comments()) {
            md.append("- **").append(comment.author()).append("**");
            if (comment.createdAt() != null) {
                md.append(" (").append(comment.createdAt()).append(")");
            }
            md.append(": ").append(comment.body().strip().replace("\n", " ")).append("\n");
        }
        md.append("\n");
    }

    private void appendRelatedIssues(StringBuilder md, SessionContextResponse context) {
        md.append("## Related Tickets\n\n");
        if (context.relatedIssues() == null || context.relatedIssues().isEmpty()) {
            md.append("_No related tickets._\n");
            return;
        }
        for (var related : context.relatedIssues()) {
            md.append("- **").append(related.identifier() != null ? related.identifier() : "?").append("** — ")
                    .append(orEmpty(related.title())).append(" (").append(related.linkType())
                    .append(", ").append(orEmpty(related.status())).append(")\n");
        }
    }

    /** Splits a description into ordered sections on bare "Heading:" lines, e.g. the format used by ticket templates. */
    private List<Section> parseSections(String description) {
        List<Section> sections = new ArrayList<>();
        if (description == null || description.isBlank()) {
            return sections;
        }
        String currentLabel = null;
        StringBuilder currentBody = new StringBuilder();
        for (String line : description.split("\n", -1)) {
            Matcher m = HEADING.matcher(line.strip());
            if (m.matches()) {
                flushSection(sections, currentLabel, currentBody);
                currentLabel = m.group(1).trim();
                currentBody = new StringBuilder();
            } else {
                currentBody.append(line).append("\n");
            }
        }
        flushSection(sections, currentLabel, currentBody);
        return sections;
    }

    private void flushSection(List<Section> sections, String label, StringBuilder body) {
        String content = body.toString().strip();
        if (label != null) {
            sections.add(new Section(label, content));
        } else if (!content.isBlank()) {
            sections.add(new Section("Description", content));
        }
    }

    private String truncate(String text) {
        if (text == null || text.length() <= maxDescriptionChars) {
            return text;
        }
        int omitted = text.length() - maxDescriptionChars;
        return text.substring(0, maxDescriptionChars) + "\n\n_[description truncated — " + omitted + " characters omitted]_";
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private record Section(String label, String body) {}
}
