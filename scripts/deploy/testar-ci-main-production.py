#!/usr/bin/env python3
"""Focal CI-reuse contract: synthetic GitHub responses/archives; no network or Docker."""
import contextlib
import copy
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import unittest
from unittest.mock import patch
import zipfile


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "main_ci_gate", Path(__file__).with_name("validar-ci-main-production.py"))
GATE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GATE)
SHA = "a" * 40
OTHER_SHA = "b" * 40
REPO_ID = 123
WORKFLOW_ID = 456
RUN_ID = 789
PREFIX = "repos/" + GATE.REPOSITORY
RUN_PATH = PREFIX + "/actions/runs/" + str(RUN_ID)
RUNS_PATH = (PREFIX + f"/actions/workflows/{WORKFLOW_ID}/runs"
             f"?event=push&branch=main&head_sha={SHA}&per_page=100")
STARTED = "2026-01-01T00:01:00Z"
CREATED = "2026-01-01T00:02:00Z"
COMPLETED = "2026-01-01T00:03:00Z"


def zipped(files):
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for name, body in files.items():
            entry = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 2, 0))
            archive.writestr(entry, body)
    return buffer.getvalue()


def suite_xml(suffix, *, skipped=0, failures=0, errors=0, child=""):
    return (f'<testsuite name="br.com.topsdojob.v3.{suffix}" tests="1" '
            f'failures="{failures}" errors="{errors}" skipped="{skipped}">'
            f'<testcase name="syntheticCase">{child}</testcase></testsuite>')


class SyntheticAPI:
    """Only exact expected read-only requests are available to the real validator."""
    def __init__(self):
        repo = {"id": REPO_ID, "full_name": GATE.REPOSITORY}
        self.run = {
            "id": RUN_ID, "status": "completed", "conclusion": "success",
            "head_sha": SHA, "event": "push", "head_branch": "main",
            "workflow_id": WORKFLOW_ID, "path": GATE.WORKFLOW, "run_attempt": 2,
            "repository": copy.deepcopy(repo), "head_repository": copy.deepcopy(repo),
        }
        self.jobs = [{
            "name": name, "status": "completed", "conclusion": "success",
            "run_id": RUN_ID, "head_sha": SHA, "run_attempt": 2,
            "started_at": STARTED, "completed_at": COMPLETED,
            "steps": [{"name": step, "status": "completed", "conclusion": "success"}
                      for step in steps],
        } for name, steps in GATE.JOBS.items()]
        self.artifacts = [{
            "id": 1000 + index, "name": name, "expired": False,
            "created_at": CREATED, "size_in_bytes": 1,
            "workflow_run": {
                "id": RUN_ID, "head_sha": SHA, "head_branch": "main",
                "repository_id": REPO_ID, "head_repository_id": REPO_ID,
            },
        } for index, name in enumerate(GATE.ARTIFACTS)]
        self.data = {
            PREFIX: repo,
            PREFIX + "/actions/workflows/ci.yml": {
                "id": WORKFLOW_ID, "path": GATE.WORKFLOW, "state": "active"},
            RUNS_PATH: {"total_count": 1, "workflow_runs": [{"id": RUN_ID}]},
            RUN_PATH: self.run,
            RUN_PATH + "/attempts/2/jobs?per_page=100": {
                "total_count": len(self.jobs), "jobs": self.jobs},
            RUN_PATH + "/artifacts?per_page=100": {
                "total_count": len(self.artifacts), "artifacts": self.artifacts},
        }
        self.files = {}
        self.archives = {}
        self.calls = []
        self.refreshed_run = None
        self.run_reads = 0
        for artifact in self.artifacts:
            self.replace_files(artifact["name"], {"evidence.txt": "synthetic evidence"})
        self.replace_files("backend-reconciliation-evidence", {
            f"surefire-reports/TEST-{suffix}.xml": suite_xml(suffix)
            for suffix in GATE.ESSENTIAL_SUITES
        })
        operation = {name + ".log": marker + "\n" for name, marker in GATE.GATES.items()}
        operation["testar-transicao-previews-production.log"] = "Ran 40 tests in 0.01s\n\nOK\n"
        operation["testar-operacao-containers-production.log"] += (
            "CONTAINER_TEST_CLEANUP exit=0 owned_resources_only=true\n")
        operation["testar-backfill-previews-production.log"] += (
            "original_exit=0 cleanup_exit=0 ambiguous=0\n")
        self.replace_files("operation-reconciliation-evidence", operation)

    def artifact(self, name):
        return next(item for item in self.artifacts if item["name"] == name)

    def replace_files(self, name, files):
        self.files[name] = files
        artifact = self.artifact(name)
        raw = zipped(files)
        self.archives[artifact["id"]] = raw
        artifact["size_in_bytes"] = len(raw)
        artifact["digest"] = "sha256:" + hashlib.sha256(raw).hexdigest()

    def json(self, path):
        self.calls.append(("json", path))
        if path == RUN_PATH:
            self.run_reads += 1
            if self.refreshed_run is not None and self.run_reads > 1:
                return copy.deepcopy(self.refreshed_run)
        if path not in self.data:
            raise AssertionError("unexpected API path: " + path)
        return copy.deepcopy(self.data[path])

    def archive(self, artifact_id):
        self.calls.append(("archive", artifact_id))
        if artifact_id not in self.archives:
            raise AssertionError("unexpected archive ID")
        return self.archives[artifact_id]


