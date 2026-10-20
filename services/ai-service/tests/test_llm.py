import httpx
import openai
import pytest
from pydantic import BaseModel

from app.config import Settings
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
    assert request["model"] == "gemini-3.5-flash"
    assert request["messages"][0] == {"role": "system", "content": "sys"}
    assert request["response_format"]["type"] == "json_schema"
    assert request["response_format"]["json_schema"]["schema"] == Answer.model_json_schema()
    assert request["extra_body"] == {"reasoning_effort": "low"}


def test_omits_reasoning_effort_when_disabled():
    llm, client = make_llm([{"value": 1}], llm_reasoning_effort="")

    complete(llm)

    assert client.requests[0]["extra_body"] is None


def test_strips_a_code_fence_around_the_json():
    fence = "`" * 3
    reply = f'{fence}json\n{{"value": 3}}\n{fence}'
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


def test_soft_check_earns_a_repair_attempt():
    llm, client = make_llm([{"value": 1}, {"value": 2}])

    result = complete(llm, soft_check=lambda a: ["prefer an even value"] if a.value % 2 else [])

    assert result.value == 2
    assert "prefer an even value" in client.requests[1]["messages"][-1]["content"]


def test_soft_check_that_stays_unsatisfied_returns_the_last_valid_reply():
    llm, client = make_llm([{"value": 1}, {"value": 3}])

    result = complete(llm, soft_check=lambda a: ["prefer an even value"] if a.value % 2 else [])

    assert result.value == 3
    assert len(client.requests) == 2


def test_hard_check_still_raises_even_when_a_soft_check_is_present():
    llm, _ = make_llm([{"value": -1}, {"value": -2}])

    with pytest.raises(LLMOutputError):
        complete(
            llm,
            check=lambda a: ["value must be positive"] if a.value < 0 else [],
            soft_check=lambda a: [],
        )


def status_error(code: int) -> openai.APIStatusError:
    request = httpx.Request("POST", "http://provider")
    return openai.APIStatusError(
        f"HTTP {code}", response=httpx.Response(code, request=request), body=None
    )


@pytest.mark.parametrize("code", [429, 503])
def test_a_busy_model_hands_over_to_the_next_one(code):
    llm, client = make_llm(
        [status_error(code), {"value": 7}],
        llm_model="big-model",
        llm_fallback_models=("small-model",),
    )

    result = complete(llm)

    assert result == Answer(value=7)
    assert [request["model"] for request in client.requests] == ["big-model", "small-model"]


def test_a_refused_request_is_not_retried_on_another_model():
    llm, client = make_llm(
        [status_error(400)], llm_model="big-model", llm_fallback_models=("small-model",)
    )

    with pytest.raises(LLMUnavailableError, match="HTTP 400 for big-model"):
        complete(llm)
    assert len(client.requests) == 1


def test_when_every_model_is_busy_the_last_answer_is_reported():
    llm, _ = make_llm(
        [status_error(503), status_error(503)],
        llm_model="big-model",
        llm_fallback_models=("small-model",),
    )

    with pytest.raises(LLMUnavailableError, match="HTTP 503 for small-model"):
        complete(llm)


def test_a_timeout_says_so_instead_of_claiming_the_provider_is_unreachable():
    error = openai.APITimeoutError(request=httpx.Request("POST", "http://provider"))
    llm, _ = make_llm([error], llm_timeout_seconds=180)

    with pytest.raises(LLMUnavailableError, match="did not answer within 180 s"):
        complete(llm)


def test_fallback_models_are_read_as_a_comma_separated_list(monkeypatch):
    monkeypatch.setenv("LLM_FALLBACK_MODELS", " small-model, tiny-model ,")

    assert Settings.from_env().llm_fallback_models == ("small-model", "tiny-model")


def dropped_connection() -> openai.APIConnectionError:
    return openai.APIConnectionError(request=httpx.Request("POST", "http://provider"))


def test_a_dropped_connection_also_hands_over_to_the_next_model():
    llm, client = make_llm(
        [dropped_connection(), {"value": 7}],
        llm_model="big-model",
        llm_fallback_models=("small-model",),
    )

    assert complete(llm) == Answer(value=7)
    assert [request["model"] for request in client.requests] == ["big-model", "small-model"]


def test_a_provider_that_drops_every_model_is_reported_as_unreachable():
    llm, _ = make_llm(
        [dropped_connection(), dropped_connection()],
        llm_model="big-model",
        llm_fallback_models=("small-model",),
    )

    with pytest.raises(LLMUnavailableError, match="Cannot reach"):
        complete(llm)


GOOGLE_QUOTA_REPLY = (
    "Error code: 429 - [{'error': {'code': 429, 'message': 'You exceeded your current quota,"
    " please check your plan and billing details. Quota exceeded for metric:"
    " generativelanguage.googleapis.com/generate_content_free_tier_requests, limit: 20,"
    " model: gemini-3.8-flash\\nPlease retry in 15h17m5.907759903s.', 'status':"
    " 'RESOURCE_EXHAUSTED', 'details': [" + "x" * 2000 + "]}}]"
)


def test_a_used_up_quota_is_described_in_one_sentence():
    request = httpx.Request("POST", "http://provider")
    error = openai.APIStatusError(
        GOOGLE_QUOTA_REPLY, response=httpx.Response(429, request=request), body=None
    )
    llm, _ = make_llm([error], llm_model="gemini-3.8-flash")

    with pytest.raises(LLMUnavailableError) as raised:
        complete(llm)

    assert str(raised.value) == (
        "Model provider returned HTTP 429 for gemini-3.8-flash: its quota is used up"
        " (limit: 20 requests); it can be used again in 15h17m5s."
    )


def test_a_missing_key_is_reported_before_any_call():
    llm, client = make_llm([{"value": 1}], llm_api_key="")

    with pytest.raises(LLMUnavailableError, match="LLM_API_KEY is not set"):
        complete(llm)
    assert client.requests == []


def test_gemini_with_flash_lite_fallbacks_is_the_default(monkeypatch):
    monkeypatch.delenv("LLM_FALLBACK_MODELS", raising=False)
    monkeypatch.delenv("LLM_MODEL", raising=False)

    settings = Settings.from_env()

    assert settings.llm_model == "gemini-3.5-flash"
    assert settings.llm_fallback_models == ("gemini-3.5-flash-lite", "gemini-3.1-flash-lite")

