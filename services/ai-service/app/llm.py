"""Single entry point for every model call.

Each call asks for JSON matching a Pydantic model, validates the reply, and
gives the model a bounded number of chances to repair an invalid one.
"""

import json
import re
from collections.abc import Callable
from typing import TypeVar

import openai
from openai import OpenAI
from pydantic import BaseModel, ValidationError

from .config import Settings
from .tracing import Tracer

T = TypeVar("T", bound=BaseModel)

_THINK_BLOCK = re.compile(r"<think>.*?</think>", re.DOTALL)
_CODE_FENCE = re.compile(r"^```(?:json)?\s*(.*?)\s*```$", re.DOTALL)


class LLMOutputError(RuntimeError):
    """The model replied, but never with output that passed validation."""


class LLMUnavailableError(RuntimeError):
    """The model provider could not be reached or rejected the request."""


class LLMClient:
    def __init__(
        self,
        client: OpenAI | None = None,
        settings: Settings | None = None,
        tracer: Tracer | None = None,
    ):
        self.settings = settings or Settings.from_env()
        self.tracer = tracer or Tracer()
        self.client = client or OpenAI(
            base_url=self.settings.llm_base_url,
            api_key=self.settings.llm_api_key,
            timeout=self.settings.llm_timeout_seconds,
            max_retries=self.settings.llm_max_retries,
        )

    def complete_structured(
        self,
        *,
        system: str,
        user: str,
        schema: type[T],
        name: str,
        max_tokens: int | None = None,
        check: Callable[[T], list[str]] | None = None,
        soft_check: Callable[[T], list[str]] | None = None,
    ) -> T:
        """Return a validated `schema` instance.

        `check` adds rules the JSON schema cannot express (for example, that a
        path exists in the repository). It returns a list of problems; an empty
        list means the value is accepted.

        `soft_check` works the same way but is advisory: its problems earn the
        model a repair attempt, and if it still cannot satisfy them the last
        otherwise-valid reply is returned. Use it for problems a later stage
        would catch anyway, where a cheap early retry is worth trying.
        """
        messages = [
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ]
        problems: list[str] = []
        fallback: T | None = None

        for _ in range(1 + self.settings.llm_max_repairs):
            content = self._request(messages, schema, name, max_tokens)
            value, problems = self._parse(content, schema, check)
            if value is not None:
                problems = soft_check(value) if soft_check else []
                if not problems:
                    return value
                fallback = value

            messages.append({"role": "assistant", "content": content})
            messages.append({"role": "user", "content": _repair_prompt(problems)})

        if fallback is not None:
            return fallback
        raise LLMOutputError(
            f"Model output for '{name}' failed validation: " + "; ".join(problems)
        )

    def _request(
        self,
        messages: list[dict],
        schema: type[BaseModel],
        name: str,
        max_tokens: int | None,
    ) -> str:
        with self.tracer.generation(
            name, model=self.settings.llm_model, input=messages
        ) as generation:
            content, usage = self._call_provider(messages, schema, name, max_tokens)
            generation.update(output=content, usage=usage)
            return content

    def _call_provider(
        self,
        messages: list[dict],
        schema: type[BaseModel],
        name: str,
        max_tokens: int | None,
    ) -> tuple[str, dict[str, int] | None]:
        extra_body = {}
        if self.settings.llm_reasoning_effort:
            extra_body["reasoning_effort"] = self.settings.llm_reasoning_effort

        try:
            response = self.client.chat.completions.create(
                model=self.settings.llm_model,
                messages=messages,
                max_tokens=max_tokens or self.settings.llm_max_output_tokens,
                response_format={
                    "type": "json_schema",
                    "json_schema": {
                        "name": name,
                        "strict": True,
                        "schema": schema.model_json_schema(),
                    },
                },
                extra_body=extra_body or None,
            )
        except openai.APIConnectionError as exc:
            raise LLMUnavailableError(
                f"Cannot reach the model provider at {self.settings.llm_base_url}"
            ) from exc
        except openai.APIStatusError as exc:
            raise LLMUnavailableError(
                f"Model provider returned HTTP {exc.status_code}: {exc.message}"
            ) from exc

        choice = response.choices[0]
        if choice.finish_reason == "length":
            # A truncated reply cannot be repaired by asking again with more text.
            raise LLMOutputError(
                f"Model output for '{name}' was cut off at the output token limit."
            )
        if not choice.message.content:
            raise LLMOutputError(f"Model returned an empty response for '{name}'.")
        return choice.message.content, _usage(response)

    @staticmethod
    def _parse(
        content: str,
        schema: type[T],
        check: Callable[[T], list[str]] | None,
    ) -> tuple[T | None, list[str]]:
        try:
            value = schema.model_validate(json.loads(_clean(content)))
        except json.JSONDecodeError as exc:
            return None, [f"Reply was not valid JSON: {exc.msg}"]
        except ValidationError as exc:
            return None, [
                f"{'.'.join(str(part) for part in error['loc'])}: {error['msg']}"
                for error in exc.errors()
            ]

        problems = check(value) if check else []
        return (None, problems) if problems else (value, [])


def _usage(response) -> dict[str, int] | None:
    usage = getattr(response, "usage", None)
    if usage is None:
        return None
    return {
        "input": getattr(usage, "prompt_tokens", 0) or 0,
        "output": getattr(usage, "completion_tokens", 0) or 0,
    }


def _clean(content: str) -> str:
    text = _THINK_BLOCK.sub("", content).strip()
    fenced = _CODE_FENCE.match(text)
    return fenced.group(1) if fenced else text


def _repair_prompt(problems: list[str]) -> str:
    listed = "\n".join(f"- {problem}" for problem in problems)
    return (
        "Your previous reply was rejected for these reasons:\n"
        f"{listed}\n"
        "Reply again with corrected JSON only."
    )
