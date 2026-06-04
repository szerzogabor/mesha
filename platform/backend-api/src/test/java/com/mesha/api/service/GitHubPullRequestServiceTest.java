package com.mesha.api.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubPullRequestServiceTest {

    private final GitHubPullRequestService service = new GitHubPullRequestService(null, null, null, null, null);

    @Test
    void extractNextPageUrl_returnsNullWhenHeaderIsNull() {
        assertThat(service.extractNextPageUrl(null)).isNull();
    }

    @Test
    void extractNextPageUrl_returnsNullWhenHeaderIsBlank() {
        assertThat(service.extractNextPageUrl("  ")).isNull();
    }

    @Test
    void extractNextPageUrl_returnsNullWhenNoNextRel() {
        String header = "<https://api.github.com/repos/owner/repo/pulls?page=1>; rel=\"first\", "
                + "<https://api.github.com/repos/owner/repo/pulls?page=1>; rel=\"prev\"";
        assertThat(service.extractNextPageUrl(header)).isNull();
    }

    @Test
    void extractNextPageUrl_parsesNextUrlFromLinkHeader() {
        String header = "<https://api.github.com/repos/owner/repo/pulls?per_page=100&page=2>; rel=\"next\", "
                + "<https://api.github.com/repos/owner/repo/pulls?per_page=100&page=5>; rel=\"last\"";
        assertThat(service.extractNextPageUrl(header))
                .isEqualTo("https://api.github.com/repos/owner/repo/pulls?per_page=100&page=2");
    }

    @Test
    void extractNextPageUrl_parsesNextUrlWhenItIsLastEntry() {
        String header = "<https://api.github.com/repos/owner/repo/pulls?per_page=100&page=1>; rel=\"prev\", "
                + "<https://api.github.com/repos/owner/repo/pulls?per_page=100&page=3>; rel=\"next\"";
        assertThat(service.extractNextPageUrl(header))
                .isEqualTo("https://api.github.com/repos/owner/repo/pulls?per_page=100&page=3");
    }
}
