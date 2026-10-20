"""Measures how often the agent solves a fixed set of tasks.

    python -m evals.run --repo priyanshujhagithub/demo --branch main
    python -m evals.run --repo owner/name --max-tasks 2
    python -m evals.run --repo owner/name --sha <commit>   # same starting code every time

A task counts as solved only if the agent's own loop passed AND a hidden
acceptance test, which the agent never sees, also passes on top of its final
changes. Without that second check an agent could "pass" by writing weak tests.

Every task uses real model calls and real sandbox time. Use --max-tasks to cap
a sweep; the summary prints the sandbox credit the sweep is estimated to have used.
"""

import argparse
import json
import sys
import time
from dataclasses import asdict, dataclass
from datetime import UTC, datetime
from pathlib import Path

from app.config import Settings
from app.correction_loop import CorrectionLoop
from app.generator import CodeGenerator
from app.llm import LLMClient, LLMOutputError, LLMUnavailableError
from app.planner import TaskPlanner
from app.sandbox import (
    E2BSandboxRunner,
    SandboxRunner,
    SandboxSetupError,
    SandboxUnavailableError,
    find_project_root,
)
from app.schemas import CodeChange, RepositoryRef, RepositorySnapshotContext

from .snapshot import fetch_public_snapshot

TASKS_FILE = Path(__file__).parent / "tasks.json"
RESULTS_DIR = Path(__file__).parent / "results"


@dataclass
class TaskOutcome:
    task_id: str
    # What the agent's own loop concluded: passed, tests_failed, ...
    loop_outcome: str
    attempts: int
    # None when the loop did not pass, so there was nothing to accept.
    accepted: bool | None
    seconds: float
    sandbox_seconds: float
    estimated_cost_usd: float
    message: str | None = None

    @property
    def solved(self) -> bool:
        return self.loop_outcome == "passed" and self.accepted is True


def run_task(
    task: dict,
    context: RepositorySnapshotContext,
    repository: RepositoryRef,
    planner: TaskPlanner,
    loop: CorrectionLoop,
    sandbox_runner: SandboxRunner,
) -> TaskOutcome:
    started = time.monotonic()
    try:
        plan = planner.plan(context, task["task"])
        result = loop.run(context, task["task"], plan, repository)
    except (LLMOutputError, LLMUnavailableError) as exc:
        # Recorded as this task's result: one bad answer, or one spell of an
        # unreachable provider, must not end the whole sweep.
        return TaskOutcome(
            task_id=task["id"],
            loop_outcome=(
                "no_valid_output" if isinstance(exc, LLMOutputError) else "model_unavailable"
            ),
            attempts=0,
            accepted=None,
            seconds=round(time.monotonic() - started, 1),
            sandbox_seconds=0.0,
            estimated_cost_usd=0.0,
            message=str(exc),
        )

    accepted = None
    sandbox_seconds = result.sandbox_seconds
    cost = result.estimated_cost_usd
    message = result.message

    if result.outcome == "passed":
        accepted, usage, note = _accept(
            task, context, repository, result.final_changes, sandbox_runner
        )
        sandbox_seconds += usage[0]
        cost += usage[1]
        message = note or message

    return TaskOutcome(
        task_id=task["id"],
        loop_outcome=result.outcome,
        attempts=len(result.attempts),
        accepted=accepted,
        seconds=round(time.monotonic() - started, 1),
        sandbox_seconds=round(sandbox_seconds, 1),
        estimated_cost_usd=round(cost, 5),
        message=message,
    )


def _accept(
    task: dict,
    context: RepositorySnapshotContext,
    repository: RepositoryRef,
    changes: list[CodeChange],
    sandbox_runner: SandboxRunner,
) -> tuple[bool | None, tuple[float, float], str | None]:
    """Run the repository's tests again with the hidden acceptance test added."""
    project_type = context.metadata.projectType
    root = find_project_root((file.path for file in context.files), project_type)
    prefix = f"{root}/" if root else ""
    hidden = CodeChange(
        path=f"{prefix}tests/test_acceptance_{task['id'].replace('-', '_')}.py",
        action="create",
        content=task["acceptance"],
        explanation="Hidden acceptance test.",
    )

    try:
        session = sandbox_runner.open(repository, project_type, root)
    except SandboxUnavailableError as exc:
        return None, (0.0, 0.0), f"Acceptance not run: {exc}"
    except SandboxSetupError as exc:
        usage = (exc.usage.sandbox_seconds, exc.usage.estimated_cost_usd)
        return None, usage, f"Acceptance not run: {exc}"

    try:
        result = session.run_tests([*changes, hidden])
    finally:
        usage = session.close()

    note = None if result.outcome == "passed" else "The hidden acceptance test failed."
    return (
        result.outcome == "passed",
        (usage.sandbox_seconds, usage.estimated_cost_usd),
        note,
    )


