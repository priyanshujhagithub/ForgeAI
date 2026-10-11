                 FORGEAI USER #2
                       │
                       │
                       ▼
             AuthController
                       │
                       ▼
          AuthenticatedUser
              userId = 2
                       │
                       ▼
       GitHubOAuthStateService
              creates state
              abc123 → user 2
                       │
                       ▼
          GitHubOAuthService
         builds authorization URL
                       │
                       ▼
                    GITHUB
                       │
              User authorizes
                       │
                       ▼
         callback?code=XYZ
                  &state=abc123
                       │
                       ▼
             AuthController
                       │
              validate/consume
                  state
                       │
                       ▼
                userId = 2
                       │
                       ▼
          GitHubOAuthService
             exchange code
                       │
                       ▼
            GitHubTokenResponse
              accessToken
                       │
                       ▼
               GitHubClient
          GET /user + Bearer token
                       │
                       ▼
            GitHubUserResponse
        githubId + githubUsername
                       │
                       ▼
        GitHubConnectionService
                       │
                       ▼
              GitHubConnection
                       │
                       ▼
       GitHubConnectionRepository
                       │
                       ▼
                 PostgreSQL