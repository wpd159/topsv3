#!/usr/bin/env python3
"""Local unit proofs; no Docker, network, files outside a temporary directory or DB."""
import copy
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("preview_runtime", Path(__file__).with_name("validar-transicao-previews-runtime.py"))
RUNTIME = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(RUNTIME)


def source():
    return {"Id": "a" * 64, "Name": "/topsv3-production-backend", "Image": "sha256:" + "b" * 64,
            "RestartCount": 0,
            "Config": {"Image": "topsv3-production-backend:" + "c" * 40,
                       "User": RUNTIME.executor_user(),
                       "Env": [key + "=" + value for key, value in RUNTIME.REQUIRED_ENV.items()] +
                              ["SPRING_DATASOURCE_URL=jdbc:postgresql://fixture/db"],
                       "Entrypoint": ["java", "-jar", "/app/app.jar"], "Cmd": RUNTIME.EXPECTED_ARGS.copy(),
                       "Labels": {"com.docker.compose.project": "topsv3-production"}},
            "State": {"Running": True, "Paused": False, "Restarting": False, "OOMKilled": False,
                      "StartedAt": "2026-01-01T00:00:00Z", "FinishedAt": "2026-01-01T00:01:00Z", "ExitCode": 0},
            "HostConfig": {"RestartPolicy": {"Name": "no"}},
            "NetworkSettings": {"Networks": {RUNTIME.NETWORK: {}}}}


def journal(mode="APPLY"):
    pairs = [("STARTED", "NOT_STARTED"), ("PLAN_READY", "NOT_STARTED")]
    if mode == "APPLY":
        pairs += [("BEFORE_CAPTURED", "NOT_STARTED"), ("APPLY_STARTED", "UNKNOWN"), ("APPLY_FINISHED", "COMMITTED")]
    commit = "COMMITTED" if mode == "APPLY" else "NOT_STARTED"
    if mode != "PLAN":
        pairs += [("VALIDATION_PASSED", commit)]
    pairs += [("SUCCEEDED", commit)]
    return [{"mode": mode, "stage": stage, "commit": value, "time_utc": "2026-01-01T00:00:00Z"}
            for stage, value in pairs]


def private_capture(path, payload, mode=0o600):
    """Synthetic unit data, created with its final mode; no ownership repair."""
    mask = os.umask(0)
    try:
        descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, mode)
    finally:
        os.umask(mask)
    with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
        handle.write(payload)


SINCE = "2026-01-01T00:00:59.000000001Z"
UNTIL = "2026-01-01T00:01:00.000000009Z"
CURRENT = "2026-01-01T00:00:59.123456789Z "
SUCCESS = CURRENT + "Graceful shutdown complete\n"


def exercise_stopped(test, stdout, stderr, *, accepted=False, rc=0,
                     capture_failure=None, child_code=None, child_timeout=None):
    """Real observer/receipt, synthetic Docker identity/SQL; optional REAL child."""
    with tempfile.TemporaryDirectory() as folder:
        current = source()
        current["State"]["Running"] = False
        current["State"]["FinishedAt"] = UNTIL
        before = {"id": current["Id"], "image": current["Image"],
                  "started_at": current["State"]["StartedAt"], "restart_count": 0,
                  "allowed_runtime": {}}
        (Path(folder) / "preview-source.json").write_text(json.dumps(before))
        pg = {"Config": {"Env": ["POSTGRES_USER=synthetic", "POSTGRES_DB=synthetic"]}}
        real_run = subprocess.run
        log_calls = []

        def run_boundary(args, **kwargs):
            if args[:2] == ["docker", "logs"]:
                test.assertEqual(args, ["docker", "logs", "--timestamps", "--since", SINCE,
                                        "--until", UNTIL, current["Id"]])
                test.assertEqual(kwargs["timeout"], 15)
                log_calls.append(args)
                if capture_failure is not None:
                    raise capture_failure
                if child_code is not None:
                    if child_timeout is not None:
                        kwargs["timeout"] = child_timeout
                    return real_run([sys.executable, "-c", child_code], **kwargs)
                return subprocess.CompletedProcess(args, rc, stdout, stderr)
            test.assertEqual(args[:5], ["docker", "exec", "-i", RUNTIME.PREFIX + "postgres", "psql"])
            test.assertIn("BEGIN READ ONLY;", kwargs["input"])
            test.assertIn("ROLLBACK;", kwargs["input"])
            return subprocess.CompletedProcess(args, 0, "0\n", "")

        observed_output = io.StringIO()
        with patch.object(RUNTIME, "inspect", side_effect=[current, pg]), \
                patch.object(RUNTIME, "no_other_producers", return_value=3) as producers, \
                patch.object(RUNTIME.subprocess, "run", side_effect=run_boundary), \
                contextlib.redirect_stdout(observed_output):
            if accepted:
                RUNTIME.stopped(folder, SINCE)
            else:
                with test.assertRaises(RuntimeError) as error:
                    RUNTIME.stopped(folder, SINCE)
                test.assertRegex(str(error.exception), r"^[a-z_]+$")
                test.assertNotIn("private-fixture-value", str(error.exception))
                producers.assert_not_called()
        test.assertEqual(len(log_calls), 1)
        receipt = Path(folder) / "preview-drained.json"
        test.assertEqual(receipt.exists(), accepted)
        if accepted:
            data = json.loads(receipt.read_text())
            test.assertEqual(data["since"], SINCE)
            test.assertEqual(data["until"], UNTIL)
            test.assertEqual(len(data["logs_stdout_sha256"]), 64)
            test.assertEqual(len(data["logs_stderr_sha256"]), 64)
            test.assertNotIn("Graceful shutdown", receipt.read_text())
            test.assertEqual(observed_output.getvalue(), "PREVIEW_PRODUCERS_DRAINED=PASS\n")
        else:
            test.assertEqual(observed_output.getvalue(), "")


