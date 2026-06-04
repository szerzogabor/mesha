package com.mesha.api.service;

final class GitHubLinkHeaderParser {

    private GitHubLinkHeaderParser() {}

    static String extractNextPageUrl(String linkHeader) {
        if (linkHeader == null || linkHeader.isBlank()) return null;
        for (String part : linkHeader.split(",")) {
            String[] segments = part.trim().split(";");
            if (segments.length == 2 && segments[1].trim().equals("rel=\"next\"")) {
                String url = segments[0].trim();
                return url.startsWith("<") && url.endsWith(">")
                        ? url.substring(1, url.length() - 1) : url;
            }
        }
        return null;
    }
}