def summarise(outcomes: list[TaskOutcome], model: str) -> str:
    solved = sum(outcome.solved for outcome in outcomes)
    loop_passed = sum(outcome.loop_outcome == "passed" for outcome in outcomes)
    total = len(outcomes)
    attempts = [outcome.attempts for outcome in outcomes if outcome.attempts]

    lines = [
        f"Model: {model}",
        f"Solved (loop passed and hidden test passed): {solved}/{total}",
        f"Loop reported passing: {loop_passed}/{total}",
        f"Mean attempts: {sum(attempts) / len(attempts):.1f}" if attempts else "Mean attempts: n/a",
        f"Total time: {sum(o.seconds for o in outcomes) / 60:.1f} min",
        f"Sandbox time: {sum(o.sandbox_seconds for o in outcomes):.0f} s "
        f"(estimated ${sum(o.estimated_cost_usd for o in outcomes):.4f} of E2B credit)",
        "",
        "| Task | Loop outcome | Attempts | Hidden test | Time (s) |",
        "|---|---|---|---|---|",
    ]
    for outcome in outcomes:
        hidden = {True: "passed", False: "failed", None: "not run"}[outcome.accepted]
        lines.append(
            f"| {outcome.task_id} | {outcome.loop_outcome} | {outcome.attempts} "
            f"| {hidden} | {outcome.seconds:.0f} |"
        )
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--repo", required=True, help="Public GitHub repository, owner/name")
    parser.add_argument("--branch", default="main")
    parser.add_argument("--sha", help="Commit to start from (default: the branch head)")
    parser.add_argument("--max-tasks", type=int, help="Run at most this many tasks")
    parser.add_argument("--task", action="append", help="Run only this task id (repeatable)")
    args = parser.parse_args(argv)

    tasks = json.loads(TASKS_FILE.read_text(encoding="utf-8"))
    if args.task:
        tasks = [task for task in tasks if task["id"] in args.task]
    if args.max_tasks:
        tasks = tasks[: args.max_tasks]
    if not tasks:
        print("No tasks selected.", file=sys.stderr)
        return 2

    owner, _, name = args.repo.partition("/")
    context, repository = fetch_public_snapshot(owner, name, args.branch, args.sha)

    settings = Settings.from_env()
    llm = LLMClient(settings=settings)
    sandbox_runner = E2BSandboxRunner(settings)
    planner = TaskPlanner(llm)
    loop = CorrectionLoop(CodeGenerator(llm), sandbox_runner, llm)

    outcomes = []
    for index, task in enumerate(tasks, start=1):
        print(f"[{index}/{len(tasks)}] {task['id']} ...", flush=True)
        outcome = run_task(task, context, repository, planner, loop, sandbox_runner)
        verdict = "SOLVED" if outcome.solved else "not solved"
        print(f"    {verdict}: {outcome.loop_outcome}, {outcome.attempts} attempt(s), "
              f"{outcome.seconds:.0f}s", flush=True)
        outcomes.append(outcome)

    summary = summarise(outcomes, settings.llm_model)
    print("\n" + summary)

    RESULTS_DIR.mkdir(exist_ok=True)
    stamp = datetime.now(UTC).strftime("%Y%m%dT%H%M%SZ")
    report = {
        "model": settings.llm_model,
        "repository": args.repo,
        "baseSha": repository.base_sha,
        "startedAt": stamp,
        "outcomes": [asdict(outcome) | {"solved": outcome.solved} for outcome in outcomes],
    }
    path = RESULTS_DIR / f"{stamp}.json"
    path.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(f"\nSaved {path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
