# Deploying ForgeAI for free

This puts the whole system on the internet at no cost, with nothing running on
your own computer. No step needs a payment method, and you should not add one:
without a card on file, every service below stops when a free allowance runs out
instead of sending a bill.

| Part | Where it runs | Free allowance (as checked in October 2026) |
|---|---|---|
| Web app | Render static site | Served from a CDN; does not sleep |
| Java API | Render free web service | 512 MB, 0.1 CPU; sleeps after 15 idle minutes; 750 instance hours a month per workspace |
| AI service | Render free web service | Same as above, sharing the 750 hours |
| Database | Neon free Postgres | 1 GB storage, 100 compute hours a month; suspends after 5 idle minutes |
| Model | Google Gemini API, free tier | Per-account limits, shown in AI Studio |
| Sandboxes | E2B Hobby | One-time $100 credit |
| Tracing | Langfuse Cloud Hobby | 50,000 units a month |

Free terms change. Check each provider's pricing page before relying on them.

## What to expect

- **Cold starts.** After 15 minutes without visitors Render stops both services.
  The first visit after that wakes them. The web app asks for this the moment
  it opens, so they are starting while you read the page, and the sign-in form
  says so if it is still waiting. Measured locally under the same limits
  (512 MB, 0.1 CPU), the Java service takes about 56 seconds to start; a class
  data archive made at build time brought that down from 99 seconds.
- **Keep a run's page open.** Render stops a service that has had no incoming
  request for 15 minutes. While a run's page is open it asks for progress every
  few seconds, which keeps the API awake. A run left alone for longer than that
  can be stopped part-way and is then marked failed.
- **Anyone can sign up; only you can start runs.** Runs spend model calls and
  sandbox credit, so `FORGEAI_ALLOWED_EMAILS` limits who may start them, and
  `FORGEAI_DAILY_RUN_LIMIT` caps how many each account starts per day.
- **The AI service is public.** Free Render services cannot receive traffic on
  Render's private network, so the API reaches the AI service at its public
  address. The AI service answers only requests carrying the shared secret
  `AI_SERVICE_TOKEN`, which the Blueprint generates and gives to both services.
- **Busy models are skipped.** Gemini's free tier often turns a popular model
  away with "experiencing high demand" (HTTP 503) or "too many requests" (429).
  `LLM_FALLBACK_MODELS` lists the models to try next, in order; the first that
  answers is used, and the trace records which one it was.
- **Your code goes to Google.** On Gemini's free tier, Google may use what you
  send to improve its products. Use it with repositories you would be happy to
  make public.

## 1. Create the accounts

All are free and none should ask for a card. If one does, stop and do not enter it.

1. **Render**: https://render.com, sign up with GitHub.
2. **Neon**: https://neon.com, sign up.
3. **Google AI Studio**: https://aistudio.google.com, sign in and create an API
   key. Do not set up billing.
4. **E2B** and **Langfuse**: you already have these; reuse the keys in your `.env`.

## 2. Put the code on GitHub

Render deploys from a GitHub repository. Push this repository to GitHub (public
is best: it is also what reviewers will look at). Every later push to the main
branch redeploys whatever changed.

## 3. Create the database on Neon

1. Create a project. Pick the region **AWS Asia Pacific (Singapore)**, the same
   region as the Render services in `render.yaml`, so the API and its database
   are close.
2. Open **Connect**. Turn **Connection pooling off**: Flyway and Hibernate need
   a direct connection.
3. From the connection string `postgresql://USER:PASSWORD@HOST/DATABASE?sslmode=require`
   note three values:
   - `DATABASE_URL` = `jdbc:postgresql://HOST/DATABASE?sslmode=require`
   - `DATABASE_USERNAME` = `USER`
   - `DATABASE_PASSWORD` = `PASSWORD`

The tables are created by the API's migrations the first time it starts.

## 4. Make an encryption key

Stored GitHub tokens are encrypted with a key that must be exactly 32 bytes.
Make a new one for the deployment; do not reuse your local key.

