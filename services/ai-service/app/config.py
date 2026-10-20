import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv

# Local development keeps secrets in a .env file at the repository root, so
# look upwards for one. Real environment variables always win, and in a
# container there is simply no file to find.
for _directory in Path(__file__).resolve().parents:
    if (_directory / ".env").is_file():
        load_dotenv(_directory / ".env", override=False)
        break


@dataclass(frozen=True)
class Settings:
    # Google Gemini through its OpenAI-compatible endpoint. The key comes from
    # Google AI Studio (free tier, no billing needed) and must be set in LLM_API_KEY.
    llm_base_url: str = "https://generativelanguage.googleapis.com/v1beta/openai/"
    llm_api_key: str = ""
    # Chosen by free daily allowance as much as by quality: one run makes 6 to
    # 15 model calls, and the free tier allows the newest Flash models only 20
    # requests a day but the Flash Lite models 500 (AI Studio, October 2026).
    llm_model: str = "gemini-3.5-flash"
    # Tried in order when the model above is busy or out of quota (HTTP 429 or
    # 5xx, or a dropped connection). A smaller model that answers beats a run
    # that fails.
    llm_fallback_models: tuple[str, ...] = ("gemini-3.5-flash-lite", "gemini-3.1-flash-lite")
    # Gemini 3 models cannot turn thinking off; "low" keeps it short. Set to an
    # empty string for a provider that rejects the parameter.
    llm_reasoning_effort: str = "low"
    llm_timeout_seconds: float = 180.0
    # Retries for the last model in the list only (see llm.py); a busy earlier
    # model is skipped at once instead.
    llm_max_retries: int = 3
    llm_max_output_tokens: int = 4_000
    llm_max_repairs: int = 1

    # Prompt budget. Gemini's context is far larger, but small prompts answer
    # faster and stay well inside the free tier's tokens-per-minute limit.
    evidence_budget_chars: int = 16_000
    max_file_chars: int = 6_000

    # Code generation rewrites whole files, so a file must fit both in the
    # prompt and in the output. Larger files are refused rather than truncated.
    generation_max_file_chars: int = 8_000
    generation_evidence_budget_chars: int = 6_000
    generation_max_output_tokens: int = 8_000

    # E2B sandbox. Blank key means sandbox runs report "unavailable".
    e2b_api_key: str = ""
    # Optional custom templates; the default image has Python and Node only.
    e2b_template: str = ""
    e2b_template_java: str = ""
    # Hard lifetime: E2B destroys the sandbox itself after this, even if we crash.
    sandbox_lifetime_seconds: int = 600
    sandbox_setup_timeout_seconds: int = 300
    sandbox_test_timeout_seconds: int = 240
    sandbox_output_tail_chars: int = 6_000
    # Estimate only: 2 vCPU and about 0.5 GiB at E2B's published per-second rates.
    sandbox_usd_per_second: float = 0.00003

    # Self-correction loop.
    max_attempts: int = 3
    # How much failing test output is shown to the model.
    feedback_output_chars: int = 3_000

    # Shared secret the project service sends in X-Service-Token. Deployed, this
    # service is reachable from the internet and every call spends model and
    # sandbox quota, so it answers no one else. Blank (local) means no check.
    service_token: str = ""

    # Langfuse tracing. Blank keys turn tracing off.
    langfuse_public_key: str = ""
    langfuse_secret_key: str = ""
    langfuse_base_url: str = ""
    # Prompts contain repository source. Set to false to send only timings and usage.
    tracing_capture_content: bool = True

    @classmethod
    def from_env(cls) -> "Settings":
        defaults = cls()
        return cls(
            llm_base_url=os.getenv("LLM_BASE_URL", defaults.llm_base_url),
            llm_api_key=os.getenv("LLM_API_KEY", defaults.llm_api_key),
            llm_model=os.getenv("LLM_MODEL", defaults.llm_model),
            llm_fallback_models=(
                tuple(
                    model.strip()
                    for model in os.environ["LLM_FALLBACK_MODELS"].split(",")
                    if model.strip()
                )
                if "LLM_FALLBACK_MODELS" in os.environ
                else defaults.llm_fallback_models
            ),
            llm_reasoning_effort=os.getenv(
                "LLM_REASONING_EFFORT", defaults.llm_reasoning_effort
            ),
            llm_timeout_seconds=float(
                os.getenv("LLM_TIMEOUT_SECONDS", defaults.llm_timeout_seconds)
            ),
            llm_max_retries=int(os.getenv("LLM_MAX_RETRIES", defaults.llm_max_retries)),
            llm_max_output_tokens=int(
                os.getenv("LLM_MAX_OUTPUT_TOKENS", defaults.llm_max_output_tokens)
            ),
            llm_max_repairs=int(os.getenv("LLM_MAX_REPAIRS", defaults.llm_max_repairs)),
            evidence_budget_chars=int(
                os.getenv("EVIDENCE_BUDGET_CHARS", defaults.evidence_budget_chars)
            ),
            max_file_chars=int(os.getenv("MAX_FILE_CHARS", defaults.max_file_chars)),
            generation_max_file_chars=int(
                os.getenv("GENERATION_MAX_FILE_CHARS", defaults.generation_max_file_chars)
            ),
            generation_evidence_budget_chars=int(
                os.getenv(
                    "GENERATION_EVIDENCE_BUDGET_CHARS",
                    defaults.generation_evidence_budget_chars,
                )
            ),
            generation_max_output_tokens=int(
                os.getenv(
                    "GENERATION_MAX_OUTPUT_TOKENS", defaults.generation_max_output_tokens
                )
            ),
            e2b_api_key=os.getenv("E2B_API_KEY", defaults.e2b_api_key),
            e2b_template=os.getenv("E2B_TEMPLATE", defaults.e2b_template),
            e2b_template_java=os.getenv("E2B_TEMPLATE_JAVA", defaults.e2b_template_java),
            sandbox_lifetime_seconds=int(
                os.getenv("SANDBOX_LIFETIME_SECONDS", defaults.sandbox_lifetime_seconds)
            ),
            sandbox_setup_timeout_seconds=int(
                os.getenv(
                    "SANDBOX_SETUP_TIMEOUT_SECONDS", defaults.sandbox_setup_timeout_seconds
                )
            ),
            sandbox_test_timeout_seconds=int(
                os.getenv(
                    "SANDBOX_TEST_TIMEOUT_SECONDS", defaults.sandbox_test_timeout_seconds
                )
            ),
            sandbox_output_tail_chars=int(
                os.getenv("SANDBOX_OUTPUT_TAIL_CHARS", defaults.sandbox_output_tail_chars)
            ),
            sandbox_usd_per_second=float(
                os.getenv("SANDBOX_USD_PER_SECOND", defaults.sandbox_usd_per_second)
            ),
            max_attempts=int(os.getenv("MAX_ATTEMPTS", defaults.max_attempts)),
            feedback_output_chars=int(
                os.getenv("FEEDBACK_OUTPUT_CHARS", defaults.feedback_output_chars)
            ),
            service_token=os.getenv("AI_SERVICE_TOKEN", defaults.service_token),
            langfuse_public_key=os.getenv("LANGFUSE_PUBLIC_KEY", defaults.langfuse_public_key),
            langfuse_secret_key=os.getenv("LANGFUSE_SECRET_KEY", defaults.langfuse_secret_key),
            langfuse_base_url=os.getenv("LANGFUSE_BASE_URL")
            or os.getenv("LANGFUSE_HOST", defaults.langfuse_base_url),
            tracing_capture_content=os.getenv("TRACING_CAPTURE_CONTENT", "true").lower()
            != "false",
        )
