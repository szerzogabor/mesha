package com.mesha.api.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GitHubLinkHeaderParserTest {

    @Test
    void returnsNullWhenHeaderIsNull() {
        assertThat(GitHubLinkHeaderParser.extractNextPageUrl(null)).isNull();
    }

    @Test
    void returnsNullWhenHeaderIsBlank() {
        assertThat(GitHubLinkHeaderParser.extractNextPageUrl("  ")).isNull();
    }

    @Test
    void returnsNullWhenNoNextRel() {
        String header = "<https://api.github.com/repos/owner/repo/pulls?page=1>; rel=\"first\", "
                + "<https://api.github.com/repos/owner/repo/pulls?page=1>; rel=\"prev\"";
        assertThat(GitHubLinkHeaderParser.extractNextPageUrl(header)).isNull();
    }

    @Test
    void parsesNextUrlFromLinkHeader() {
        String header = "<https://api.github.com/repos/owner/repo/pulls?per_page=100&page=2>; rel=\"next\", "
                + "<https://api.github.com/repos/owner/repo/pulls?per_page=100&page=5>; rel=\"last\"";
        assertThat(GitHubLinkHeaderParser.extractNextPageUrl(header))
                .isEqualTo("https://api.github.com/repos/owner/repo/pulls?per_page=100&page=2");
    }

    @Test
    void parsesNextUrlWhenItIsLastEntry() {
        String header = "<https://api.github.com/repos/owner/repo/pulls?per_page=100&page=1>; rel=\"prev\", "
                + "<https://api.github.com/repos/owner/repo/pulls?per_page=100&page=3>; rel=\"next\"";
        assertThat(GitHubLinkHeaderParser.extractNextPageUrl(header))
                .isEqualTo("https://api.github.com/repos/owner/repo/pulls?per_page=100&page=3");
    }
}
