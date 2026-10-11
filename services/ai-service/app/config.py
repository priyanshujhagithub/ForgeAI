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
        )