class TransitionTests(unittest.TestCase):
    def test_real_job_fixture_is_required_on_ci_host_without_socket_mounts(self):
        ci = (ROOT / ".github/workflows/ci.yml").read_text().split("  verify-operation:", 1)[0]
        self.assertIn('PREVIEW_JOB_USER_INTEGRATION_ENABLED: "true"', ci)
        self.assertIn('preview-job-user-*/evidence/**', ci)
        self.assertIn('name: preview-job-user-integration-evidence', ci)
        fixture = (ROOT / "scripts/deploy/testar-backfill-previews-job-user-integrado.sh").read_text()
        for required in ('"$(id -u)" -ne 0', 'run_backfill || rc=$?', 'trap on_exit EXIT',
                         '_op_lock "$root"', 'JVM_CAPTURE_OWNER=PASS', 'JVM_ROOT_REGRESSION=PASS',
                         'PermissionError', "proof['commit'] == 'UNKNOWN'", 'pending_exit" -eq 76',
                         'JVM_CONFIGURATION_AND_IDENTITY=PASS'):
            self.assertIn(required, fixture)
        self.assertNotIn('sudo ', fixture)
        self.assertNotIn('--privileged', fixture)
        self.assertNotRegex(fixture, r'(?:--volume|--mount|-v)[^\n]*docker\.sock')
        isolated = (ROOT / "scripts/deploy/testar-backfill-previews-production.sh").read_text()
        self.assertIn('! -e /var/run/docker.sock', isolated)

    def test_source_graceful_supported(self):
        RUNTIME.validate_source(source())

    def test_451_configuration_fails_before_mutation(self):
        current = source()
        current["Config"]["Env"] = ["SPRING_PROFILES_ACTIVE=production"]
        with patch.object(RUNTIME, "inspect", return_value=current), patch.object(RUNTIME, "call") as calls:
            with tempfile.TemporaryDirectory() as folder, self.assertRaisesRegex(RuntimeError, "graceful"):
                RUNTIME.preflight(folder, "c" * 40)
            calls.assert_not_called()

    def test_overrides_signals_and_arguments_rejected(self):
        variants = []
        for name in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "SPRING_APPLICATION_JSON"):
            changed = source()
            changed["Config"]["Env"].append(name + "=synthetic-override")
            variants.append(changed)
        changed = source()
        changed["Config"]["StopSignal"] = "SIGKILL"
        variants.append(changed)
        changed = source()
        changed["Config"]["Cmd"].append("--server.shutdown=immediate")
        variants.append(changed)
        for changed in variants:
            with self.subTest(config=changed["Config"]["StopSignal"] if "StopSignal" in changed["Config"] else "override"):
                with self.assertRaises(RuntimeError):
                    RUNTIME.validate_source(changed)

    def test_external_producer_not_limited_to_project(self):
        for same_network in (True, False):
            other = source()
            other["Id"] = "d" * 64
            other["Name"] = "/unrelated-project-job"
            other["Config"]["Image"] = "isolated-backend:fixture"
            other["Config"]["Labels"] = {"com.docker.compose.project": "unrelated"}
            if not same_network:
                other["NetworkSettings"]["Networks"] = {"other": {}}
            with patch.object(RUNTIME, "call", return_value=other["Id"]), patch.object(RUNTIME, "inspect", return_value=other):
                with self.assertRaisesRegex(RuntimeError, "external_producer"):
                    RUNTIME.no_other_producers(source())

    def test_host_process_not_silently_ignored(self):
        for process in ("999999 java -jar producer.jar", "999999 docker compose run backend",
                        "999999 bash /opt/ops/executar-backfill-previews-production.sh"):
            with patch.object(RUNTIME, "call", side_effect=["", process]):
                with self.assertRaises(RuntimeError):
                    RUNTIME.no_other_producers(source())

    def test_apply_receipts_before_after_write_and_resume(self):
        for mode in ("PLAN", "APPLY", "VALIDATE"):
            RUNTIME.check_journal(journal(mode), mode)
        for events in (journal()[:3], journal()[:-1],
                       journal() + [{"mode": "APPLY", "stage": "FAILED", "commit": "COMMITTED"}],
                       journal()[:3] + [{"mode": "APPLY", "stage": "FAILED", "commit": "ROLLED_BACK"}]):
            with self.assertRaises(RuntimeError):
                RUNTIME.check_journal(events, "APPLY")
        with tempfile.TemporaryDirectory() as folder:
            RUNTIME.emit_file(folder, "receipt.json", {"commit": "COMMITTED"})
            with self.assertRaises(FileExistsError):
                RUNTIME.emit_file(folder, "receipt.json", {"commit": "NOT_STARTED"})
            self.assertEqual(json.loads((Path(folder) / "receipt.json").read_text())["commit"], "COMMITTED")

    def test_public_contract_rejects_incomplete_output_and_any_refusal(self):
        marker = "PUBLIC_PREVIEW_CONTRACT_QUERY_COMPLETE\n"
        valid = "2026-01-01\tPUBLICO_GALERIA_DTO_SELECIONADO\tTOTAL\tUNIVERSO\t2\t2\t3\t1\n"
        def rows(gallery=0, card=0):
            result = ""
            for scope, count in (("PUBLICO_GALERIA_DTO_SELECIONADO", gallery), ("PUBLICO_CARD_DTO_SELECIONADO", card)):
                for section, category, value in (("TOTAL", "UNIVERSO", count), ("CONTRATO", "ACEITA_COMPLETO", count),
                                                  ("CONTRATO", "NAO_ACEITA_OU_NAO_COMPROVADO", 0)):
                    result += f"2026-01-01\t{scope}\t{section}\t{category}\t{value}\t{value}\t{value}\t{value}\n"
            return result
        self.assertEqual(RUNTIME.check_aggregate(rows(gallery=3) + marker)["PUBLICO_GALERIA_DTO_SELECIONADO"], 3)
        self.assertEqual(sum(RUNTIME.check_aggregate(rows() + marker).values()), 0)
        refused = "2026-01-01\tPUBLICO_CARD_DTO_SELECIONADO\tCONTRATO\tNAO_ACEITA_OU_NAO_COMPROVADO\t1\t1\t1\t1\n"
        for output in ("", marker, valid, "malformed\n" + marker, valid + refused + marker,
                       rows(gallery=1).replace("\tACEITA_COMPLETO\t1\t1\t1\t1", "\tACEITA_COMPLETO\t0\t0\t0\t0") + marker):
            with self.assertRaises(RuntimeError):
                RUNTIME.check_aggregate(output)

    def test_current_graceful_marker_not_historical_or_forced(self):
        with tempfile.TemporaryDirectory() as folder:
            before = source()
            receipt = {"id": before["Id"], "image": before["Image"], "started_at": before["State"]["StartedAt"], "restart_count": 0}
            (Path(folder) / "preview-source.json").write_text(json.dumps(receipt))
            stopped = copy.deepcopy(before)
            stopped["State"]["Running"] = False
            since = "2026-01-01T00:00:59Z"
            for output in ("", CURRENT + "Graceful shutdown aborted with active requests\n"):
                result = subprocess.CompletedProcess([], 0, output, "")
                with patch.object(RUNTIME, "inspect", return_value=stopped), patch.object(RUNTIME.subprocess, "run", return_value=result) as calls:
                    with self.assertRaisesRegex(RuntimeError, "graceful"):
                        RUNTIME.stopped(folder, since)
                    self.assertEqual(calls.call_args.args[0], ["docker", "logs", "--timestamps", "--since", since,
                                                             "--until", stopped["State"]["FinishedAt"], before["Id"]])
            stopped["State"]["ExitCode"] = 137
            with patch.object(RUNTIME, "inspect", return_value=stopped), patch.object(RUNTIME, "call") as calls:
                with self.assertRaisesRegex(RuntimeError, "not_drained"):
                    RUNTIME.stopped(folder, since)
                calls.assert_not_called()

    def test_real_subprocess_stdout_success_stderr_timeout_is_rejected(self):
        warning = CURRENT + "Timed out while waiting for executor 'applicationTaskExecutor' to terminate\n"
        child = "import sys; sys.stdout.write(" + repr(SUCCESS) + "); sys.stderr.write(" + repr(warning) + ")"
        exercise_stopped(self, None, None, child_code=child)

    def test_real_subprocess_valid_completion_and_both_stream_hashes(self):
        diagnostic = CURRENT + "Accepted tasks completed\n"
        child = "import sys; sys.stdout.write(" + repr(SUCCESS) + "); sys.stderr.write(" + repr(diagnostic) + ")"
        exercise_stopped(self, None, None, accepted=True, child_code=child)
        exercise_stopped(self, "", SUCCESS, accepted=True)
        exercise_stopped(self, SUCCESS, SUCCESS, accepted=True)

    def test_timeout_and_abort_in_any_stream_reject_without_receipt(self):
        for failure in ("Timed out while waiting for executor 'applicationTaskExecutor' to terminate",
                        "Graceful shutdown aborted with active requests", "GRACEFUL SHUTDOWN ABORTED",
                        "shutdown phase timed out"):
            warning = CURRENT + failure + "\n"
            for out, err in ((SUCCESS + warning, ""), ("", SUCCESS + warning),
                             (SUCCESS, warning), (warning, SUCCESS), (SUCCESS + warning, warning)):
                with self.subTest(failure=failure, stdout=bool(out), stderr=bool(err)):
                    exercise_stopped(self, out, err)

    def test_history_future_and_nanosecond_edges_are_checked_not_trusted(self):
        for invalid in ("2026-01-01T00:00:58Z", "2026-01-01T00:00:59.000000000Z",
                        "2026-01-01T00:01:00.000000010Z", "2026-01-01T00:01:01Z"):
            with self.subTest(timestamp=invalid):
                history = invalid + " Graceful shutdown complete\n"
                exercise_stopped(self, history, "")
                exercise_stopped(self, SUCCESS, history)
        for valid in (SINCE, UNTIL, "2025-12-31T21:00:59.123456789-03:00"):
            exercise_stopped(self, valid + " Graceful shutdown complete\n", "", accepted=True)

    def test_incomplete_stream_or_untimestamped_diagnostic_rejects(self):
        for out, err in ((SUCCESS.rstrip("\n"), ""), (SUCCESS, CURRENT + "unfinished"),
                         (SUCCESS, None), (None, ""), (SUCCESS.encode(), ""),
                         (SUCCESS, "private-fixture-value: Docker daemon warning\n"),
                         ("Graceful shutdown complete\n", ""), (SUCCESS, "\n")):
            with self.subTest(stdout_type=type(out).__name__, stderr_type=type(err).__name__):
                exercise_stopped(self, out, err)

    def test_failed_return_and_collection_failures_never_create_receipt(self):
        for status in (1, 42, -15):
            exercise_stopped(self, SUCCESS, "", rc=status)
        for failure in (subprocess.TimeoutExpired("private-fixture-value", 15, output=SUCCESS),
                        OSError("private-fixture-value"), UnicodeError("private-fixture-value")):
            exercise_stopped(self, SUCCESS, "", capture_failure=failure)

    def test_receipt_window_must_belong_to_current_source(self):
        for started, finished in (("2026-01-01T00:01:00Z", UNTIL),
                                  ("2026-01-01T00:00:00Z", "2026-01-01T00:00:58Z")):
            with tempfile.TemporaryDirectory() as folder:
                current = source()
                current["State"].update(Running=False, StartedAt=started, FinishedAt=finished)
                before = {"id": current["Id"], "image": current["Image"],
                          "started_at": started, "restart_count": 0, "allowed_runtime": {}}
                (Path(folder) / "preview-source.json").write_text(json.dumps(before))
                with patch.object(RUNTIME, "inspect", return_value=current), \
                        patch.object(RUNTIME.subprocess, "run") as calls:
                    with self.assertRaisesRegex(RuntimeError, "drain_log_window"):
                        RUNTIME.stopped(folder, SINCE)
                    calls.assert_not_called()
                self.assertFalse((Path(folder) / "preview-drained.json").exists())

    def test_real_subprocess_timeout_after_partial_success_rejects(self):
        child = "import sys,time; sys.stdout.write(" + repr(SUCCESS) + "); sys.stdout.flush(); time.sleep(2)"
        exercise_stopped(self, None, None, child_code=child, child_timeout=0.1)

    def test_general_call_retains_pure_json_and_sql_stdout(self):
        for payload in ('{"result":0}\n', "0\n"):
            child = "import sys; sys.stdout.write(" + repr(payload) + "); sys.stderr.write('synthetic diagnostic\\n')"
            result = RUNTIME.call([sys.executable, "-c", child])
            self.assertEqual(result, payload)
            self.assertNotIn("diagnostic", result)

    def test_workflow_order_and_no_nested_docker_wrapper(self):
        workflow = (ROOT / ".github/workflows/deploy-production.yml").read_text()
        positions = [workflow.index(value) for value in ("op_begin", "op_phase CONFIGURING",
                     "op_phase BUILDING", "op_expect_candidate",
                     "op_preview_validate_before_activation", "op_phase ACTIVATING")]
        self.assertEqual(positions, sorted(positions))
        for obsolete in ("op_preview_preflight", "op_preview_drain_and_reconcile"):
            self.assertNotIn(obsolete, workflow)
        coordinator = (ROOT / "scripts/deploy/coordenar-transicao-previews-production.sh").read_text()
        for forbidden in ("OP_AMBIGUOUS=0", "--kill-after", "docker kill", "docker stop", "APPLY:delta"):
            self.assertNotIn(forbidden, coordinator)
        self.assertIn("preview_backfill_main VALIDATE final", coordinator)
        self.assertIn('op_run readonly python3', coordinator)
        self.assertIn(' public ', coordinator)
        self.assertIn('cleanup-proof', coordinator)
        self.assertIn('runner=(op_run mutating)', (ROOT / "scripts/deploy/executar-backfill-previews-production.sh").read_text())

    def test_public_gate_images_are_prepared_in_each_job(self):
        ci = (ROOT / ".github/workflows/ci.yml").read_text().split("  verify-operation:", 1)[1]
        deploy = (ROOT / ".github/workflows/deploy-production.yml").read_text()
        for workflow in (ci, deploy):
            preparation = workflow.split("for test_image in ", 1)[1].split("done", 1)[0]
            for image in ("postgres:17.10-alpine", "flyway/flyway:12.10.0"):
                self.assertIn(image, preparation.split("; do", 1)[0].split())
            self.assertIn('timeout --signal=TERM --kill-after=15s 180s docker pull "$test_image"', preparation)
            self.assertLess(workflow.index("for test_image in "), workflow.index("testar-gate-previews-publicos-production"))

    def test_core_fixture_executes_integral_workflow_with_declared_boundaries(self):
        fixture = (ROOT / "scripts/deploy/testar-operacao-containers-production.sh").read_text()
        self.assertIn('done < "$workflow" > "${case_dir}/activation.sh"', fixture)
        self.assertIn('bash "${case_dir}/activation.sh" "$candidate_sha"', fixture)
        for obsolete in ("CORE_SCOPE_PROJECTION", "activation.full.sh", "PREFLIGHT_EVENT_DELTA"):
            self.assertNotIn(obsolete, fixture)
        self.assertIn('workflow=integral backfill=synthetic_validate sql=synthetic_aggregate', fixture)
        self.assertIn('no_real_spring_or_storage_claim=true', fixture)
        self.assertIn("grep -Fxq 'PREVIEW_TRANSITION_READY=PASS readonly=true'", fixture)
        self.assertIn('label=topsv3.preview.operation=${operation_id}', fixture)
        self.assertNotIn("op_preview_validate_before_activation()", fixture)

    def test_core_fixture_keeps_source_observer_and_real_recovery_deadlines(self):
        fixture = (ROOT / "scripts/deploy/testar-operacao-containers-production.sh").read_text()
        for assertion in ("source_entrypoint_unproven", "PREVIEW_NODE_SOURCE_REJECTION=PASS",
                          "first_home-restore<32000", "last_end-restore<183000",
                          "elapsed_recovery_ms >= 300000", "source_observer_checked=1",
                          'case_timeout=220', 'case_timeout=390'):
            self.assertIn(assertion, fixture)
        self.assertNotIn("PREVIEW_FULL_WORKFLOW_NEGATIVE", fixture)
        historical = (ROOT / "scripts/deploy/testar-origem451-spring-production.sh").read_text()
        self.assertIn('python3 "$observer" preflight', historical)
        self.assertIn('source_graceful_configuration_unproven', historical)

    def test_core_fixture_boundary_is_validate_only_and_uses_real_evidence_predicates(self):
        fixture = (ROOT / "scripts/deploy/testar-operacao-containers-production.sh").read_text()
        boundary = fixture.split("<<'SYNTHETIC_VALIDATE_BOUNDARY'\n", 1)[1].split("\nSYNTHETIC_VALIDATE_BOUNDARY\n", 1)[0]
        for required in ('"$1" == VALIDATE', '"$2" == final', '|| return 91',
                         'STARTED PLAN_READY VALIDATION_PASSED SUCCEEDED',
                         '"mode":"VALIDATE","commit":"NOT_STARTED"',
                         '--pull never --network none --restart no',
                         'job_user="$(id -u):$(id -g)"', '--user "$job_user"',
                         '--label "topsv3.preview.operation=$OP_ID"',
                         '--entrypoint node "$TEST_CANDIDATE_IMAGE"',
                         '--app.restricted-media-preview-reconciliation.mode=VALIDATE',
                         '--app.restricted-media-preview-reconciliation.apply-confirmed=false',
                         'outcome "$report"', 'terminal "$report"'):
            self.assertIn(required, boundary)
        self.assertNotIn('apply-confirmed=true', boundary)
        self.assertIn('public_media_prefix=synthetic-public/', fixture)
        self.assertIn('PUBLIC_PREVIEW_CONTRACT_QUERY_COMPLETE', fixture)
        self.assertIn('preview_public_sql_synthetic', fixture)
        self.assertIn('--env POSTGRES_USER=postgres --env POSTGRES_DB=postgres', fixture)

    def test_named_service_substitute_rejected(self):
        other = source()
        other["Name"] = "/topsv3-production-frontend"
        other["Id"] = "e" * 64
        expected = {"topsv3-production-frontend": {"image": "sha256:" + "f" * 64, "reference": "expected:source"}}
        with patch.object(RUNTIME, "call", return_value=other["Id"]), patch.object(RUNTIME, "inspect", return_value=other):
            with self.assertRaisesRegex(RuntimeError, "named_service_identity_changed"):
                RUNTIME.no_other_producers(source(), allowed_runtime=expected)

    def test_async_executor_configuration_is_distinct_from_scheduler(self):
        current = source()
        current["Config"]["Env"] = [line for line in current["Config"]["Env"] if not line.startswith("SPRING_TASK_EXECUTION_")]
        with self.assertRaisesRegex(RuntimeError, "graceful"):
            RUNTIME.validate_source(current)

    def test_candidate_image_and_private_before_identity_are_required(self):
        with tempfile.TemporaryDirectory() as folder:
            operation = Path(folder)
            report = operation / "previews" / "apply"
            report.mkdir(parents=True)
            candidate = source()
            candidate["State"]["Running"] = False
            candidate["Config"]["Labels"]["topsv3.preview.operation"] = "synthetic-owner"
            (operation / "candidate.images.tsv").write_text("backend\t" + candidate["Image"] + "\t" + candidate["Config"]["Image"] + "\n")
            (report / "image-pin.json").write_text(json.dumps({"image": candidate["Image"], "user": RUNTIME.executor_user()}))
            candidate["Config"]["Image"] = candidate["Image"]
            name = "delta-apply.tsv.state.jsonl"
            before = report / "delta-apply.tsv.before.json"
            private_capture(before, '{"mode":"APPLY","fixture":true}')
            before_hash = RUNTIME.hashlib.sha256(before.read_bytes()).hexdigest()
            events = journal()
            for event in events[2:]:
                event["before_sha256"] = before_hash
            (report / name).write_text("\n".join(json.dumps(event) for event in events) + "\n")
            with patch.object(RUNTIME, "inspect", return_value=candidate):
                RUNTIME.terminal(str(report), "fixture", "synthetic-owner", "APPLY", name)
            self.assertTrue((report / "container-terminal.json").exists())
            changed = copy.deepcopy(candidate)
            changed["Image"] = "sha256:" + "e" * 64
            with patch.object(RUNTIME, "inspect", return_value=changed), self.assertRaisesRegex(RuntimeError, "candidate_image"):
                RUNTIME.terminal(str(report), "fixture", "synthetic-owner", "APPLY", name)
            before.write_text('{"changed":true}')
            with patch.object(RUNTIME, "inspect", return_value=candidate), self.assertRaisesRegex(RuntimeError, "before_snapshot_identity"):
                RUNTIME.terminal(str(report), "fixture", "synthetic-owner", "APPLY", name)

    def test_wrong_journal_order_rejected(self):
        events = journal()
        events[3], events[4] = events[4], events[3]
        with self.assertRaisesRegex(RuntimeError, "sequence"):
            RUNTIME.check_journal(events, "APPLY")

    def test_before_apply_pin_forbids_mutable_tag_build_and_pull(self):
        physical = "sha256:" + "a" * 64
        user = RUNTIME.executor_user()
        RUNTIME.validate_pin({"services": {"backend": {"image": physical, "pull_policy": "never", "restart": "no", "user": user}}}, physical, user)
        for service in ({"image": "candidate:tag", "pull_policy": "never"},
                        {"image": physical, "pull_policy": "never", "build": {"context": "."}},
                        {"image": physical, "pull_policy": "missing", "restart": "no"},
                        {"image": physical, "pull_policy": "never", "restart": "unless-stopped"},
                        {"image": physical, "pull_policy": "never"}):
            with self.assertRaisesRegex(RuntimeError, "pin_unproven"):
                RUNTIME.validate_pin({"services": {"backend": service}}, physical, user)

    def test_executor_identity_uses_effective_ids_not_a_fixed_account(self):
        for uid, gid in ((17321, 28432), (34543, 45654)):
            with self.subTest(uid=uid, gid=gid), patch.object(RUNTIME.os, "geteuid", return_value=uid), \
                    patch.object(RUNTIME.os, "getegid", return_value=gid):
                self.assertEqual(RUNTIME.executor_user(), f"{uid}:{gid}")

    def test_pin_rejects_missing_root_or_different_executor_before_any_job(self):
        sha, physical, user = "c" * 40, "sha256:" + "b" * 64, "17321:28432"
        for resolved in (None, "", "0:0", "17321:28433", "17322:28432"):
            with self.subTest(resolved=resolved), tempfile.TemporaryDirectory() as folder:
                config = {"services": {"backend": {"image": physical, "pull_policy": "never",
                                                     "restart": "no", "user": resolved}}}
                with patch.object(RUNTIME.os, "geteuid", return_value=17321), \
                        patch.object(RUNTIME.os, "getegid", return_value=28432), \
                        patch.object(RUNTIME, "call", side_effect=[physical, physical, json.dumps(config)]) as calls:
                    with self.assertRaisesRegex(RuntimeError, "executor_identity_mismatch"):
                        RUNTIME.pin(folder, sha, "source.yml", "private.env", "topsv3-production",
                                    "topsv3-production", "topsv3-production-net", physical)
                self.assertFalse((Path(folder) / "image-pin.json").exists())
                self.assertIn('user: "' + user + '"', (Path(folder) / "pinned-backfill.compose.yml").read_text())
                self.assertEqual(len(calls.call_args_list), 3)
                self.assertTrue(all("run" not in call.args[0] and "create" not in call.args[0]
                                    for call in calls.call_args_list))

    def test_pin_is_created_and_verified_before_any_job(self):
        with tempfile.TemporaryDirectory() as folder:
            operation = Path(folder)
            report = operation / "previews" / "apply"
            report.mkdir(parents=True)
            sha = "c" * 40
            physical = "sha256:" + "b" * 64
            (operation / "candidate.images.tsv").write_text("backend\t" + physical + "\ttopsv3-production-backend:" + sha + "\n")
            user = RUNTIME.executor_user()
            config = {"services": {"backend": {"image": physical, "pull_policy": "never", "restart": "no", "user": user}}}
            with patch.object(RUNTIME, "call", side_effect=[physical + "\n", physical + "\n", json.dumps(config)]) as calls:
                RUNTIME.pin(str(report), sha, "source.yml", "private.env", "topsv3-production", "topsv3-production", "topsv3-production-net")
            self.assertEqual(calls.call_args_list[0].args[0][:3], ["docker", "image", "inspect"])
            self.assertEqual(calls.call_args_list[1].args[0][3], "topsv3-production-backend:" + sha)
            self.assertEqual(calls.call_args_list[2].args[0][-3:], ["config", "--format", "json"])
            self.assertIn("build: !reset null", (report / "pinned-backfill.compose.yml").read_text())
            self.assertIn('user: "' + user + '"', (report / "pinned-backfill.compose.yml").read_text())
            self.assertEqual(json.loads((report / "image-pin.json").read_text())["image"], physical)
            self.assertEqual(json.loads((report / "image-pin.json").read_text())["user"], user)


    def test_independent_pin_requires_explicit_physical_and_matching_candidate(self):
        sha, physical = "c" * 40, "sha256:" + "b" * 64
        config = {"services": {"backend": {"image": physical, "pull_policy": "never", "restart": "no", "user": RUNTIME.executor_user()}}}
        for observed, evaluated, accepted in ((physical, physical, True),
                ("sha256:" + "e" * 64, physical, False),
                (physical, "sha256:" + "e" * 64, False)):
            with self.subTest(observed=observed, evaluated=evaluated), tempfile.TemporaryDirectory() as folder:
                with patch.object(RUNTIME, "call", side_effect=[observed, evaluated, json.dumps(config)]) as calls:
                    invoke = lambda: RUNTIME.pin(folder, sha, "source.yml", "private.env",
                            "topsv3-production", "topsv3-production", "topsv3-production-net", physical)
                    if accepted:
                        invoke()
                        proof = json.loads((Path(folder) / "image-pin.json").read_text())
                        self.assertEqual((proof["image"], proof["sha"], proof["origin"]), (physical, sha, "explicit"))
                    else:
                        with self.assertRaises(RuntimeError):
                            invoke()
                        self.assertFalse((Path(folder) / "image-pin.json").exists())
                        self.assertFalse((Path(folder) / "pinned-backfill.compose.yml").exists())
                    self.assertTrue(all("pull" not in call.args[0] and "build" not in call.args[0]
                                        for call in calls.call_args_list))
        for physical_input in ("candidate:mutable", "sha256:bad"):
            with tempfile.TemporaryDirectory() as folder, patch.object(RUNTIME, "call") as calls:
                with self.assertRaisesRegex(RuntimeError, "candidate_image_id_invalid"):
                    RUNTIME.pin(folder, sha, "source.yml", "private.env", "topsv3-production",
                                "topsv3-production", "topsv3-production-net", physical_input)
                calls.assert_not_called()

    def exercise_outcome(self, events, expected, *, mode="APPLY", exit_code=1,
                         mutate_runtime=None, suffix="\n", mutate_before=False, observation_failure=False,
                         pin_user=None, capture_owner=None, capture_mode=0o600, expected_reason=None):
        with tempfile.TemporaryDirectory() as folder:
            report = Path(folder)
            current = source()
            current["Config"]["Labels"]["topsv3.preview.operation"] = "synthetic-owner"
            current["Config"]["Image"] = current["Image"]
            current["State"].update(Running=False, ExitCode=exit_code)
            (report / "image-pin.json").write_text(json.dumps({"image": current["Image"],
                "user": RUNTIME.executor_user() if pin_user is None else pin_user}))
            name = "initial-" + mode.lower() + ".tsv.state.jsonl"
            before = report / (name.removesuffix(".state.jsonl") + ".before.json")
            private_capture(before, '{"synthetic_before":true}', capture_mode)
            before_hash = RUNTIME.hashlib.sha256(before.read_bytes()).hexdigest()
            events = copy.deepcopy(events)
            captured = False
            for event in events:
                captured |= event["stage"] == "BEFORE_CAPTURED"
                if captured:
                    event["before_sha256"] = before_hash
            contents = "\n".join(json.dumps(event) for event in events) + suffix
            (report / name).write_text(contents, encoding="utf-8")
            if mutate_runtime:
                mutate_runtime(current)
            if mutate_before:
                before.write_text('{"changed":true}', encoding="utf-8")
            real_stat = Path.stat

            def metadata_boundary(path, *args, **kwargs):
                metadata = real_stat(path, *args, **kwargs)
                if path == before and capture_owner is not None:
                    fields = list(metadata)
                    fields[4:6] = capture_owner
                    return os.stat_result(fields)
                return metadata

            with patch.object(RUNTIME, "inspect", side_effect=RuntimeError("observation_command_failed")
                              if observation_failure else None, return_value=current), \
                    patch.object(Path, "stat", metadata_boundary):
                RUNTIME.outcome(folder, "synthetic-container", "synthetic-owner", mode, name)
                proof = json.loads((report / "outcome.json").read_text())
                self.assertEqual(proof["commit"], expected)
                self.assertFalse(proof["retry_allowed"])
                if expected_reason is not None:
                    self.assertEqual(proof["reason"], expected_reason)
                if expected in ("COMMITTED", "ROLLED_BACK"):
                    self.assertTrue(proof["terminal"])
                    self.assertTrue(proof["identity_verified"])
                    self.assertEqual(proof["before_sha256"], before_hash)
                with self.assertRaises(FileExistsError):
                    RUNTIME.outcome(folder, "synthetic-container", "synthetic-owner", mode, name)
                if exit_code or expected == "UNKNOWN":
                    with self.assertRaises((RuntimeError, json.JSONDecodeError)):
                        RUNTIME.terminal(folder, "synthetic-container", "synthetic-owner", mode, name)
                    self.assertFalse((report / "container-terminal.json").exists())
                else:
                    RUNTIME.terminal(folder, "synthetic-container", "synthetic-owner", mode, name)
                    receipt = json.loads((report / "container-terminal.json").read_text())
                    self.assertEqual(receipt["commit"], expected)

    def test_outcome_preserves_committed_after_failure_and_never_grants_retry(self):
        events = journal()
        self.exercise_outcome(events, "COMMITTED", exit_code=0)
        for length in (4, 5, 6, 7):
            self.exercise_outcome(events[:length] + [{"mode": "APPLY", "stage": "FAILED", "commit": "COMMITTED"}],
                                  "COMMITTED", exit_code=1)

    def test_outcome_distinguishes_rollback_unknown_and_no_transaction(self):
        self.exercise_outcome(journal()[:4] + [{"mode": "APPLY", "stage": "FAILED", "commit": "ROLLED_BACK"}],
                              "ROLLED_BACK")
        self.exercise_outcome(journal()[:4], "UNKNOWN")
        self.exercise_outcome(journal()[:4] + [{"mode": "APPLY", "stage": "FAILED", "commit": "UNKNOWN"}], "UNKNOWN")
        self.exercise_outcome(journal()[:2] + [{"mode": "APPLY", "stage": "FAILED", "commit": "NOT_STARTED"}], "NOT_STARTED")
        self.exercise_outcome(journal("PLAN"), "NOT_STARTED", mode="PLAN", exit_code=0)
        self.exercise_outcome(journal("VALIDATE"), "NOT_STARTED", mode="VALIDATE", exit_code=0)

    def test_outcome_rejects_identity_journal_and_capture_ambiguity(self):
        for mutation in (lambda value: value["Config"]["Labels"].update({"topsv3.preview.operation": "other-owner"}),
                         lambda value: value.update(Image="sha256:" + "e" * 64),
                         lambda value: value["HostConfig"]["RestartPolicy"].update(Name="unless-stopped"),
                         lambda value: value["State"].update(Running=True)):
            self.exercise_outcome(journal(), "UNKNOWN", mutate_runtime=mutation)
        self.exercise_outcome(journal(), "UNKNOWN", observation_failure=True)
        self.exercise_outcome(journal(), "UNKNOWN", suffix="")
        self.exercise_outcome(journal(), "UNKNOWN", suffix="\n{\"unfinished\":")
        self.exercise_outcome(journal(), "UNKNOWN", mutate_before=True)
        self.exercise_outcome(journal() + [{"mode": "APPLY", "stage": "FAILED", "commit": "ROLLED_BACK"}], "UNKNOWN")
        self.exercise_outcome(journal()[:2] + [{"mode": "APPLY", "stage": "FAILED", "commit": "COMMITTED"}], "UNKNOWN")

    def test_outcome_rejects_executor_pin_and_runtime_user_without_retry(self):
        user = RUNTIME.executor_user()
        divergent = f"{os.geteuid() + 1}:{os.getegid() + 1}"
        for observed in ("", "0:0" if user != "0:0" else "1:1", divergent):
            self.exercise_outcome(journal(), "UNKNOWN", exit_code=0,
                                  mutate_runtime=lambda value, observed=observed: value["Config"].update(User=observed),
                                  expected_reason="backfill_executor_identity_mismatch")
            self.exercise_outcome(journal(), "UNKNOWN", exit_code=0, pin_user=observed,
                                  expected_reason="backfill_executor_identity_mismatch")

    def test_private_capture_owner_or_broad_mode_is_unknown_not_rollback(self):
        # Metadata boundary for this unit proof; the separate JVM fixture must
        # exercise the real root/non-root permission barrier with the runner.
        reason = "private_before_snapshot_owner_or_mode_mismatch"
        for owner in ((os.geteuid() + 1, os.getegid()), (os.geteuid(), os.getegid() + 1)):
            self.exercise_outcome(journal(), "UNKNOWN", exit_code=0, capture_owner=owner, expected_reason=reason)
        for mode in (0o640, 0o644, 0o660, 0o1600):
            self.exercise_outcome(journal(), "UNKNOWN", exit_code=0, capture_mode=mode, expected_reason=reason)
        with patch.object(RUNTIME.os, "geteuid", return_value=17321), \
                patch.object(RUNTIME.os, "getegid", return_value=28432):
            self.exercise_outcome(journal(), "UNKNOWN", exit_code=0, capture_owner=(0, 0), expected_reason=reason)


    def readonly_job_fixture(self, folder, exit_code=0):
        report = Path(folder) / "validate"
        report.mkdir()
        current = source()
        owner = "11111111-2222-3333-4444-555555555555"
        current["Name"] = "/synthetic-validate"
        current["Config"]["Image"] = current["Image"]
        current["Config"]["Labels"]["topsv3.preview.operation"] = owner
        current["Config"]["Cmd"] = ["--app.restricted-media-preview-reconciliation.mode=VALIDATE",
                                    "--app.restricted-media-preview-reconciliation.apply-confirmed=false"]
        current["Config"]["Env"] += ["R2_PUBLIC_MEDIA_PREFIX=synthetic-public",
                                      "R2_PUBLIC_BASE_URL=https://synthetic.invalid"]
        current["State"].update(Running=False, ExitCode=exit_code)
        (report / "image-pin.json").write_text(json.dumps({"image": current["Image"], "user": RUNTIME.executor_user()}))
        return report, current, owner

    def test_cleanup_proves_owned_terminal_validate_even_when_validation_failed(self):
        for exit_code in (0, 1, 137):
            with self.subTest(exit_code=exit_code), tempfile.TemporaryDirectory() as folder:
                report, current, owner = self.readonly_job_fixture(folder, exit_code)
                journal_path = report / "final-validate.tsv.state.jsonl"
                journal_path.write_text('{"stage":"FAILED","synthetic":true}\n')
                original = journal_path.read_bytes()
                with patch.object(RUNTIME, "inspect", return_value=current), patch.object(RUNTIME, "call") as calls:
                    RUNTIME.cleanup_proof(str(report), "synthetic-validate", owner)
                    proof = json.loads((report / "cleanup-proof.json").read_text())
                    self.assertEqual((proof["decision"], proof["id"], proof["exit_code"]),
                                     ("REMOVE", current["Id"], exit_code))
                    with self.assertRaises(FileExistsError):
                        RUNTIME.cleanup_proof(str(report), "synthetic-validate", owner)
                    calls.assert_not_called()
                self.assertEqual(journal_path.read_bytes(), original)

    def test_cleanup_inspect_error_requires_independent_exact_successful_absence(self):
        for listed, accepted in (("", True), ("a" * 64 + "\n", False),
                                 (RuntimeError("observation_command_failed"), False)):
            with self.subTest(listed=str(listed)), tempfile.TemporaryDirectory() as folder:
                report, _, owner = self.readonly_job_fixture(folder)
                with patch.object(RUNTIME, "inspect", side_effect=RuntimeError("observation_command_failed")), \
                        patch.object(RUNTIME, "call", side_effect=listed if isinstance(listed, Exception) else None,
                                     return_value=listed) as calls:
                    if accepted:
                        RUNTIME.cleanup_proof(str(report), "synthetic-validate", owner)
                        proof = json.loads((report / "cleanup-proof.json").read_text())
                        self.assertEqual(proof["decision"], "ABSENT")
                    else:
                        with self.assertRaises(RuntimeError):
                            RUNTIME.cleanup_proof(str(report), "synthetic-validate", owner)
                        self.assertFalse((report / "cleanup-proof.json").exists())
                    calls.assert_called_once_with(["docker", "container", "ls", "--all", "--quiet", "--no-trunc",
                                                   "--filter", "name=^/synthetic\\-validate$"])

    def test_cleanup_refuses_unowned_changed_mutating_or_live_job(self):
        changes = [lambda item: item["Config"]["Labels"].update({"topsv3.preview.operation": "other"}),
                   lambda item: item["Config"].update(User="divergent-user"),
                   lambda item: item.update(Image="sha256:" + "e" * 64),
                   lambda item: item.update(Name="/other-job"),
                   lambda item: item["HostConfig"]["RestartPolicy"].update(Name="unless-stopped"),
                   lambda item: item["Config"]["Cmd"].append("--app.restricted-media-preview-reconciliation.mode=APPLY"),
                   lambda item: item["Config"].update(Cmd=["--app.restricted-media-preview-reconciliation.mode=VALIDATE",
                                                "--app.restricted-media-preview-reconciliation.apply-confirmed=true"])]
        for flag in ("Running", "Restarting", "Paused"):
            changes.append(lambda item, flag=flag: item["State"].update({flag: True}))
        for change in changes:
            with self.subTest(change=change), tempfile.TemporaryDirectory() as folder:
                report, current, owner = self.readonly_job_fixture(folder)
                change(current)
                with patch.object(RUNTIME, "inspect", return_value=current), patch.object(RUNTIME, "call") as calls:
                    with self.assertRaises(RuntimeError):
                        RUNTIME.cleanup_proof(str(report), "synthetic-validate", owner)
                    calls.assert_not_called()
                self.assertFalse((report / "cleanup-proof.json").exists())

    def test_public_gate_requires_fresh_validate_outcome_and_unchanged_journal(self):
        for changed in ("none", "unknown_outcome", "failed_outcome", "restart", "owner", "journal", "no_receipt"):
            with self.subTest(changed=changed), tempfile.TemporaryDirectory() as folder:
                report, current, owner = self.readonly_job_fixture(folder)
                name = "final-validate.tsv.state.jsonl"
                (report / name).write_text("\n".join(json.dumps(event) for event in journal("VALIDATE")) + "\n")
                with patch.object(RUNTIME, "inspect", return_value=current):
                    RUNTIME.outcome(str(report), "synthetic-validate", owner, "VALIDATE", name)
                    RUNTIME.terminal(str(report), "synthetic-validate", owner, "VALIDATE", name)
                if changed in ("unknown_outcome", "failed_outcome"):
                    proof_path = report / "outcome.json"
                    proof = json.loads(proof_path.read_text())
                    proof["commit" if changed == "unknown_outcome" else "exit_code"] = "UNKNOWN" if changed == "unknown_outcome" else 1
                    proof_path.write_text(json.dumps(proof))
                elif changed == "restart":
                    current["State"]["FinishedAt"] = "2026-01-01T00:02:00Z"
                elif changed == "owner":
                    current["Config"]["Labels"]["topsv3.preview.operation"] = "other"
                elif changed == "journal":
                    (report / name).write_text("{\"stage\":\"UNKNOWN\"}\n")
                elif changed == "no_receipt":
                    (report / "container-terminal.json").unlink()
                sql = Path(folder) / "readonly.sql"
                sql.write_text("BEGIN READ ONLY;\nROLLBACK;")
                output = "".join("2026-01-01\t" + scope + "\t" + section + "\t" + category + "\t0\t0\t0\t0\n"
                    for scope in ("PUBLICO_GALERIA_DTO_SELECIONADO", "PUBLICO_CARD_DTO_SELECIONADO")
                    for section, category in (("TOTAL", "UNIVERSO"), ("CONTRATO", "ACEITA_COMPLETO"),
                                              ("CONTRATO", "NAO_ACEITA_OU_NAO_COMPROVADO")))
                output += "PUBLIC_PREVIEW_CONTRACT_QUERY_COMPLETE\n"
                pg = {"Config": {"Env": ["POSTGRES_USER=synthetic", "POSTGRES_DB=synthetic"]}}
                with patch.object(RUNTIME, "inspect", side_effect=[current, pg]), \
                        patch.object(RUNTIME, "call", return_value=output) as calls:
                    if changed == "none":
                        RUNTIME.public_gate(folder, str(sql), "synthetic-validate")
                        self.assertTrue((Path(folder) / "public-preview-contract.json").exists())
                        self.assertIn("BEGIN READ ONLY;", calls.call_args.kwargs["stdin"])
                    else:
                        with self.assertRaises((RuntimeError, KeyError)):
                            RUNTIME.public_gate(folder, str(sql), "synthetic-validate")
                        calls.assert_not_called()
                        self.assertFalse((Path(folder) / "public-preview-contract.json").exists())


if __name__ == "__main__":
    unittest.main(verbosity=2)
