package com.mesha.api.dto;

/**
 * Response for resolving a model's direct, fetchable download URL — see
 * {@link com.mesha.api.service.LocalAiModelDownloadProxyService#resolveDownloadUrl}.
 *
 * @param url the resolved URL to fetch the artifact from directly
 */
public record ResolveDownloadUrlDto(String url) {
}