class MainCIReuseTests(unittest.TestCase):
    def setUp(self):
        self.api = SyntheticAPI()
        self.ci = (ROOT / GATE.WORKFLOW).read_text(encoding="utf-8")

    def rejected(self, reason, *, api=None, ci=None):
        with self.assertRaisesRegex(GATE.Rejected, reason):
            GATE.validate(SHA, api or self.api, self.ci if ci is None else ci)

    def test_accepts_exact_main_success_current_attempt_and_complete_evidence(self):
        result = GATE.validate(SHA, self.api, self.ci)
        self.assertEqual(result, {
            "result": "MAIN_CI_REUSE=PASS", "sha": SHA, "run_id": RUN_ID,
            "attempt": 2, "jobs": 3, "artifacts": 5,
        })
        self.assertEqual(self.api.run_reads, 2)
        self.assertEqual([call[1] for call in self.api.calls if call[0] == "archive"], [1000, 1003])

    def test_rejects_abbreviated_or_nonhex_sha_before_any_api_read(self):
        for value in (SHA[:7], "g" * 40, SHA.upper(), ""):
            with self.subTest(sha=value), self.assertRaisesRegex(GATE.Rejected, "SHA completo"):
                GATE.validate(value, self.api, self.ci)
        self.assertEqual(self.api.calls, [])

    def test_rejects_repository_and_workflow_identity(self):
        changes = (
            (PREFIX, "full_name", "other/repository", "repositorio divergente"),
            (PREFIX + "/actions/workflows/ci.yml", "path", ".github/workflows/other.yml", "workflow CI"),
            (PREFIX + "/actions/workflows/ci.yml", "state", "disabled_manually", "workflow CI"),
        )
        for path, field, value, message in changes:
            with self.subTest(field=field):
                api = SyntheticAPI()
                api.data[path][field] = value
                self.rejected(message, api=api)

    def test_rejects_run_sha_event_ref_and_workflow_divergence(self):
        for field, value in (("head_sha", OTHER_SHA), ("event", "pull_request"),
                             ("head_branch", "other"), ("workflow_id", 999),
                             ("path", ".github/workflows/other.yml")):
            with self.subTest(field=field):
                api = SyntheticAPI()
                api.run[field] = value
                self.rejected("divergente", api=api)

    def test_rejects_response_run_id_different_from_requested_run(self):
        self.api.run["id"] = RUN_ID - 1
        self.rejected("ID do run divergente")

    def test_rejects_fork_or_wrong_repository_in_run(self):
        for field in ("repository", "head_repository"):
            for key, value in (("id", 999), ("full_name", "fork/topsv3")):
                with self.subTest(field=field, key=key):
                    api = SyntheticAPI()
                    api.run[field][key] = value
                    self.rejected("origem do run", api=api)

    def test_rejects_incomplete_failed_or_skipped_run(self):
        for status, conclusion in (("in_progress", None), ("queued", None),
                                   ("completed", "failure"), ("completed", "cancelled"),
                                   ("completed", "skipped")):
            with self.subTest(status=status, conclusion=conclusion):
                api = SyntheticAPI()
                api.run.update(status=status, conclusion=conclusion)
                self.rejected("CI mais recente", api=api)

    def test_does_not_fall_back_to_older_success(self):
        latest = copy.deepcopy(self.api.run)
        latest.update(id=RUN_ID + 1, conclusion="failure")
        self.api.data[RUNS_PATH] = {
            "total_count": 2, "workflow_runs": [{"id": RUN_ID + 1}, {"id": RUN_ID}]}
        self.api.data[PREFIX + "/actions/runs/" + str(RUN_ID + 1)] = latest
        self.rejected("CI mais recente")
        self.assertNotIn(("json", RUN_PATH), self.api.calls)
        self.assertFalse(any(call[0] == "archive" for call in self.api.calls))

    def test_rejects_missing_run(self):
        self.api.data[RUNS_PATH] = {"total_count": 0, "workflow_runs": []}
        self.rejected("CI push/main ausente")

    def test_rejects_incomplete_paginated_lists(self):
        for path in (RUNS_PATH, RUN_PATH + "/attempts/2/jobs?per_page=100",
                     RUN_PATH + "/artifacts?per_page=100"):
            with self.subTest(path=path):
                api = SyntheticAPI()
                api.data[path]["total_count"] += 1
                self.rejected("resposta incompleta", api=api)

    def test_rejects_invalid_attempt(self):
        for attempt in (0, -1, "2", True):
            with self.subTest(attempt=attempt):
                api = SyntheticAPI()
                api.run["run_attempt"] = attempt
                self.rejected("tentativa invalida", api=api)

    def test_rejects_missing_or_duplicate_jobs(self):
        self.api.jobs.pop()
        self.api.data[RUN_PATH + "/attempts/2/jobs?per_page=100"]["total_count"] -= 1
        self.rejected("jobs obrigatorios")
        api = SyntheticAPI()
        api.jobs[1] = copy.deepcopy(api.jobs[0])
        self.rejected("jobs obrigatorios", api=api)

    def test_rejects_failed_skipped_or_incomplete_job(self):
        for status, conclusion in (("completed", "failure"), ("completed", "skipped"),
                                   ("in_progress", None)):
            with self.subTest(status=status, conclusion=conclusion):
                api = SyntheticAPI()
                api.jobs[0].update(status=status, conclusion=conclusion)
                self.rejected("job Backend", api=api)

    def test_rejects_job_from_another_sha_run_or_attempt(self):
        for field, value in (("head_sha", OTHER_SHA), ("run_id", RUN_ID - 1), ("run_attempt", 1)):
            with self.subTest(field=field):
                api = SyntheticAPI()
                api.jobs[0][field] = value
                self.rejected("job de outra identidade/tentativa", api=api)

    def test_rejects_missing_mandatory_step(self):
        for index, job in enumerate(self.api.jobs):
            with self.subTest(job=job["name"]):
                api = SyntheticAPI()
                api.jobs[index]["steps"].pop()
                self.rejected("etapa obrigatoria ausente", api=api)

    def test_rejects_skipped_failed_or_incomplete_step(self):
        for status, conclusion in (("completed", "skipped"), ("completed", "failure"),
                                   ("in_progress", None)):
            with self.subTest(status=status, conclusion=conclusion):
                api = SyntheticAPI()
                api.jobs[0]["steps"][0].update(status=status, conclusion=conclusion)
                self.rejected("etapa Checkout", api=api)

    def test_rejects_ambiguous_steps(self):
        self.api.jobs[0]["steps"].append(copy.deepcopy(self.api.jobs[0]["steps"][0]))
        self.rejected("etapas ambiguas")

    def test_rejects_each_missing_artifact(self):
        for name in GATE.ARTIFACTS:
            with self.subTest(artifact=name):
                api = SyntheticAPI()
                api.artifacts.remove(api.artifact(name))
                api.data[RUN_PATH + "/artifacts?per_page=100"]["total_count"] -= 1
                self.rejected("artefato obrigatorio ausente", api=api)

    def test_rejects_duplicate_artifact(self):
        self.api.artifacts.append(copy.deepcopy(self.api.artifacts[0]))
        self.api.data[RUN_PATH + "/artifacts?per_page=100"]["total_count"] += 1
        self.rejected("artefato obrigatorio ausente/ambiguo")

    def test_rejects_empty_expired_or_old_attempt_artifact(self):
        for field, value, message in (
                ("size_in_bytes", 0, "vazio/expirado"), ("expired", True, "vazio/expirado"),
                ("created_at", "2025-12-31T23:59:00Z", "fora da tentativa"),
                ("created_at", "2026-01-01T00:04:00Z", "fora da tentativa")):
            with self.subTest(field=field, value=value):
                api = SyntheticAPI()
                api.artifacts[0][field] = value
                self.rejected(message, api=api)

    def test_rejects_evidence_timestamp_without_timezone(self):
        for target, key in (("artifact", "created_at"), ("job", "started_at"),
                            ("job", "completed_at")):
            with self.subTest(target=target, key=key):
                api = SyntheticAPI()
                record = api.artifacts[0] if target == "artifact" else api.jobs[0]
                record[key] = record[key].removesuffix("Z")
                self.rejected("timestamp de evidencia sem fuso", api=api)

    def test_rejects_wrong_artifact_identity(self):
        for field, value in (("id", RUN_ID - 1), ("head_sha", OTHER_SHA),
                             ("head_branch", "branch"), ("repository_id", 999),
                             ("head_repository_id", 999)):
            with self.subTest(field=field):
                api = SyntheticAPI()
                api.artifacts[0]["workflow_run"][field] = value
                self.rejected("identidade do artefato", api=api)

    def test_rejects_archive_digest_mismatch(self):
        for name in ("backend-reconciliation-evidence", "operation-reconciliation-evidence"):
            with self.subTest(name=name):
                api = SyntheticAPI()
                api.artifact(name)["digest"] = "sha256:" + "0" * 64
                self.rejected("digest do artefato", api=api)

    def test_rejects_missing_essential_suite(self):
        files = self.api.files["backend-reconciliation-evidence"]
        files.pop(next(iter(files)))
        self.api.replace_files("backend-reconciliation-evidence", files)
        self.rejected("suite essencial ausente")

    def test_rejects_skipped_or_failed_essential_suite(self):
        variants = (
            {"skipped": 1}, {"child": "<skipped/>"}, {"failures": 1},
            {"errors": 1}, {"child": "<failure message='synthetic'/>"},
            {"child": "<error message='synthetic'/>"},
        )
        for variant in variants:
            with self.subTest(variant=variant):
                api = SyntheticAPI()
                files = api.files["backend-reconciliation-evidence"]
                files[next(iter(files))] = suite_xml(GATE.ESSENTIAL_SUITES[0], **variant)
                api.replace_files("backend-reconciliation-evidence", files)
                self.rejected("suite essencial omitida|falha", api=api)

    def test_rejects_incomplete_xml_case_count(self):
        files = self.api.files["backend-reconciliation-evidence"]
        name = next(iter(files))
        files[name] = files[name].replace('tests="1"', 'tests="2"')
        self.api.replace_files("backend-reconciliation-evidence", files)
        self.rejected("relatorio Surefire incompleto")

    def test_rejects_xml_entity_declarations(self):
        files = self.api.files["backend-reconciliation-evidence"]
        name = next(iter(files))
        files[name] = '<!DOCTYPE testsuite [<!ENTITY value "synthetic">]>' + files[name]
        self.api.replace_files("backend-reconciliation-evidence", files)
        self.rejected("XML de testes invalido")

    def test_rejects_each_missing_operation_marker(self):
        for gate in GATE.GATES:
            with self.subTest(gate=gate):
                api = SyntheticAPI()
                files = api.files["operation-reconciliation-evidence"]
                files[gate + ".log"] = files[gate + ".log"].replace(GATE.GATES[gate], "NOT_PROVEN")
                api.replace_files("operation-reconciliation-evidence", files)
                self.rejected("evidencia operacional ausente", api=api)

    def test_rejects_ambiguous_or_failed_cleanup(self):
        changes = (
            ("testar-backfill-previews-production.log", "ambiguous=0", "ambiguous=1"),
            ("testar-backfill-previews-production.log", "cleanup_exit=0", "cleanup_exit=1"),
            ("testar-operacao-containers-production.log", "exit=0", "exit=1"),
            ("testar-operacao-containers-production.log", "owned_resources_only=true", "owned_resources_only=false"),
        )
        for name, old, new in changes:
            with self.subTest(name=name, new=new):
                api = SyntheticAPI()
                files = api.files["operation-reconciliation-evidence"]
                files[name] = files[name].replace(old, new)
                api.replace_files("operation-reconciliation-evidence", files)
                self.rejected("cleanup .* nao comprovado", api=api)

    def test_rejects_incomplete_python_test_log(self):
        for log in ("Ran 0 tests in 0.01s\nOK\n", "Ran 40 tests in 0.01s\nFAILED\n"):
            with self.subTest(log=log):
                api = SyntheticAPI()
                files = api.files["operation-reconciliation-evidence"]
                files["testar-transicao-previews-production.log"] = log
                api.replace_files("operation-reconciliation-evidence", files)
                self.rejected("testes Python incompletos", api=api)

    def test_rejects_ci_changed_during_validation(self):
        self.api.refreshed_run = copy.deepcopy(self.api.run)
        self.api.refreshed_run["run_attempt"] = 3
        self.rejected("CI mudou durante")

    def test_rejects_every_disabled_backend_integration(self):
        for flag in GATE.FLAGS:
            with self.subTest(flag=flag):
                changed = self.ci.replace(flag + ': "true"', flag + ': "false"')
                self.assertNotEqual(changed, self.ci)
                self.rejected("integracao desabilitada", ci=changed)

    def test_rejects_changed_maven_command(self):
        changed = self.ci.replace("run: mvn --batch-mode --no-transfer-progress verify",
                                  "run: mvn --batch-mode --no-transfer-progress test")
        self.assertNotEqual(changed, self.ci)
        self.rejected("comando Maven nao equivalente", ci=changed)

    def test_rejects_changed_java_distribution_or_version(self):
        for old, new in (("distribution: temurin", "distribution: zulu"),
                         ('java-version: "17"', 'java-version: "21"')):
            with self.subTest(old=old):
                changed = self.ci.replace(old, new)
                self.assertNotEqual(changed, self.ci)
                self.rejected("Java CI nao equivalente", ci=changed)

    def test_rejects_changed_node_version(self):
        changed = self.ci.replace('node-version: "22.13.1"', 'node-version: "24"')
        self.assertNotEqual(changed, self.ci)
        self.rejected("Node CI nao equivalente", ci=changed)

    def test_rejects_changed_backend_or_operation_test_image(self):
        for old, new in (("eclipse-temurin:17.0.13_11-jre; do", "eclipse-temurin:17-jre; do"),
                         ("nginx:1.27-alpine python:3.12-slim-bookworm; do",
                          "nginx:1.28-alpine python:3.12-slim-bookworm; do")):
            with self.subTest(old=old):
                changed = self.ci.replace(old, new)
                self.assertNotEqual(changed, self.ci)
                self.rejected("imagens de teste CI nao equivalentes", ci=changed)

    def test_rejects_checkout_ref_path_or_sparse_override_in_each_job(self):
        checkout = "          fetch-depth: 1\n"
        self.assertEqual(self.ci.count(checkout), len(GATE.JOBS))
        for index in range(len(GATE.JOBS)):
            for override in ("ref: other", "path: other", "sparse-checkout: backend"):
                with self.subTest(job=index, override=override):
                    pieces = self.ci.split(checkout)
                    pieces[index + 1] = "          " + override + "\n" + pieces[index + 1]
                    changed = checkout.join(pieces)
                    self.rejected("checkout CI nao corresponde", ci=changed)

    def test_rejects_removed_operation_gate_or_result_propagation(self):
        for old, new in (("testar-backup-validado-production testar-operacao-containers-production; do",
                          "testar-operacao-containers-production; do"),
                         ('codes=("${PIPESTATUS[@]}")', "codes=(0 0)"),
                         ('exit "$first_failure"', "exit 0")):
            with self.subTest(old=old):
                changed = self.ci.replace(old, new)
                self.assertNotEqual(changed, self.ci)
                self.rejected("cobertura operacional|propagacao do resultado", ci=changed)

    def cli(self, *, api=None, environment=None, checkout=SHA, argv=None):
        stdout, stderr = io.StringIO(), io.StringIO()
        env = {"GITHUB_REPOSITORY": GATE.REPOSITORY, "GITHUB_REF": "refs/heads/main", "GITHUB_SHA": SHA}
        env.update(environment or {})
        with patch.object(GATE.sys, "argv", argv or ["gate", SHA]), \
                patch.dict(os.environ, env, clear=True), \
                patch.object(GATE.subprocess, "check_output", return_value=checkout + "\n"), \
                patch.object(GATE, "GitHub", return_value=api or self.api), \
                contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            code = GATE.main()
        return code, stdout.getvalue(), stderr.getvalue()

    def test_cli_success_outputs_reusable_identity(self):
        code, stdout, stderr = self.cli()
        self.assertEqual(code, 0)
        self.assertEqual(stderr, "")
        self.assertEqual(json.loads(stdout)["sha"], SHA)

    def test_cli_rejects_wrong_dispatch_repository_ref_or_checkout(self):
        cases = (
            {"environment": {"GITHUB_REPOSITORY": "fork/topsv3"}},
            {"environment": {"GITHUB_REF": "refs/heads/feature"}},
            {"environment": {"GITHUB_SHA": OTHER_SHA}}, {"checkout": OTHER_SHA},
        )
        for arguments in cases:
            with self.subTest(arguments=arguments):
                code, stdout, stderr = self.cli(**arguments)
                self.assertEqual(code, 1)
                self.assertEqual(stdout, "")
                self.assertIn("MAIN_CI_REUSE=BLOCKED", stderr)
        self.assertEqual(self.api.calls, [])

    def test_cli_missing_or_malformed_evidence_fails_closed_without_body(self):
        api = SyntheticAPI()
        del api.artifacts[0]["digest"]
        code, stdout, stderr = self.cli(api=api)
        self.assertEqual((code, stdout), (1, ""))
        self.assertIn("evidencia indisponivel/invalida (KeyError)", stderr)
        self.assertNotIn("digest", stderr)
        api = SyntheticAPI()
        files = api.files["backend-reconciliation-evidence"]
        files[next(iter(files))] = "<invalid synthetic-private-body"
        api.replace_files("backend-reconciliation-evidence", files)
        code, stdout, stderr = self.cli(api=api)
        self.assertEqual((code, stdout), (1, ""))
        self.assertIn("ParseError", stderr)
        self.assertNotIn("synthetic-private-body", stderr)

    def test_cli_api_error_fails_closed(self):
        with patch.object(self.api, "json", side_effect=OSError("synthetic-private-body")):
            code, stdout, stderr = self.cli()
        self.assertEqual((code, stdout), (1, ""))
        self.assertIn("MAIN_CI_REUSE=BLOCKED", stderr)
        self.assertNotIn("synthetic-private-body", stderr)


if __name__ == "__main__":
    unittest.main(verbosity=2)
