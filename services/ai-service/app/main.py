from fastapi import FastAPI

'''
main.py
└── FastAPI routes

schemas.py
└── Pydantic models

analyzer.py
└── analysis / LLM logic
'''

from .analyzer import RepositoryAnalyzer
from .schemas import AnalysisResult, RepositorySnapshotContext


app = FastAPI(title="ForgeAI AI Service")

analyzer = RepositoryAnalyzer()


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post(
    "/api/v1/analyze",
    response_model=AnalysisResult,
)
def analyze_repository(
    context: RepositorySnapshotContext,
):
    return analyzer.analyze(context)