#!/usr/bin/env python3
"""Local unit proofs; no Docker, network, files outside a temporary directory or DB."""
import copy
import importlib.util
import json
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
                       "Env": [key + "=" + value for key, value in RUNTIME.REQUIRED_ENV.items()] +
                              ["SPRING_DATASOURCE_URL=jdbc:postgresql://fixture/db"],
                       "Entrypoint": ["java", "-jar", "/app/app.jar"], "Cmd": RUNTIME.EXPECTED_ARGS.copy(),
                       "Labels": {"com.docker.compose.project": "topsv3-production"}},
            "State": {"Running": True, "Paused": False, "Restarting": False, "OOMKilled": False,
                      "StartedAt": "2026-01-01T00:00:00Z", "FinishedAt": "2026-01-01T00:01:00Z", "ExitCode": 0},
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


class TransitionTests(unittest.TestCase):
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
            for output in ("", "Graceful shutdown aborted with active requests"):
                with patch.object(RUNTIME, "inspect", return_value=stopped), patch.object(RUNTIME, "call", return_value=output) as calls:
                    with self.assertRaisesRegex(RuntimeError, "graceful"):
                        RUNTIME.stopped(folder, since)
                    self.assertEqual(calls.call_args.args[0], ["docker", "logs", "--since", since, before["Id"]])
            stopped["State"]["ExitCode"] = 137
            with patch.object(RUNTIME, "inspect", return_value=stopped), patch.object(RUNTIME, "call") as calls:
                with self.assertRaisesRegex(RuntimeError, "not_drained"):
                    RUNTIME.stopped(folder, since)
                calls.assert_not_called()

    def test_workflow_order_and_no_nested_docker_wrapper(self):
        workflow = (ROOT / ".github/workflows/deploy-production.yml").read_text()
        positions = [workflow.index(value) for value in ("op_preview_preflight", "op_phase CONFIGURING",
                     "op_phase BUILDING", "op_preview_drain_and_reconcile", "op_phase ACTIVATING")]
        self.assertEqual(positions, sorted(positions))
        coordinator = (ROOT / "scripts/deploy/coordenar-transicao-previews-production.sh").read_text()
        self.assertIn("timeout --signal=TERM 150s docker stop --time=-1", coordinator)
        self.assertNotIn("OP_AMBIGUOUS=0", coordinator)
        self.assertNotIn("--kill-after", coordinator)
        self.assertNotIn("docker kill", coordinator)
        self.assertEqual(coordinator.count("APPLY:delta"), 1)
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

    def test_core_fixture_projection_preserves_every_other_byte(self):
        fixture = (ROOT / "scripts/deploy/testar-operacao-containers-production.sh").read_text()
        projection = fixture.split("<<'CORE_SCOPE_PROJECTION'\n", 1)[1].split("\nCORE_SCOPE_PROJECTION\n", 1)[0]
        hooks = [b'source "${release_dir}/scripts/deploy/coordenar-transicao-previews-production.sh"\n',
                 b'op_preview_preflight\n', b'op_preview_drain_and_reconcile\n']
        segments = [b'#!/bin/bash\nset -Eeuo pipefail\nop_begin "$root" "$sha"\n',
                    b'# unchanged comment\n', b'op_phase CONFIGURING\nprintf " keep  spacing "\n',
                    b'op_phase ACTIVATING\nop_smoke "$sha"\nop_finish\n']
        original = segments[0] + hooks[0] + segments[1] + hooks[1] + segments[2] + hooks[2] + segments[3]
        with tempfile.TemporaryDirectory() as folder:
            source_file, core_file = Path(folder) / "full.sh", Path(folder) / "core.sh"
            source_file.write_bytes(original)
            result = subprocess.run([sys.executable, "-c", projection, str(source_file), str(core_file)],
                                    capture_output=True, text=True, check=False)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(source_file.read_bytes(), original)
            self.assertEqual(core_file.read_bytes(), b"".join(segments))
            self.assertIn("preview_hooks_removed=3 other_bytes_unchanged=true", result.stdout)
            for index, changed in enumerate((original.replace(hooks[1], b""), original + hooks[1],
                            original.replace(hooks[1], b"__SWAP__\n").replace(hooks[2], hooks[1]).replace(b"__SWAP__\n", hooks[2]),
                            original + b'op_preview_new_unreviewed_hook\n')):
                with self.subTest(changed=changed):
                    source_file.write_bytes(changed)
                    rejected = Path(folder) / ("must-not-create-" + str(index) + ".sh")
                    result = subprocess.run([sys.executable, "-c", projection, str(source_file), str(rejected)],
                                            capture_output=True, text=True, check=False)
                    self.assertNotEqual(result.returncode, 0)
                    self.assertIn("CORE_SCOPE_ERROR:", result.stderr)
                    self.assertFalse(rejected.exists())

    def test_core_fixture_keeps_real_negative_transition_and_deadlines(self):
        fixture = (ROOT / "scripts/deploy/testar-operacao-containers-production.sh").read_text()
        for assertion in ("PREVIEW_TRANSITION=FAIL reason=source_name_mismatch", "result=ABORTED mutated=0",
                          "source_entrypoint_unproven", "PREVIEW_FULL_WORKFLOW_NEGATIVE=PASS",
                          "first_home-restore<32000", "last_end-restore<183000",
                          "elapsed_recovery_ms >= 300000", "preview_negative_checked=1"):
            self.assertIn(assertion, fixture)
        for name in ("op_preview_preflight", "op_preview_drain_and_reconcile"):
            self.assertNotIn(name + "()", fixture)

    def test_preflight_event_delta_accepts_only_known_readonly_traces(self):
        fixture = (ROOT / "scripts/deploy/testar-operacao-containers-production.sh").read_text()
        proof = fixture.split("<<'PREFLIGHT_EVENT_DELTA'\n", 1)[1].split("\nPREFLIGHT_EVENT_DELTA\n", 1)[0]
        with tempfile.TemporaryDirectory() as folder:
            before, after = Path(folder) / "before", Path(folder) / "after"
            original = b"previous owned setup\n"
            before.write_bytes(original)
            for delta, expected in ((b"", 0), (b"PROBE_TRACE event=start\nCONTENT_TRACE stage=baseline\n", 0),
                                    (b"CONTROLLED_BOUNDARY psql\n", 1), (b"RESTORE_UP time_ms=1\n", 1),
                                    (b"/private-backend image id\n", 1), (b"UNKNOWN\n", 1)):
                after.write_bytes(original + delta)
                result = subprocess.run([sys.executable, "-c", proof, str(before), str(after)],
                                        capture_output=True, text=True, check=False)
                self.assertEqual(result.returncode, expected, result.stderr)
            after.write_bytes(b"changed evidence\n")
            result = subprocess.run([sys.executable, "-c", proof, str(before), str(after)],
                                    capture_output=True, text=True, check=False)
            self.assertNotEqual(result.returncode, 0)

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
            candidate["Config"]["Image"] = candidate["Image"]
            name = "delta-apply.tsv.state.jsonl"
            before = report / "delta-apply.tsv.before.json"
            before.write_text('{"mode":"APPLY","fixture":true}', encoding="utf-8")
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
        RUNTIME.validate_pin({"services": {"backend": {"image": physical, "pull_policy": "never"}}}, physical)
        for service in ({"image": "candidate:tag", "pull_policy": "never"},
                        {"image": physical, "pull_policy": "never", "build": {"context": "."}},
                        {"image": physical, "pull_policy": "missing"}):
            with self.assertRaisesRegex(RuntimeError, "pin_unproven"):
                RUNTIME.validate_pin({"services": {"backend": service}}, physical)

    def test_pin_is_created_and_verified_before_any_job(self):
        with tempfile.TemporaryDirectory() as folder:
            operation = Path(folder)
            report = operation / "previews" / "apply"
            report.mkdir(parents=True)
            sha = "c" * 40
            physical = "sha256:" + "b" * 64
            (operation / "candidate.images.tsv").write_text("backend\t" + physical + "\ttopsv3-production-backend:" + sha + "\n")
            config = {"services": {"backend": {"image": physical, "pull_policy": "never"}}}
            with patch.object(RUNTIME, "call", side_effect=[physical + "\n", json.dumps(config)]) as calls:
                RUNTIME.pin(str(report), sha, "source.yml", "private.env", "topsv3-production", "topsv3-production", "topsv3-production-net")
            self.assertEqual(calls.call_args_list[0].args[0][:3], ["docker", "image", "inspect"])
            self.assertEqual(calls.call_args_list[1].args[0][-3:], ["config", "--format", "json"])
            self.assertIn("build: !reset null", (report / "pinned-backfill.compose.yml").read_text())
            self.assertEqual(json.loads((report / "image-pin.json").read_text())["image"], physical)


if __name__ == "__main__":
    unittest.main(verbosity=2)
