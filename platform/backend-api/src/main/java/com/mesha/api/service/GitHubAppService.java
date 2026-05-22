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
import org.slf4j.MDC;
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
        log.debug("github_app_jwt_generate app_id={}", props.getAppId());
        try {
            PrivateKey privateKey = parsePrivateKey(props.getPrivateKey());
            Instant now = Instant.now();
            String jwt = Jwts.builder()
                    .issuer(String.valueOf(props.getAppId()))
                    .issuedAt(Date.from(now.minusSeconds(60)))
                    .expiration(Date.from(now.plusSeconds(540)))
                    .signWith(privateKey)
                    .compact();
            log.debug("github_app_jwt_generated app_id={}", props.getAppId());
            return jwt;
        } catch (Exception e) {
            log.error("github_app_jwt_error app_id={}", props.getAppId(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to generate GitHub App JWT: " + e.getMessage());
        }
    }

    /**
     * Obtains an installation access token for a specific installation.
     * These tokens expire after 1 hour and are used for API calls on behalf of the installation.
     */
    public String getInstallationToken(Long installationId) {
        MDC.put("githubInstallationId", String.valueOf(installationId));
        long startMs = System.currentTimeMillis();
        try {
            log.debug("github_installation_token_start installation_id={}", installationId);
            String appJwt = generateAppJwt();
            String endpoint = "/app/installations/" + installationId + "/access_tokens";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API + endpoint))
                    .header("Authorization", "Bearer " + appJwt)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long durationMs = System.currentTimeMillis() - startMs;
            log.debug("github_api_call endpoint={} status={} duration_ms={}", endpoint, response.statusCode(), durationMs);

            if (response.statusCode() != 201) {
                log.warn("github_installation_token_failed installation_id={} status={} duration_ms={}",
                        installationId, response.statusCode(), durationMs);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "GitHub API returned " + response.statusCode());
            }
            JsonNode node = objectMapper.readTree(response.body());
            log.debug("github_installation_token_obtained installation_id={} duration_ms={}", installationId, durationMs);
            return node.get("token").asText();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startMs;
            log.error("github_installation_token_error installation_id={} duration_ms={}", installationId, durationMs, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to get installation token: " + e.getMessage());
        } finally {
            MDC.remove("githubInstallationId");
        }
    }

    /**
     * Persists a new GitHub App installation linked to a workspace.
     * Called after the GitHub App install redirect comes back.
     */
    @Transactional
    public GitHubInstallation registerInstallation(Long installationId, UUID workspaceId) {
        MDC.put("githubInstallationId", String.valueOf(installationId));
        try {
            Optional<GitHubInstallation> existing = installationRepo.findByInstallationId(installationId);
            if (existing.isPresent()) {
                log.info("installation_already_registered installation_id={} workspace_id={}", installationId, workspaceId);
                return existing.get();
            }

            log.info("installation_register_start installation_id={} workspace_id={}", installationId, workspaceId);
            Workspace workspace = workspaceRepo.findById(workspaceId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));

            String appJwt = generateAppJwt();
            String endpoint = "/app/installations/" + installationId;
            long apiStartMs = System.currentTimeMillis();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API + endpoint))
                    .header("Authorization", "Bearer " + appJwt)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("github_api_call endpoint={} status={} duration_ms={}",
                    endpoint, response.statusCode(), System.currentTimeMillis() - apiStartMs);

            JsonNode node = objectMapper.readTree(response.body());

            GitHubInstallation installation = new GitHubInstallation();
            installation.setWorkspace(workspace);
            installation.setInstallationId(installationId);
            installation.setAppId(props.getAppId());
            installation.setAccountLogin(node.path("account").path("login").asText());
            installation.setAccountType(node.path("account").path("type").asText("User"));
            installation.setAccountAvatarUrl(node.path("account").path("avatar_url").asText(null));
            installation.setStatus("active");

            installation = installationRepo.save(installation);
            log.info("installation_registered installation_id={} account={} workspace_id={}",
                    installationId, installation.getAccountLogin(), workspaceId);
            return installation;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("installation_register_error installation_id={} workspace_id={}", installationId, workspaceId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to register installation: " + e.getMessage());
        } finally {
            MDC.remove("githubInstallationId");
        }
    }

    /**
     * Fetches the list of repositories accessible to this installation from GitHub.
     */
    public List<JsonNode> listInstallationRepositories(Long installationId) {
        MDC.put("githubInstallationId", String.valueOf(installationId));
        long startMs = System.currentTimeMillis();
        try {
            log.debug("installation_repos_list_start installation_id={}", installationId);
            String token = getInstallationToken(installationId);
            List<JsonNode> repos = new ArrayList<>();
            String nextUrl = GITHUB_API + "/installation/repositories?per_page=100";
            int pageCount = 0;

            while (nextUrl != null) {
                pageCount++;
                long pageStartMs = System.currentTimeMillis();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(nextUrl))
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/vnd.github+json")
                        .header("X-GitHub-Api-Version", "2022-11-28")
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                log.debug("github_api_call endpoint=/installation/repositories page={} status={} duration_ms={}",
                        pageCount, response.statusCode(), System.currentTimeMillis() - pageStartMs);

                JsonNode root = objectMapper.readTree(response.body());
                root.path("repositories").forEach(repos::add);

                // Follow GitHub's Link header for next page
                nextUrl = extractNextPageUrl(response.headers().firstValue("Link").orElse(null));
            }

            log.info("installation_repos_listed installation_id={} repo_count={} pages={} duration_ms={}",
                    installationId, repos.size(), pageCount, System.currentTimeMillis() - startMs);
            return repos;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("installation_repos_list_error installation_id={} duration_ms={}",
                    installationId, System.currentTimeMillis() - startMs, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to list repositories: " + e.getMessage());
        } finally {
            MDC.remove("githubInstallationId");
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
        MDC.put("githubInstallationId", String.valueOf(installationId));
        try {
            log.info("repository_connect_start workspace_id={} installation_id={} repo_id={}",
                    workspaceId, installationId, githubRepoId);

            GitHubInstallation installation = installationRepo.findByInstallationId(installationId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Installation not found"));

            Workspace workspace = workspaceRepo.findById(workspaceId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));

            Optional<GitHubRepository> existing = repositoryRepo.findByGithubRepoId(githubRepoId);
            if (existing.isPresent()) {
                GitHubRepository repo = existing.get();
                repo.setConnected(true);
                log.info("repository_reconnected repo={} workspace_id={}", repo.getFullName(), workspaceId);
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

            repo = repositoryRepo.save(repo);
            log.info("repository_connected repo={} workspace_id={} installation_id={}",
                    repo.getFullName(), workspaceId, installationId);
            return repo;
        } finally {
            MDC.remove("githubInstallationId");
        }
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
            log.info("installation_status_changed installation_id={} status=suspended account={}",
                    installationId, i.getAccountLogin());
        });
    }

    @Transactional
    public void markInstallationDeleted(Long installationId) {
        installationRepo.findByInstallationId(installationId).ifPresent(i -> {
            i.setStatus("deleted");
            installationRepo.save(i);
            log.info("installation_status_changed installation_id={} status=deleted account={}",
                    installationId, i.getAccountLogin());
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
