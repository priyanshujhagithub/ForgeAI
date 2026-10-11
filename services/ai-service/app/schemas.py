from pydantic import BaseModel, ConfigDict, Field


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
        description="Important classes, modules, services, or components identified in the repository."
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
        description="Files that are especially important for understanding or modifying this repository."
    )