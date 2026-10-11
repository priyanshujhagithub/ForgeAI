from typing import Literal

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class RepositoryFile(BaseModel):
    path: str
    content: str
    sha: str


class RepositoryMetadata(BaseModel):
    projectType: str
    springBoot: bool


class RepositorySnapshotContext(BaseModel):
    owner: str
    repository: str
    files: list[RepositoryFile]
    metadata: RepositoryMetadata


class AnalysisResult(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    summary: str = Field(
        description="A concise description of what the repository does."
    )

    project_type: str = Field(
        alias="projectType",
        description="The concrete technology/project type, such as Spring Boot REST API."
    )

    key_components: list[str] = Field(
        alias="keyComponents",
        description=(
            "Important classes, modules, services, or components identified in the repository."
        ),
    )

    dependencies: list[str] = Field(
        description="Important external libraries, frameworks, or services used by the repository."
    )

    architecture_notes: str = Field(
        alias="architectureNotes",
        description="A concise description of the repository architecture and major relationships."
    )

    relevant_files: list[str] = Field(
        alias="relevantFiles",
        description=(
            "Files that are especially important for understanding or modifying this repository."
        ),
    )

class CamelModel(BaseModel):
    """Snake_case in Python, camelCase on the wire (for Java and for the model)."""

    model_config = ConfigDict(populate_by_name=True, alias_generator=to_camel)


class PlanStep(CamelModel):
    order: int = Field(description="1-based position of this step in the plan.")
    description: str = Field(description="What to change in this step and why.")
    files: list[str] = Field(description="Repository paths this step touches.")


class EngineeringPlan(CamelModel):
    task_understanding: str = Field(
        description="A restatement of the task in terms of this repository."
    )
    files_to_modify: list[str] = Field(
        description="Existing repository paths that must be changed."
    )
    files_to_create: list[str] = Field(
        description="New repository paths that must be added."
    )
    steps: list[PlanStep] = Field(description="Ordered implementation steps.")


class PlanRequest(CamelModel):
    context: RepositorySnapshotContext
    task: str = Field(min_length=1, max_length=2000)
    analysis: AnalysisResult | None = None


class CodeChange(CamelModel):
    path: str
    action: Literal["modify", "create", "delete"]
    content: str = Field(description="Complete resulting file content; empty for delete.")
    explanation: str


class GenerationResult(CamelModel):
    changes: list[CodeChange]


class GenerateRequest(CamelModel):
    context: RepositorySnapshotContext
    task: str = Field(min_length=1, max_length=2000)
    plan: EngineeringPlan


class RepositoryRef(CamelModel):
    """Where the sandbox clones from. The token is only needed for private repositories."""

    clone_url: str
    base_branch: str
    base_sha: str | None = None
    # repr=False keeps the token out of logs, tracebacks and traces.
    access_token: str | None = Field(default=None, repr=False)


class SandboxResult(CamelModel):
    # "infrastructure_error" means the sandbox itself failed (clone, toolchain,
    # baseline install); it says nothing about the generated code.
    # "no_tests" means the test command found nothing to run.
    outcome: Literal["passed", "tests_failed", "no_tests", "infrastructure_error"]
    command: str
    exit_code: int | None = None
    stdout: str = ""
    stderr: str = ""
    duration_seconds: float = 0.0
    timed_out: bool = False
    message: str | None = None


class SandboxRunRequest(CamelModel):
    repository: RepositoryRef
    project_type: str
    # Folder holding the build manifest, relative to the repository root.
    project_root: str = ""
    changes: list[CodeChange] = []


class SandboxRunResponse(CamelModel):
    result: SandboxResult
    sandbox_seconds: float
    estimated_cost_usd: float


class FailureDiagnosis(CamelModel):
    # Field order matters: the model must state the cause before proposing a fix.
    root_cause: str = Field(description="Why the tests failed, citing the test output.")
    fix_strategy: str = Field(description="What to change to fix the root cause.")
    files_to_change: list[str] = Field(description="Repository paths that must be rewritten.")


class AttemptRecord(CamelModel):
    number: int
    changes: list[CodeChange]
    sandbox_result: SandboxResult
    # Why this attempt failed. Empty when it passed, or when no further
    # attempt was allowed and so no diagnosis was requested.
    diagnosis: FailureDiagnosis | None = None


class LoopResult(CamelModel):
    # "no_tests": the repository has no tests, so the changes are unverified.
    outcome: Literal[
        "passed", "tests_failed", "no_tests", "sandbox_unavailable", "infrastructure_error"
    ]
    final_changes: list[CodeChange]
    attempts: list[AttemptRecord]
    sandbox_seconds: float = 0.0
    estimated_cost_usd: float = 0.0
    message: str | None = None


class SolveRequest(CamelModel):
    context: RepositorySnapshotContext
    task: str = Field(min_length=1, max_length=2000)
    repository: RepositoryRef
    # Supply a plan the user already reviewed, or leave empty to plan here.
    plan: EngineeringPlan | None = None
    analysis: AnalysisResult | None = None


class SolveResponse(CamelModel):
    plan: EngineeringPlan
    result: LoopResult
