"""Optional Langfuse tracing.

Tracing must never break a run: with no keys it is a no-op, and any failure
inside Langfuse is logged and swallowed. Only coarse observations are sent
(one per run, per attempt, per model call) to stay well inside the free tier.
"""

import logging
from collections.abc import Iterator
from contextlib import contextmanager
from typing import Any

from .config import Settings

log = logging.getLogger(__name__)


class Observation:
    """Handle for adding results to an open span or generation."""

    def __init__(self, target: Any = None, capture_content: bool = True):
        self._target = target
        self._capture_content = capture_content

    def update(
        self,
        *,
        output: Any = None,
        metadata: dict | None = None,
        usage: dict[str, int] | None = None,
        level: str | None = None,
        status_message: str | None = None,
    ) -> None:
        if self._target is None:
            return
        fields = {
            "output": output if self._capture_content else None,
            "metadata": metadata,
            "usage_details": usage,
            "level": level,
            "status_message": status_message,
        }
        try:
            self._target.update(**{k: v for k, v in fields.items() if v is not None})
        except Exception:
            log.warning("Langfuse update failed; continuing without it", exc_info=True)


class Tracer:
    def __init__(self, client: Any = None, capture_content: bool = True):
        self._client = client
        self._capture_content = capture_content

    @classmethod
    def from_settings(cls, settings: Settings) -> "Tracer":
        if not (settings.langfuse_public_key and settings.langfuse_secret_key):
            return cls()
        try:
            from langfuse import Langfuse

            client = Langfuse(
                public_key=settings.langfuse_public_key,
                secret_key=settings.langfuse_secret_key,
                base_url=settings.langfuse_base_url or None,
            )
        except Exception:
            log.warning("Langfuse could not be initialised; tracing is off", exc_info=True)
            return cls()
        return cls(client, settings.tracing_capture_content)

    @property
    def enabled(self) -> bool:
        return self._client is not None

    def span(self, name: str, *, input: Any = None, metadata: dict | None = None):
        return self._observe("span", name, input=input, metadata=metadata)

    def generation(self, name: str, *, model: str, input: Any = None):
        return self._observe("generation", name, input=input, model=model)

    def flush(self) -> None:
        if self._client is None:
            return
        try:
            self._client.flush()
        except Exception:
            log.warning("Langfuse flush failed", exc_info=True)

    @contextmanager
    def _observe(self, as_type: str, name: str, **fields: Any) -> Iterator[Observation]:
        if self._client is None:
            yield Observation()
            return

        if not self._capture_content:
            fields["input"] = None
        try:
            manager = self._client.start_as_current_observation(
                as_type=as_type,
                name=name,
                **{k: v for k, v in fields.items() if v is not None},
            )
            target = manager.__enter__()
        except Exception:
            log.warning("Langfuse could not start '%s'; continuing untraced", name, exc_info=True)
            yield Observation()
            return

        observation = Observation(target, self._capture_content)
        try:
            yield observation
        except BaseException as exc:
            # Record the failure on the trace, then let the caller's error through.
            observation.update(level="ERROR", status_message=f"{type(exc).__name__}: {exc}"[:500])
            self._close(manager, type(exc), exc, exc.__traceback__)
            raise
        else:
            self._close(manager, None, None, None)

    @staticmethod
    def _close(manager: Any, *exc_info: Any) -> None:
        try:
            manager.__exit__(*exc_info)
        except Exception:
            log.warning("Langfuse could not close an observation", exc_info=True)
