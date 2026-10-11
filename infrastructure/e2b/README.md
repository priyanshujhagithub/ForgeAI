# E2B sandbox templates

ForgeAI runs generated code only inside E2B sandboxes.

| Project type | Template | Status |
|---|---|---|
| Python, Node | E2B default image | Verified: Python 3.11, pip, git, Node 20, npm |
| Maven, Gradle | `java/e2b.Dockerfile` | Not built or verified yet |

## Building the Java template

The default image has no JDK and about 470 MB of RAM, which is too little for Maven.
Build a custom template once with the E2B CLI and give it more memory:

```bash
npm install -g @e2b/cli
e2b auth login
cd infrastructure/e2b/java
e2b template build --name forgeai-java --memory-mb 2048
```

Then set `E2B_TEMPLATE_JAVA=forgeai-java` in the root `.env`. Until that is set,
Maven and Gradle runs end with "sandbox unavailable" instead of failing obscurely.

Check the CLI's current flags with `e2b template build --help`; this file was
written without running the build. A larger sandbox also uses credit faster.
