import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv

# The repository root .env holds local secrets. Real environment variables win.
_ROOT_ENV = Path(__file__).resolve().parents[3] / ".env"
load_dotenv(_ROOT_ENV, override=False)


@dataclass(frozen=True)
class Settings:
    # Any OpenAI-compatible endpoint works; the default is local Ollama.
    llm_base_url: str = "http://localhost:11434/v1/"
    llm_api_key: str = "ollama"
    llm_model: str = "forgeai-qwen3"
    # "none" turns off Qwen3 thinking, which otherwise dominates latency.
    # Set to an empty string for providers that reject the parameter.
    llm_reasoning_effort: str = "none"
    llm_timeout_seconds: float = 600.0
    llm_max_output_tokens: int = 1500
    llm_max_repairs: int = 1

    # Prompt budget. The model's context is 8192 tokens (see the Modelfile),
    # so repository evidence is capped well below that to leave room for output.
    evidence_budget_chars: int = 16_000
    max_file_chars: int = 6_000

    # Code generation rewrites whole files, so a file must fit both in the
    # prompt and in the output. Larger files are refused rather than truncated.
    generation_max_file_chars: int = 8_000
    generation_evidence_budget_chars: int = 6_000
    generation_max_output_tokens: int = 3_000

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

    @classmethod
    def from_env(cls) -> "Settings":
        defaults = cls()
        return cls(
            llm_base_url=os.getenv("LLM_BASE_URL", defaults.llm_base_url),
            llm_api_key=os.getenv("LLM_API_KEY", defaults.llm_api_key),
            llm_model=os.getenv("LLM_MODEL", defaults.llm_model),
            llm_reasoning_effort=os.getenv(
                "LLM_REASONING_EFFORT", defaults.llm_reasoning_effort
            ),
            llm_timeout_seconds=float(
                os.getenv("LLM_TIMEOUT_SECONDS", defaults.llm_timeout_seconds)
            ),
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
        )
