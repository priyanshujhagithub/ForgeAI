"""FastAPI routes only. Schemas live in schemas.py; model logic in the other modules."""

from functools import lru_cache

from fastapi import Depends, FastAPI, Request
from fastapi.responses import JSONResponse

from .analyzer import RepositoryAnalyzer
from .llm import LLMOutputError, LLMUnavailableError
from .schemas import AnalysisResult, RepositorySnapshotContext

app = FastAPI(title="ForgeAI AI Service")


@lru_cache
def get_analyzer() -> RepositoryAnalyzer:
    return RepositoryAnalyzer()


@app.exception_handler(LLMOutputError)
def handle_llm_output_error(request: Request, exc: LLMOutputError) -> JSONResponse:
    return JSONResponse(status_code=502, content={"detail": str(exc)})


@app.exception_handler(LLMUnavailableError)
def handle_llm_unavailable(request: Request, exc: LLMUnavailableError) -> JSONResponse:
    return JSONResponse(status_code=503, content={"detail": str(exc)})


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/api/v1/analyze", response_model=AnalysisResult)
def analyze_repository(
    context: RepositorySnapshotContext,
    analyzer: RepositoryAnalyzer = Depends(get_analyzer),
):
    return analyzer.analyze(context)
