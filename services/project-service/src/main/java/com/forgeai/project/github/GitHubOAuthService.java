package com.forgeai.project.github;

//  This is different from GitHubClient.
//
//  This service handles the OAuth handshake.

import com.forgeai.project.dto.GitHubTokenResponse;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class GitHubOAuthService {
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final RestClient restClient;

    public GitHubOAuthService(
            @Value("${github.client-id}") String clientId,
            @Value("${github.client-secret}") String clientSecret,
            @Value("${github.redirect-uri}") String redirectUri,
            RestClient.Builder restClientBuilder) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.restClient = RestClient.builder().build();
    }

    public String buildAuthorizationUrl(String  state){
        return UriComponentsBuilder
                .fromUriString("https://github.com/login/oauth/authorize")
                .queryParam("client_id",clientId)
                .queryParam("redirect_uri",redirectUri)
                .queryParam("scope","repo")
                .queryParam("state",state)
                .toUriString();
    }

    // The code is not the GitHub access token.
    //
    //Think of it as:
    //
    //temporary authorization ticket
    //
    //GitHub says:
    //
    //This user has authorized ForgeAI. Here's a temporary code. Come back server-to-server and exchange it for a token.

    public GitHubTokenResponse exchangeCodeForAccessToken(String code) {

        return restClient.post()
                .uri("https://github.com/login/oauth/access_token")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body("""
                    {
                      "client_id": "%s",
                      "client_secret": "%s",
                      "code": "%s",
                      "redirect_uri": "%s"
                    }
                    """.formatted(
                        clientId,
                        clientSecret,
                        code,
                        redirectUri
                ))
                .retrieve()
                .body(GitHubTokenResponse.class);
    }
}
