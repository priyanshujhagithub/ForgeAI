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
