"""ForgeAI demo UI.

Deliberately thin: every action is one call to the Java API, and the page is a
view of the agent-run record that API returns. Run with:

    streamlit run ui/app.py
"""

import difflib
import os
import time

import streamlit as st

from api import ApiError, ForgeApi

API_URL = os.getenv("FORGEAI_API_URL", "http://localhost:8080")
POLL_SECONDS = 5

STEPS = ["QUEUED", "ANALYZING", "PLANNING", "GENERATING_AND_TESTING", "OPENING_PR", "COMPLETED"]
STEP_LABELS = {
    "QUEUED": "Queued",
    "ANALYZING": "Analysing repository",
    "PLANNING": "Planning",
    "GENERATING_AND_TESTING": "Generating and testing",
    "OPENING_PR": "Opening pull request",
    "COMPLETED": "Completed",
}
END_STATES = {
    "COMPLETED": ("success", "Tests passed and a pull request is open."),
    "TESTS_FAILED": ("error", "Tests were still failing after the last attempt. No pull request."),
    "SANDBOX_UNAVAILABLE": (
        "warning",
        "Changes were generated but could not be tested. No pull request.",
    ),
    "FAILED": ("error", "The run failed."),
}


def get_api() -> ForgeApi:
    return ForgeApi(API_URL, token=st.session_state.get("token"))


def show_error(error: ApiError) -> None:
    st.error(error.detail)


# --- sidebar: account, GitHub, project --------------------------------------


def sidebar_account(api: ForgeApi) -> bool:
    st.sidebar.header("Account")

    if st.session_state.get("token"):
        st.sidebar.write(f"Signed in as **{st.session_state['email']}**")
        if st.sidebar.button("Sign out"):
            st.session_state.clear()
            st.rerun()
        return True

    with st.sidebar.form("login"):
        email = st.text_input("Email")
        password = st.text_input("Password", type="password")
        sign_in = st.form_submit_button("Sign in")
        create = st.form_submit_button("Create account")

    if sign_in or create:
        try:
            if create:
                api.register(email, password)
            st.session_state["token"] = api.login(email, password)
            st.session_state["email"] = email
            st.rerun()
        except ApiError as error:
            show_error(error)
    return False


def sidebar_github(api: ForgeApi) -> bool:
    st.sidebar.header("GitHub")
    try:
        connection = api.github_connection()
    except ApiError as error:
        show_error(error)
        return False

    if connection["connected"]:
        st.sidebar.write(f"Connected as **{connection['githubUsername']}**")
        return True

    st.sidebar.write("Not connected.")
    try:
        st.sidebar.link_button("Connect GitHub", api.github_authorization_url())
        st.sidebar.caption("Approve access in the tab that opens, then come back here.")
        # A button, not a page reload: reloading the page would end the sign-in session.
        st.sidebar.button("I've approved access")
    except ApiError as error:
        show_error(error)
    return False


def sidebar_project(api: ForgeApi) -> dict | None:
    st.sidebar.header("Project")
    try:
        projects = api.projects()
    except ApiError as error:
        show_error(error)
        return None

    project = None
    if projects:
        project = st.sidebar.selectbox(
            "Project",
            projects,
            format_func=lambda p: f"{p['name']} ({repository_label(p)})",
        )

    with st.sidebar.expander("New project"):
        with st.form("new-project"):
            name = st.text_input("Name")
            owner = st.text_input("Repository owner")
            repository = st.text_input("Repository name")
            if st.form_submit_button("Create and connect"):
                try:
                    created = api.create_project(name, f"{owner}/{repository}")
                    api.connect_repository(created["id"], owner, repository)
                    st.rerun()
                except ApiError as error:
                    show_error(error)
    return project


def repository_label(project: dict) -> str:
    if project.get("githubOwner") and project.get("githubRepoName"):
        return f"{project['githubOwner']}/{project['githubRepoName']}"
    return "no repository"


# --- main: start a run, watch it --------------------------------------------


def start_run_form(api: ForgeApi, project: dict) -> None:
    with st.form("start-run"):
        task = st.text_area(
            "Task",
            placeholder="Add input validation to the user registration endpoint.",
        )
        if st.form_submit_button("Start run", type="primary"):
            try:
                st.session_state["run_id"] = api.start_run(project["id"], task)["id"]
                st.rerun()
            except ApiError as error:
                show_error(error)


def run_history(api: ForgeApi, project: dict) -> None:
    try:
        runs = api.runs(project["id"])
    except ApiError as error:
        show_error(error)
        return
    if not runs:
        return

    with st.expander(f"Previous runs ({len(runs)})"):
        for run in runs:
            label = f"{run['status']}: {run['task'][:70]}"
            if st.button(label, key=f"open-{run['id']}"):
                st.session_state["run_id"] = run["id"]
                st.rerun()


