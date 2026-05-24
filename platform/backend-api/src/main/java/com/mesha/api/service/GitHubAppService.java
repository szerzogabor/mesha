package com.mesha.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesha.api.config.GitHubAppProperties;
import com.mesha.api.dto.AvailableRepositoryDto;
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
        log.debug("Generating GitHub App JWT appId={}", props.getAppId());
        try {
            PrivateKey privateKey = parsePrivateKey(props.getPrivateKey());
            Instant now = Instant.now();
            String jwt = Jwts.builder()
                    .issuer(String.valueOf(props.getAppId()))
                    .issuedAt(Date.from(now.minusSeconds(60)))
                    .expiration(Date.from(now.plusSeconds(540)))
                    .signWith(privateKey)
                    .compact();
            log.debug("GitHub App JWT generated appId={}", props.getAppId());
            return jwt;
        } catch (Exception e) {
            log.error("Failed to generate GitHub App JWT appId={}: {}", props.getAppId(), e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to generate GitHub App JWT: " + e.getMessage());
        }
    }

    /**
     * Obtains an installation access token for a specific installation.
     * These tokens expire after 1 hour and are used for API calls on behalf of the installation.
     */
    public String getInstallationToken(Long installationId) {
        log.debug("Requesting installation access token installationId={}", installationId);
        try {
            String appJwt = generateAppJwt();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API + "/app/installations/" + installationId + "/access_tokens"))
                    .header("Authorization", "Bearer " + appJwt)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            long start = System.currentTimeMillis();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long durationMs = System.currentTimeMillis() - start;

            if (response.statusCode() != 201) {
                log.error("GitHub API returned unexpected status for installation token installationId={} httpStatus={} durationMs={}",
                        installationId, response.statusCode(), durationMs);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "GitHub API returned " + response.statusCode());
            }
            JsonNode node = objectMapper.readTree(response.body());
            log.debug("Installation access token obtained installationId={} durationMs={}", installationId, durationMs);
            return node.get("token").asText();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get installation token installationId={}: {}", installationId, e.getMessage(), e);
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
        log.info("Registering GitHub App installation installationId={} workspaceId={}", installationId, workspaceId);

        Optional<GitHubInstallation> existing = installationRepo.findByInstallationId(installationId);
        if (existing.isPresent()) {
            log.info("Installation already registered installationId={} workspaceId={}", installationId, workspaceId);
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

            long start = System.currentTimeMillis();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long durationMs = System.currentTimeMillis() - start;
            log.debug("GitHub App installation details fetched installationId={} httpStatus={} durationMs={}",
                    installationId, response.statusCode(), durationMs);

            JsonNode node = objectMapper.readTree(response.body());
            String accountLogin = node.path("account").path("login").asText();
            String accountType = node.path("account").path("type").asText("User");

            GitHubInstallation installation = new GitHubInstallation();
            installation.setWorkspace(workspace);
            installation.setInstallationId(installationId);
            installation.setAppId(props.getAppId());
            installation.setAccountLogin(accountLogin);
            installation.setAccountType(accountType);
            installation.setAccountAvatarUrl(node.path("account").path("avatar_url").asText(null));
            installation.setStatus("active");

            installation = installationRepo.save(installation);
            log.info("GitHub App installation registered installationId={} accountLogin={} accountType={} workspaceId={}",
                    installationId, accountLogin, accountType, workspaceId);
            return installation;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to register installation installationId={} workspaceId={}: {}",
                    installationId, workspaceId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to register installation: " + e.getMessage());
        }
    }

    /**
     * Fetches the list of repositories accessible to this installation from GitHub.
     */
    public List<JsonNode> listInstallationRepositories(Long installationId) {
        log.debug("Listing installation repositories installationId={}", installationId);
        try {
            String token = getInstallationToken(installationId);
            List<JsonNode> repos = new ArrayList<>();
            String nextUrl = GITHUB_API + "/installation/repositories?per_page=100";
            int page = 0;

            while (nextUrl != null) {
                page++;
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(nextUrl))
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/vnd.github+json")
                        .header("X-GitHub-Api-Version", "2022-11-28")
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                JsonNode root = objectMapper.readTree(response.body());
                int countBefore = repos.size();
                root.path("repositories").forEach(repos::add);
                log.debug("Fetched installation repositories page installationId={} page={} added={} total={}",
                        installationId, page, repos.size() - countBefore, repos.size());

                nextUrl = extractNextPageUrl(response.headers().firstValue("Link").orElse(null));
            }

            log.info("Listed installation repositories installationId={} totalCount={}", installationId, repos.size());
            return repos;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to list installation repositories installationId={}: {}", installationId, e.getMessage(), e);
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
        log.info("Connecting repository workspaceId={} installationId={} githubRepoId={}",
                workspaceId, installationId, githubRepoId);

        GitHubInstallation installation = installationRepo.findByInstallationId(installationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Installation not found"));

        Workspace workspace = workspaceRepo.findById(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));

        Optional<GitHubRepository> existing = repositoryRepo.findByGithubRepoId(githubRepoId);
        if (existing.isPresent()) {
            GitHubRepository repo = existing.get();
            repo.setConnected(true);
            log.info("Re-connecting existing repository fullName={} workspaceId={}", repo.getFullName(), workspaceId);
            return repositoryRepo.save(repo);
        }

        List<JsonNode> repos = listInstallationRepositories(installationId);
        JsonNode repoNode = repos.stream()
                .filter(r -> r.path("id").asLong() == githubRepoId)
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("Repository not accessible by installation installationId={} githubRepoId={}",
                            installationId, githubRepoId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Repository not accessible by this installation");
                });

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
        log.info("Repository connected fullName={} workspaceId={} installationId={}",
                repo.getFullName(), workspaceId, installationId);
        return repo;
    }

    /**
     * Returns the list of repositories accessible to an installation, formatted for the frontend.
     */
    public List<AvailableRepositoryDto> listAvailableRepositories(Long installationId, UUID workspaceId) {
        log.debug("Listing available repositories for installationId={} workspaceId={}", installationId, workspaceId);
        installationRepo.findByInstallationId(installationId)
                .filter(i -> i.getWorkspace().getId().equals(workspaceId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Installation not found for this workspace"));
        List<AvailableRepositoryDto> repos = listInstallationRepositories(installationId)
                .stream().map(AvailableRepositoryDto::from).toList();
        log.debug("Listed available repositories installationId={} count={}", installationId, repos.size());
        return repos;
    }

    public List<GitHubInstallationDto> listInstallations(UUID workspaceId) {
        log.debug("Listing installations workspaceId={}", workspaceId);
        List<GitHubInstallationDto> installations = installationRepo.findAllByWorkspaceId(workspaceId)
                .stream().map(GitHubInstallationDto::from).toList();
        log.debug("Listed installations workspaceId={} count={}", workspaceId, installations.size());
        return installations;
    }

    @Transactional
    public void markInstallationActive(Long installationId) {
        log.info("Marking installation active installationId={}", installationId);
        installationRepo.findByInstallationId(installationId).ifPresent(i -> {
            i.setStatus("active");
            installationRepo.save(i);
            log.info("Installation marked active installationId={}", installationId);
        });
    }

    @Transactional
    public void markInstallationSuspended(Long installationId) {
        log.info("Marking installation suspended installationId={}", installationId);
        installationRepo.findByInstallationId(installationId).ifPresent(i -> {
            i.setStatus("suspended");
            installationRepo.save(i);
            log.info("Installation suspended installationId={}", installationId);
        });
    }

    @Transactional
    public void markInstallationDeleted(Long installationId) {
        log.info("Marking installation deleted installationId={}", installationId);
        installationRepo.findByInstallationId(installationId).ifPresent(i -> {
            i.setStatus("deleted");
            installationRepo.save(i);
            log.info("Installation marked deleted installationId={}", installationId);
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
