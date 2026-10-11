import httpx
import openai
import pytest
from pydantic import BaseModel

from app.llm import LLMOutputError, LLMUnavailableError
from tests.fakes import make_llm


class Answer(BaseModel):
    value: int


def complete(llm, **kwargs):
    return llm.complete_structured(
        system="sys", user="question", schema=Answer, name="answer", **kwargs
    )


def test_returns_validated_model_and_sends_json_schema():
    llm, client = make_llm([{"value": 7}])

    assert complete(llm) == Answer(value=7)

    request = client.requests[0]
    assert request["model"] == "forgeai-qwen3"
    assert request["messages"][0] == {"role": "system", "content": "sys"}
    assert request["response_format"]["type"] == "json_schema"
    assert request["response_format"]["json_schema"]["schema"] == Answer.model_json_schema()
    assert request["extra_body"] == {"reasoning_effort": "none"}


def test_omits_reasoning_effort_when_disabled():
    llm, client = make_llm([{"value": 1}], llm_reasoning_effort="")

    complete(llm)

    assert client.requests[0]["extra_body"] is None


def test_strips_think_block_and_code_fence():
    fence = "`" * 3
    reply = f'<think>hmm</think>\n{fence}json\n{{"value": 3}}\n{fence}'
    llm, _ = make_llm([reply])

    assert complete(llm).value == 3


def test_repairs_invalid_reply_by_feeding_errors_back():
    llm, client = make_llm([{"value": "not a number"}, {"value": 5}])

    assert complete(llm).value == 5

    repair_messages = client.requests[1]["messages"]
    assert repair_messages[-2]["role"] == "assistant"
    assert repair_messages[-1]["role"] == "user"
    assert "value" in repair_messages[-1]["content"]


def test_custom_check_failure_triggers_repair():
    llm, client = make_llm([{"value": -1}, {"value": 2}])

    result = complete(llm, check=lambda a: ["value must be positive"] if a.value < 0 else [])

    assert result.value == 2
    assert "value must be positive" in client.requests[1]["messages"][-1]["content"]


def test_raises_after_repairs_are_exhausted():
    llm, client = make_llm(["not json", "still not json"])

    with pytest.raises(LLMOutputError, match="failed validation"):
        complete(llm)

    assert len(client.requests) == 2


def test_truncated_reply_is_not_retried():
    llm, client = make_llm(['{"value": 1'], finish_reason="length")

    with pytest.raises(LLMOutputError, match="cut off"):
        complete(llm)

    assert len(client.requests) == 1


def test_empty_reply_raises():
    llm, _ = make_llm([""])

    with pytest.raises(LLMOutputError, match="empty"):
        complete(llm)


def test_connection_failure_becomes_unavailable_error():
    error = openai.APIConnectionError(request=httpx.Request("POST", "http://localhost"))
    llm, _ = make_llm([error])

    with pytest.raises(LLMUnavailableError, match="Cannot reach"):
        complete(llm)
