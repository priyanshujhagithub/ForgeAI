import pytest
from pydantic import BaseModel

from app.config import Settings
from app.llm import LLMClient
from app.tracing import Tracer
from tests.fakes import FakeChatClient


class FakeObservation:
    def __init__(self, record: dict):
        self.record = record

    def update(self, **fields):
        self.record.setdefault("updates", []).append(fields)


class FakeLangfuse:
    """Records observations the way the Langfuse client is called."""

    def __init__(self, fail_on: str | None = None):
        self.fail_on = fail_on
        self.observations: list[dict] = []
        self.flushed = 0

    def start_as_current_observation(self, **fields):
        if self.fail_on == "start":
            raise RuntimeError("langfuse down")
        record = {**fields, "closed": False}
        self.observations.append(record)
        outer = self

        class Manager:
            def __enter__(self):
                return FakeObservation(record)

            def __exit__(self, *exc_info):
                if outer.fail_on == "exit":
                    raise RuntimeError("langfuse down")
                record["closed"] = True
                record["error"] = exc_info[0]
                return False

        return Manager()

    def flush(self):
        if self.fail_on == "flush":
            raise RuntimeError("langfuse down")
        self.flushed += 1


def test_without_keys_tracing_is_a_silent_no_op():
    tracer = Tracer.from_settings(Settings())

    assert tracer.enabled is False
    with tracer.span("run", input={"a": 1}) as span:
        span.update(output="x", metadata={"k": "v"})
    tracer.flush()


def test_span_records_input_output_and_metadata():
    client = FakeLangfuse()
    tracer = Tracer(client)

    with tracer.span("attempt-1", input={"task": "t"}, metadata={"attempt": 1}) as span:
        span.update(output={"outcome": "passed"})

    (record,) = client.observations
    assert record["as_type"] == "span"
    assert record["name"] == "attempt-1"
    assert record["input"] == {"task": "t"}
    assert record["metadata"] == {"attempt": 1}
    assert record["updates"] == [{"output": {"outcome": "passed"}}]
    assert record["closed"] is True


def test_error_in_the_traced_code_is_recorded_and_still_raised():
    client = FakeLangfuse()
    tracer = Tracer(client)

    with pytest.raises(ValueError, match="real failure"), tracer.span("run"):
        raise ValueError("real failure")

    (record,) = client.observations
    assert record["updates"][0]["level"] == "ERROR"
    assert "real failure" in record["updates"][0]["status_message"]
    assert record["error"] is ValueError


@pytest.mark.parametrize("fail_on", ["start", "exit", "flush"])
def test_langfuse_failures_never_break_the_run(fail_on):
    tracer = Tracer(FakeLangfuse(fail_on=fail_on))

    with tracer.span("run") as span:
        span.update(output="still fine")
        value = 42
    tracer.flush()

    assert value == 42


def test_content_capture_can_be_turned_off():
    client = FakeLangfuse()
    tracer = Tracer(client, capture_content=False)

    with tracer.generation("plan", model="m", input=[{"role": "user", "content": "SECRET"}]) as g:
        g.update(output="SECRET OUTPUT", usage={"input": 5, "output": 7})

    (record,) = client.observations
    assert "input" not in record
    assert record["updates"] == [{"usage_details": {"input": 5, "output": 7}}]


def test_every_model_call_is_one_generation_with_model_and_usage():
    class Answer(BaseModel):
        value: int

    client = FakeLangfuse()
    chat = FakeChatClient([{"value": 1}])
    llm = LLMClient(client=chat, settings=Settings(), tracer=Tracer(client))

    llm.complete_structured(system="s", user="u", schema=Answer, name="answer")

    (record,) = client.observations
    assert record["as_type"] == "generation"
    assert record["name"] == "answer"
    assert record["model"] == "forgeai-qwen3"
    assert record["input"][1] == {"role": "user", "content": "u"}
    assert record["updates"][0]["output"] == '{"value": 1}'
    assert record["closed"] is True
