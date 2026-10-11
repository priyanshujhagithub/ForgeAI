package com.forgeai.project.controller;

import com.forgeai.project.dto.*;
import com.forgeai.project.entity.User;
import com.forgeai.project.github.GitHubClient;
import com.forgeai.project.github.GitHubOAuthService;
import com.forgeai.project.github.GitHubOAuthStateService;
import com.forgeai.project.mapper.UserMapper;
import com.forgeai.project.security.AuthenticatedUser;
import com.forgeai.project.service.GitHubConnectionService;
import com.forgeai.project.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final UserService userService;
    private final UserMapper userMapper;
    private final GitHubOAuthService gitHubOAuthService;
    private final GitHubClient gitHubClient;
    private final GitHubOAuthStateService gitHubOAuthStateService;
    private final GitHubConnectionService gitHubConnectionService;

    public AuthController(
            UserService userService,
            UserMapper userMapper,
            GitHubOAuthService gitHubOAuthService,
            GitHubClient gitHubClient,
            GitHubOAuthStateService gitHubOAuthStateService,
            GitHubConnectionService gitHubConnectionService
    ) {
        this.userService = userService;
        this.userMapper = userMapper;
        this.gitHubOAuthService = gitHubOAuthService;
        this.gitHubClient = gitHubClient;
        this.gitHubOAuthStateService = gitHubOAuthStateService;
        this.gitHubConnectionService = gitHubConnectionService;
    }

    @PostMapping("/register")
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        var user=userService.register(request);
        return userMapper.toResponse(user);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request){
        return userService.login(request);
    }

    @GetMapping("/github")
    public ResponseEntity<Void> githubLogin(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AuthenticatedUser user=(AuthenticatedUser)authentication.getPrincipal();
        String state=gitHubOAuthStateService.createState(user.userId());
        String authorizationUrl= gitHubOAuthService.buildAuthorizationUrl(state);
        return ResponseEntity
                .status(302)
                .header("Location",authorizationUrl)
                .build();
    }

    @GetMapping("/github/callback")
    public String githubCallback(
            @RequestParam String code,
            @RequestParam String state
    ){
        Long userId = gitHubOAuthStateService.consumeState(state);
        if(userId==null){
            throw new IllegalArgumentException("Invalid OAuth state");
        }
        GitHubTokenResponse tokenResponse=gitHubOAuthService.exchangeCodeForAccessToken(code);
        String accessToken=tokenResponse.getAccessToken();
        GitHubUserResponse githubUser=gitHubClient.getAuthenticatedUser(accessToken);
        gitHubConnectionService.saveConnection(
                userId,
                githubUser.getId(),
                githubUser.getLogin(),
                accessToken
        );
        return "GitHub account connected: " + githubUser.getLogin();
    }
}