```bash
python -c "import base64, os; print(base64.b64encode(os.urandom(32)).decode())"
```

Keep it somewhere safe. If it is lost, users have to reconnect GitHub.

## 5. Create the services on Render

1. In Render, choose **New > Blueprint** and select your repository. Render reads
   `render.yaml` and lists `forgeai-web`, `forgeai-api` and `forgeai-ai`.
2. Fill in the values it asks for. The three addresses are
   `https://<service name>.onrender.com`; if a name is already taken anywhere on
   Render, it adds a few characters, so check the real addresses after this step.

| Service | Key | Value |
|---|---|---|
| forgeai-web | `VITE_API_URL` | `https://forgeai-api.onrender.com` |
| forgeai-api | `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | From step 3 |
| forgeai-api | `FORGEAI_ENCRYPTION_KEY` | From step 4 |
| forgeai-api | `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET` | Fill in `pending` for now; step 6 gives the real values |
| forgeai-api | `GITHUB_REDIRECT_URI` | `https://forgeai-api.onrender.com/api/v1/auth/github/callback` |
| forgeai-api | `FORGEAI_FRONTEND_URL` | `https://forgeai-web.onrender.com` (no trailing slash) |
| forgeai-api | `AI_SERVICE_BASE_URL` | `https://forgeai-ai.onrender.com` |
| forgeai-api | `FORGEAI_ALLOWED_EMAILS` | The email you will sign up with |
| forgeai-ai | `LLM_API_KEY` | Your Gemini key |
| forgeai-ai | `E2B_API_KEY`, `LANGFUSE_*` | From your `.env` |

3. Apply. The first build takes several minutes.
4. If any real address differs from the table, correct the variables that
   mention it in each service's **Environment** page. A change to
   `VITE_API_URL` needs a new build of `forgeai-web` (**Manual Deploy**).

`JWT_SECRET` and `AI_SERVICE_TOKEN` are generated by Render and never need
typing.

## 6. Make a GitHub OAuth app for the deployment

A GitHub OAuth app has one callback address, so the deployment needs its own,
separate from the one you use locally.

1. GitHub > **Settings > Developer settings > OAuth Apps > New OAuth App**.
2. **Homepage URL**: `https://forgeai-web.onrender.com`
3. **Authorization callback URL**: `https://forgeai-api.onrender.com/api/v1/auth/github/callback`
4. Generate a client secret, and put the client ID and secret into
   `forgeai-api`'s `GITHUB_CLIENT_ID` and `GITHUB_CLIENT_SECRET`. Render
   restarts the service with them.

## 7. Check it works

1. Open `https://forgeai-web.onrender.com`. The first load after a quiet spell
   wakes the services; give it a minute.
2. Create an account with the email in `FORGEAI_ALLOWED_EMAILS`.
3. **Settings > Connect GitHub**, then create a project and start a run on your
   demo repository.

If something fails, each Render service has a **Logs** tab. Common causes:

| Symptom | Likely cause |
|---|---|
| Sign-in says it cannot reach the server | `VITE_API_URL` is wrong, or `forgeai-web` was not rebuilt after changing it |
| Browser console shows a CORS error | `FORGEAI_FRONTEND_URL` does not exactly match the web app's address |
| A run fails with "AI service returned HTTP 401" | The two services disagree on `AI_SERVICE_TOKEN`; re-sync the Blueprint |
| The API log shows a database connection error | `DATABASE_URL` is not in the `jdbc:postgresql://...` form, or pooling was left on |
| "This ForgeAI deployment only starts runs for invited accounts" | Your email is not in `FORGEAI_ALLOWED_EMAILS` |

## When a free allowance runs out

Nothing is charged. Instead:

- **Render** suspends the free services until the next month.
- **Neon** stops accepting connections until the next month.
- **Gemini** answers "too many requests" until the quota resets.
- **E2B** blocks the account when the one-time credit is spent; runs then end as
  "Not tested" and open no pull request.