def show_run(api: ForgeApi, project: dict, run: dict) -> None:
    st.subheader(run["task"])
    show_progress(run)

    analysis_tab, plan_tab, changes_tab, attempts_tab = st.tabs(
        ["Analysis", "Plan", "Changes", "Test attempts"]
    )
    with analysis_tab:
        show_analysis(run.get("analysis"))
    with plan_tab:
        show_plan(run.get("plan"))
    with changes_tab:
        show_changes(api, project, run)
    with attempts_tab:
        show_attempts(run.get("result"))


def show_progress(run: dict) -> None:
    status = run["status"]

    if status in STEPS:
        position = STEPS.index(status)
        st.progress(position / (len(STEPS) - 1), text=STEP_LABELS[status])

    if status in END_STATES:
        kind, message = END_STATES[status]
        getattr(st, kind)(message)
    if run.get("errorMessage"):
        st.caption(run["errorMessage"])
    if run.get("pullRequestUrl"):
        st.link_button(f"Open pull request #{run['pullRequestNumber']}", run["pullRequestUrl"])
    if run.get("sandboxSeconds") is not None:
        st.caption(
            f"Sandbox time: {run['sandboxSeconds']:.1f} s. "
            f"Estimated E2B credit used: ${run['estimatedCostUsd']:.4f}."
        )


def show_analysis(analysis: dict | None) -> None:
    if not analysis:
        st.info("Not available yet.")
        return
    st.write(analysis["summary"])
    st.markdown(f"**Project type:** {analysis['projectType']}")
    st.markdown("**Architecture**")
    st.write(analysis["architectureNotes"])
    left, right = st.columns(2)
    left.markdown("**Key components**")
    left.write(analysis["keyComponents"])
    right.markdown("**Dependencies**")
    right.write(analysis["dependencies"])


def show_plan(plan: dict | None) -> None:
    if not plan:
        st.info("Not available yet.")
        return
    st.write(plan["taskUnderstanding"])
    for step in plan["steps"]:
        st.markdown(f"{step['order']}. {step['description']}")
        st.caption(", ".join(step["files"]))
    if plan["filesToCreate"]:
        st.markdown("**New files:** " + ", ".join(plan["filesToCreate"]))


def show_changes(api: ForgeApi, project: dict, run: dict) -> None:
    result = run.get("result")
    if not result or not result["finalChanges"]:
        st.info("No changes yet.")
        return

    for change in result["finalChanges"]:
        st.markdown(f"**{change['path']}** ({change['action']})")
        st.caption(change["explanation"])
        st.code(unified_diff(original_content(api, project, change), change), language="diff")


def original_content(api: ForgeApi, project: dict, change: dict) -> str:
    """The file as it is on GitHub, for the diff. New files start empty."""
    if change["action"] == "create":
        return ""
    cache = st.session_state.setdefault("originals", {})
    key = (project["id"], change["path"])
    if key not in cache:
        try:
            cache[key] = api.file(project["id"], change["path"])["content"]
        except ApiError:
            cache[key] = ""
    return cache[key]


def unified_diff(original: str, change: dict) -> str:
    new = "" if change["action"] == "delete" else change["content"]
    lines = difflib.unified_diff(
        original.splitlines(),
        new.splitlines(),
        fromfile=f"a/{change['path']}",
        tofile=f"b/{change['path']}",
        lineterm="",
    )
    return "\n".join(lines) or "(no textual difference)"


def show_attempts(result: dict | None) -> None:
    if not result or not result["attempts"]:
        st.info("No test runs yet.")
        return

    for attempt in result["attempts"]:
        sandbox = attempt["sandboxResult"]
        passed = sandbox["outcome"] == "passed"
        title = f"Attempt {attempt['number']}: {sandbox['outcome']} (exit {sandbox['exitCode']})"
        with st.expander(title, expanded=not passed):
            st.code(sandbox["command"], language="bash")
            if attempt.get("diagnosis"):
                st.markdown(f"**Root cause:** {attempt['diagnosis']['rootCause']}")
                st.markdown(f"**Fix strategy:** {attempt['diagnosis']['fixStrategy']}")
            output = "\n".join(part for part in (sandbox["stdout"], sandbox["stderr"]) if part)
            st.code(output or "(no output)", language="text")


# --- page -------------------------------------------------------------------


def main() -> None:
    st.set_page_config(page_title="ForgeAI", layout="wide")
    st.title("ForgeAI")
    st.caption("From a task to a tested pull request. A human reviews and merges.")

    api = get_api()
    if not sidebar_account(api):
        st.info("Sign in to begin.")
        return
    if not sidebar_github(api):
        st.info("Connect your GitHub account to continue.")
        return
    project = sidebar_project(api)
    if project is None:
        st.info("Create a project and connect a repository to continue.")
        return

    start_run_form(api, project)
    run_history(api, project)

    run_id = st.session_state.get("run_id")
    if not run_id:
        return
    try:
        run = api.run(run_id)
    except ApiError as error:
        show_error(error)
        return

    show_run(api, project, run)

    if run["active"]:
        time.sleep(POLL_SECONDS)
        st.rerun()


main()
