"""FastAPI routes only. Schemas live in schemas.py; model logic in the other modules."""

from functools import lru_cache

from fastapi import Depends, FastAPI, Request
from fastapi.responses import JSONResponse

from .analyzer import RepositoryAnalyzer
from .generator import CodeGenerator, FileTooLargeError, InvalidPlanError
from .llm import LLMClient, LLMOutputError, LLMUnavailableError
from .planner import TaskPlanner
from .schemas import (
    AnalysisResult,
    EngineeringPlan,
    GenerateRequest,
    GenerationResult,
    PlanRequest,
    RepositorySnapshotContext,
)

app = FastAPI(title="ForgeAI AI Service")


@lru_cache
def get_llm() -> LLMClient:
    return LLMClient()


@lru_cache
def get_analyzer() -> RepositoryAnalyzer:
    return RepositoryAnalyzer(get_llm())


@lru_cache
def get_planner() -> TaskPlanner:
    return TaskPlanner(get_llm())


@lru_cache
def get_generator() -> CodeGenerator:
    return CodeGenerator(get_llm())


@app.exception_handler(InvalidPlanError)
@app.exception_handler(FileTooLargeError)
def handle_unprocessable(request: Request, exc: ValueError) -> JSONResponse:
    return JSONResponse(status_code=422, content={"detail": str(exc)})


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


@app.post("/api/v1/plan", response_model=EngineeringPlan)
def plan_task(
    request: PlanRequest,
    planner: TaskPlanner = Depends(get_planner),
):
    return planner.plan(request.context, request.task, request.analysis)


@app.post("/api/v1/generate", response_model=GenerationResult)
def generate_changes(
    request: GenerateRequest,
    generator: CodeGenerator = Depends(get_generator),
):
    return generator.generate(request.context, request.task, request.plan)
