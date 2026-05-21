package com.mesha.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesha.api.config.GitHubAppProperties;
import com.mesha.api.dto.GitHubInstallationDto;
import com.mesha.api.dto.GitHubRepositoryDto;
import com.mesha.api.model.GitHubInstallation;
import com.mesha.api.model.GitHubRepository;
import com.mesha.api.model.Workspace;
import com.mesha.api.repository.GitHubInstallationRepository;
import com.mesha.api.repository.GitHubRepositoryRepository;
import com.mesha.api.repository.WorkspaceRepository;
import io.jsonwebtoken.Jwts;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class GitHubAppService {

    private static final Logger log = LoggerFactory.getLogger(GitHubAppService.class);
    private static final String GITHUB_API = "https://api.github.com";

    private final GitHubAppProperties props;
    private final GitHubInstallationRepository installationRepo;
    private final GitHubRepositoryRepository repositoryRepo;
    private final WorkspaceRepository workspaceRepo;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GitHubAppService(GitHubAppProperties props,
                            GitHubInstallationRepository installationRepo,
                            GitHubRepositoryRepository repositoryRepo,
                            WorkspaceRepository workspaceRepo,
                            ObjectMapper objectMapper) {
        this.props = props;
        this.installationRepo = installationRepo;
        this.repositoryRepo = repositoryRepo;
        this.workspaceRepo = workspaceRepo;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Generates a short-lived JWT for authenticating as the GitHub App itself.
     * Used to generate installation access tokens.
     */
    public String generateAppJwt() {
        try {
            PrivateKey privateKey = parsePrivateKey(props.getPrivateKey());
            Instant now = Instant.now();
            return Jwts.builder()
                    .issuer(String.valueOf(props.getAppId()))
                    .issuedAt(Date.from(now.minusSeconds(60)))
                    .expiration(Date.from(now.plusSeconds(540)))
                    .signWith(privateKey)
                    .compact();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to generate GitHub App JWT: " + e.getMessage());
        }
    }

    /**
     * Obtains an installation access token for a specific installation.
     * These tokens expire after 1 hour and are used for API calls on behalf of the installation.
     */
    public String getInstallationToken(Long installationId) {
        try {
            String appJwt = generateAppJwt();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API + "/app/installations/" + installationId + "/access_tokens"))
                    .header("Authorization", "Bearer " + appJwt)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 201) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "GitHub API returned " + response.statusCode());
            }
            JsonNode node = objectMapper.readTree(response.body());
            return node.get("token").asText();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to get installation token: " + e.getMessage());
        }
    }

    /**
     * Persists a new GitHub App installation linked to a workspace.
     * Called after the GitHub App install redirect comes back.
     */
    @Transactional
    public GitHubInstallation registerInstallation(Long installationId, UUID workspaceId) {
        Optional<GitHubInstallation> existing = installationRepo.findByInstallationId(installationId);
        if (existing.isPresent()) {
            return existing.get();
        }

        Workspace workspace = workspaceRepo.findById(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));

        try {
            String appJwt = generateAppJwt();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API + "/app/installations/" + installationId))
                    .header("Authorization", "Bearer " + appJwt)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode node = objectMapper.readTree(response.body());

            GitHubInstallation installation = new GitHubInstallation();
            installation.setWorkspace(workspace);
            installation.setInstallationId(installationId);
            installation.setAppId(props.getAppId());
            installation.setAccountLogin(node.path("account").path("login").asText());
            installation.setAccountType(node.path("account").path("type").asText("User"));
            installation.setAccountAvatarUrl(node.path("account").path("avatar_url").asText(null));
            installation.setStatus("active");

            return installationRepo.save(installation);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to register installation: " + e.getMessage());
        }
    }

    /**
     * Fetches the list of repositories accessible to this installation from GitHub.
     */
    public List<JsonNode> listInstallationRepositories(Long installationId) {
        try {
            String token = getInstallationToken(installationId);
            List<JsonNode> repos = new ArrayList<>();
            String nextUrl = GITHUB_API + "/installation/repositories?per_page=100";

            while (nextUrl != null) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(nextUrl))
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/vnd.github+json")
                        .header("X-GitHub-Api-Version", "2022-11-28")
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                JsonNode root = objectMapper.readTree(response.body());
                root.path("repositories").forEach(repos::add);

                // Follow GitHub's Link header for next page
                nextUrl = extractNextPageUrl(response.headers().firstValue("Link").orElse(null));
            }

            return repos;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to list repositories: " + e.getMessage());
        }
    }

    private String extractNextPageUrl(String linkHeader) {
        if (linkHeader == null) return null;
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

    /**
     * Connects a repository to the workspace — upserts based on githubRepoId.
     */
    @Transactional
    public GitHubRepository connectRepository(UUID workspaceId, Long installationId, Long githubRepoId) {
        GitHubInstallation installation = installationRepo.findByInstallationId(installationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Installation not found"));

        Workspace workspace = workspaceRepo.findById(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));

        Optional<GitHubRepository> existing = repositoryRepo.findByGithubRepoId(githubRepoId);
        if (existing.isPresent()) {
            GitHubRepository repo = existing.get();
            repo.setConnected(true);
            return repositoryRepo.save(repo);
        }

        List<JsonNode> repos = listInstallationRepositories(installationId);
        JsonNode repoNode = repos.stream()
                .filter(r -> r.path("id").asLong() == githubRepoId)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Repository not accessible by this installation"));

        GitHubRepository repo = new GitHubRepository();
        repo.setInstallation(installation);
        repo.setWorkspace(workspace);
        repo.setGithubRepoId(githubRepoId);
        repo.setOwner(repoNode.path("owner").path("login").asText());
        repo.setName(repoNode.path("name").asText());
        repo.setFullName(repoNode.path("full_name").asText());
        repo.setIsPrivate(repoNode.path("private").asBoolean(false));
        repo.setDefaultBranch(repoNode.path("default_branch").asText("main"));
        repo.setDescription(repoNode.path("description").asText(null));
        repo.setHtmlUrl(repoNode.path("html_url").asText());
        repo.setConnected(true);

        return repositoryRepo.save(repo);
    }

    public List<GitHubInstallationDto> listInstallations(UUID workspaceId) {
        return installationRepo.findAllByWorkspaceId(workspaceId)
                .stream().map(GitHubInstallationDto::from).toList();
    }

    @Transactional
    public void markInstallationSuspended(Long installationId) {
        installationRepo.findByInstallationId(installationId).ifPresent(i -> {
            i.setStatus("suspended");
            installationRepo.save(i);
        });
    }

    @Transactional
    public void markInstallationDeleted(Long installationId) {
        installationRepo.findByInstallationId(installationId).ifPresent(i -> {
            i.setStatus("deleted");
            installationRepo.save(i);
        });
    }

    private PrivateKey parsePrivateKey(String pem) throws Exception {
        try (PEMParser parser = new PEMParser(new StringReader(pem.replace("\\n", "\n")))) {
            Object obj = parser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            if (obj instanceof PEMKeyPair keyPair) {
                return converter.getPrivateKey(keyPair.getPrivateKeyInfo());
            }
            throw new IllegalArgumentException("Unsupported PEM object: " + obj.getClass().getSimpleName());
        }
    }
}
