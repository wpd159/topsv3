#!/usr/bin/env python3
"""Read-only runtime proofs for the supervised preview transition.

No service mutations or credentials are emitted. The caller owns all mutations
through op_run; absent or ambiguous observations fail closed.
"""
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

PREFIX = "topsv3-production-"
NETWORK = "topsv3-production-net"
ALLOWED = {PREFIX + name for name in ("backend", "frontend", "gateway", "postgres")}
REQUIRED_ENV = {
    "SERVER_SHUTDOWN": "graceful",
    "SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE": "120s",
    "SPRING_TASK_SCHEDULING_SHUTDOWN_AWAIT_TERMINATION": "true",
    "SPRING_TASK_SCHEDULING_SHUTDOWN_AWAIT_TERMINATION_PERIOD": "120s",
    "SPRING_TASK_EXECUTION_SHUTDOWN_AWAIT_TERMINATION": "true",
    "SPRING_TASK_EXECUTION_SHUTDOWN_AWAIT_TERMINATION_PERIOD": "120s",
    "SPRING_PROFILES_ACTIVE": "production",
}
EXPECTED_ARGS = ["--app.fixture." + name + ".enabled=false" for name in
                 ("admin-provision", "stories", "owner-credential", "auth-smoke")]


def demand(condition, reason):
    if not condition:
        raise RuntimeError(reason)


def call(args, *, stdin=None, timeout=15):
    result = subprocess.run(args, input=stdin, capture_output=True, text=True,
                            timeout=timeout, check=False)
    demand(result.returncode == 0, "observation_command_failed")
    return result.stdout


def log_time_ns(value):
    """Parse Docker RFC3339Nano without rounding a boundary to microseconds."""
    match = re.fullmatch(r"(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?(Z|[+-]\d{2}:\d{2})", value)
    demand(match is not None, "drain_log_timestamp_invalid")
    try:
        instant = datetime.datetime.fromisoformat(match[1] + match[3].replace("Z", "+00:00"))
        epoch = datetime.datetime(1970, 1, 1, tzinfo=datetime.timezone.utc)
        delta = instant - epoch
    except ValueError:
        raise RuntimeError("drain_log_timestamp_invalid") from None
    return (delta.days * 86400 + delta.seconds) * 1000000000 + int((match[2] or "").ljust(9, "0"))


def drain_logs(source_id, since, until, *, timeout=15):
    """Complete, bounded docker-log capture; never expose private log payloads.

    Unlike JSON/SQL call(), BOTH streams are evidence. Docker timestamps must
    independently corroborate the requested window, including nanosecond edges.
    EOF after a partial record is not a complete Spring logger observation.
    """
    lower, upper = log_time_ns(since), log_time_ns(until)
    demand(lower <= upper, "drain_log_window_invalid")
    args = ["docker", "logs", "--timestamps", "--since", since, "--until", until, source_id]
    try:
        result = subprocess.run(args, capture_output=True, text=True, encoding="utf-8",
                                errors="strict", timeout=timeout, check=False)
    except (subprocess.TimeoutExpired, OSError, UnicodeError):
        raise RuntimeError("drain_logs_collection_failed") from None
    demand(result.returncode == 0, "drain_logs_command_failed")
    streams = {"stdout": result.stdout, "stderr": result.stderr}
    payloads = []
    for stream in streams.values():
        demand(isinstance(stream, str) and (not stream or stream.endswith("\n")),
               "drain_logs_capture_incomplete")
        for line in stream.splitlines():
            timestamp, separator, payload = line.partition(" ")
            demand(bool(separator), "drain_log_record_invalid")
            recorded = log_time_ns(timestamp)
            demand(lower <= recorded <= upper, "drain_log_record_outside_window")
            payloads.append(payload)
    logs = "\n".join(payloads)
    demand("Graceful shutdown complete" in logs and
           not re.search(r"Graceful shutdown aborted|Timed out while waiting for executor|shutdown.*timed out", logs, re.IGNORECASE),
           "current_graceful_completion_unproven")
    return {"logs_sha256": digest(json.dumps(streams, sort_keys=True)),
            "logs_stdout_sha256": digest(streams["stdout"]),
            "logs_stderr_sha256": digest(streams["stderr"]),
            "log_records": len(payloads)}


def inspect(target):
    values = json.loads(call(["docker", "inspect", target]))
    demand(len(values) == 1, "runtime_identity_ambiguous")
    return values[0]


def environment(runtime):
    result = {}
    for line in runtime["Config"].get("Env", []):
        key, value = line.split("=", 1)
        demand(key not in result, "duplicate_environment_key")
        result[key] = value
    return result


def digest(value):
    return hashlib.sha256(value.encode()).hexdigest()


def emit_file(directory, name, value):
    folder = Path(directory)
    demand(folder.is_dir() and not folder.is_symlink(), "private_report_directory_invalid")
    target = folder / name
    with target.open("x", encoding="utf-8") as handle:
        json.dump(value, handle, sort_keys=True)
        handle.write("\n")
        handle.flush()
        os.fsync(handle.fileno())


def validate_source(runtime):
    env = environment(runtime)
    demand(runtime["Name"] == "/" + PREFIX + "backend", "source_name_mismatch")
    demand(runtime["State"]["Running"] and not runtime["State"].get("Paused")
           and not runtime["State"].get("Restarting"), "source_not_running")
    demand(runtime["Config"]["Entrypoint"] == ["java", "-jar", "/app/app.jar"],
           "source_entrypoint_unproven")
    demand(runtime["Config"].get("Cmd") == EXPECTED_ARGS, "source_arguments_unproven")
    demand(all(env.get(key) == value for key, value in REQUIRED_ENV.items()),
           "source_graceful_configuration_unproven")
    demand(not any(env.get(key) for key in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS",
                                          "_JAVA_OPTIONS", "SPRING_APPLICATION_JSON")),
           "higher_priority_configuration_unproven")
    demand(runtime["Config"].get("StopSignal") in (None, "", "SIGTERM", "15"),
           "source_stop_signal_unproven")
    demand(runtime["Config"]["Labels"].get("com.docker.compose.project") == "topsv3-production",
           "source_project_mismatch")
    demand(NETWORK in runtime["NetworkSettings"]["Networks"], "source_network_mismatch")
    return env


def no_other_producers(source, *, stopped=False, allowed_runtime=None):
    """Inspect all running containers, not only the Compose project.

    Shared network/DB/image producers are rejected. Unattributed host Java or
    operational mutators are rejected too; no external PID is stopped here.
    """
    env = environment(source)
    ids = call(["docker", "ps", "--quiet", "--no-trunc"]).split()
    runtimes = [inspect(identifier) for identifier in ids]
    for runtime in runtimes:
        name = runtime["Name"].lstrip("/")
        if name in ALLOWED:
            demand(name != PREFIX + "backend" or
                   (not stopped and runtime["Id"] == source["Id"]), "source_reappeared")
            demand(allowed_runtime is not None and name in allowed_runtime, "named_service_identity_unproven")
            expected = allowed_runtime[name]
            if name == PREFIX + "postgres":
                demand(runtime["Id"] == expected["id"], "postgres_identity_changed")
            else:
                demand(runtime["Image"] == expected["image"] and runtime["Config"]["Image"] == expected["reference"]
                       and runtime["Config"]["Labels"].get("com.docker.compose.project") == "topsv3-production"
                       and runtime["Config"]["Labels"].get("com.docker.compose.service") == name.removeprefix(PREFIX),
                       "named_service_identity_changed")
            continue
        other = environment(runtime)
        same_database = bool(env.get("SPRING_DATASOURCE_URL")) and (
            other.get("SPRING_DATASOURCE_URL") == env["SPRING_DATASOURCE_URL"])
        shared_network = NETWORK in runtime["NetworkSettings"]["Networks"]
        producer_image = runtime["Config"].get("Image", "").startswith(PREFIX + "backend:")
        demand(not (same_database or shared_network or producer_image), "external_producer_container")
    # Docker daemon and unrelated services are not blanket-blocked; unresolved
    # Java/Compose/backfill processes are. /proc membership attributes Java to
    # the inspected containers without exposing their arguments.
    for line in call(["ps", "-eo", "pid=,args="]).splitlines():
        parts = line.strip().split(None, 1)
        if len(parts) != 2 or not parts[0].isdigit():
            continue
        pid, command = int(parts[0]), parts[1]
        if pid == os.getpid():
            continue
        executable = command.split(None, 1)[0].rsplit("/", 1)[-1]
        if executable in ("docker", "docker-compose") or re.search(
                r"(?:^|/)(?:executar-backfill|criar-backup|regularizar)[^ ]*", command):
            raise RuntimeError("external_operational_process")
        if executable == "java":
            try:
                cgroup = Path(f"/proc/{pid}/cgroup").read_text()
            except OSError as failure:
                raise RuntimeError("producer_process_unproven") from failure
            owners = [item for item in runtimes if item["Id"] in cgroup]
            demand(len(owners) == 1, "unattributed_java_process")
            demand(not stopped or owners[0]["Id"] != source["Id"], "source_java_still_alive")
    return len(runtimes)


def preflight(directory, sha):
    demand(re.fullmatch(r"[a-f0-9]{40}", sha), "source_sha_invalid")
    source = inspect(PREFIX + "backend")
    validate_source(source)
    demand(source["Config"]["Image"] == PREFIX + "backend:" + sha, "source_image_mismatch")
    operation = Path(directory).parents[1]
    allowed_runtime = {}
    for line in (operation / "images.tsv").read_text().splitlines():
        service, image, reference = line.split("\t")
        demand(service in ("backend", "frontend", "gateway") and PREFIX + service not in allowed_runtime,
               "snapshot_service_invalid")
        allowed_runtime[PREFIX + service] = {"image": image, "reference": reference}
    demand(len(allowed_runtime) == 3, "snapshot_services_incomplete")
    allowed_runtime[PREFIX + "postgres"] = {"id": (operation / "postgres.identity").read_text().split()[0]}
    count = no_other_producers(source, allowed_runtime=allowed_runtime)
    value = {"id": source["Id"], "image": source["Image"], "sha": sha,
             "started_at": source["State"]["StartedAt"], "restart_count": source["RestartCount"],
             "observed_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
             "containers_checked": count, "allowed_runtime": allowed_runtime}
    emit_file(directory, "preview-source.json", value)
    print("PREVIEW_TRANSITION_PREFLIGHT=PASS")


def stopped(directory, since):
    before = json.loads((Path(directory) / "preview-source.json").read_text())
    source = inspect(before["id"])
    state = source["State"]
    demand(source["Image"] == before["image"] and state["StartedAt"] == before["started_at"]
           and source["RestartCount"] == before["restart_count"], "source_identity_changed")
    demand(not state["Running"] and not state.get("Paused") and not state.get("Restarting")
           and not state.get("OOMKilled") and state["ExitCode"] in (0, 143), "source_not_drained")
    demand(log_time_ns(since) >= log_time_ns(state["StartedAt"]), "drain_log_window_before_source_start")
    until = state["FinishedAt"]
    logs_proof = drain_logs(before["id"], since, until)
    count = no_other_producers(source, stopped=True, allowed_runtime=before["allowed_runtime"])
    # This is a complementary DB proof, never the proof of request drainage.
    pg = environment(inspect(PREFIX + "postgres"))
    sql = ("BEGIN READ ONLY; SET LOCAL statement_timeout='5s'; SET LOCAL lock_timeout='1s'; "
           "SELECT count(*) FROM pg_stat_activity WHERE pid <> pg_backend_pid() "
           "AND datname = current_database() AND backend_type = 'client backend'; ROLLBACK;")
    output = call(["docker", "exec", "-i", PREFIX + "postgres", "psql", "-X", "-qAt",
                   "--no-password", "--set=ON_ERROR_STOP=1", "-U", pg["POSTGRES_USER"],
                   "-d", pg["POSTGRES_DB"]], stdin=sql)
    demand(output.strip() == "0", "database_clients_remain")
    emit_file(directory, "preview-drained.json", {"source_id": before["id"], "since": since, "until": until,
              "exit_code": state["ExitCode"], **logs_proof,
              "containers_checked": count, "database_clients": 0})
    print("PREVIEW_PRODUCERS_DRAINED=PASS")


def check_journal(events, mode):
    demand(mode in ("PLAN", "APPLY", "VALIDATE"), "backfill_journal_mode_invalid")
    expected_stages = ["STARTED", "PLAN_READY"]
    if mode == "APPLY":
        expected_stages += ["BEFORE_CAPTURED", "APPLY_STARTED", "APPLY_FINISHED"]
    if mode != "PLAN":
        expected_stages += ["VALIDATION_PASSED"]
    expected_stages += ["SUCCEEDED"]
    demand([event["stage"] for event in events] == expected_stages, "backfill_journal_sequence_unproven")
    demand(all(event["mode"] == mode for event in events), "backfill_journal_mode_mismatch")
    for event in events:
        expected_commit = "NOT_STARTED"
        if mode == "APPLY" and event["stage"] == "APPLY_STARTED":
            expected_commit = "UNKNOWN"
        elif mode == "APPLY" and event["stage"] in ("APPLY_FINISHED", "VALIDATION_PASSED", "SUCCEEDED"):
            expected_commit = "COMMITTED"
        demand(event["commit"] == expected_commit, "backfill_journal_commit_sequence_unproven")
    expected = "COMMITTED" if mode == "APPLY" else "NOT_STARTED"
    demand(events[-1]["stage"] == "SUCCEEDED" and events[-1]["commit"] == expected,
           "backfill_commit_completion_unproven")
    demand(any(event["stage"] == "PLAN_READY" for event in events), "backfill_plan_unproven")
    if mode != "PLAN":
        demand(any(event["stage"] == "VALIDATION_PASSED" for event in events), "backfill_validation_unproven")
    if mode == "APPLY":
        demand(any(event["stage"] == "APPLY_STARTED" and event["commit"] == "UNKNOWN" for event in events)
               and any(event["stage"] == "APPLY_FINISHED" and event["commit"] == "COMMITTED" for event in events),
               "backfill_commit_sequence_unproven")


def terminal(directory, container, operation, mode, journal_name):
    runtime = inspect(container)
    state = runtime["State"]
    demand(runtime["Config"]["Labels"].get("topsv3.preview.operation") == operation,
           "backfill_owner_mismatch")
    images = (Path(directory).parents[1] / "candidate.images.tsv").read_text().splitlines()
    backend = [line.split("\t") for line in images if line.startswith("backend\t")]
    demand(len(backend) == 1 and runtime["Image"] == backend[0][1]
           and runtime["Config"]["Image"] == backend[0][1], "backfill_candidate_image_mismatch")
    demand(not state["Running"] and not state.get("Restarting") and not state.get("Paused")
           and not state.get("OOMKilled") and state["ExitCode"] == 0, "backfill_not_terminal_success")
    demand(re.fullmatch(r"(initial|delta|final)-(plan|apply|validate)\.tsv\.state\.jsonl", journal_name),
           "backfill_journal_path_invalid")
    journal = Path(directory) / journal_name
    demand(journal.is_file() and not journal.is_symlink(), "backfill_journal_absent")
    contents = journal.read_text(encoding="utf-8")
    events = [json.loads(line) for line in contents.splitlines()]
    check_journal(events, mode)
    if mode == "APPLY":
        before_path = Path(directory) / journal_name.removesuffix(".state.jsonl")
        before_path = Path(str(before_path) + ".before.json")
        demand(before_path.is_file() and not before_path.is_symlink(), "private_before_snapshot_absent")
        before_digest = hashlib.sha256(before_path.read_bytes()).hexdigest()
        captured = [event for event in events if event["stage"] == "BEFORE_CAPTURED"]
        demand(len(captured) == 1 and captured[0].get("before_sha256") == before_digest
               and events[-1].get("before_sha256") == before_digest, "private_before_snapshot_identity_unproven")
    emit_file(directory, "container-terminal.json", {"id": runtime["Id"], "image": runtime["Image"],
              "exit_code": state["ExitCode"], "finished_at": state["FinishedAt"], "operation": operation,
              "journal_sha256": digest(contents)})
    print("PREVIEW_JOB_TERMINAL=PASS")


def validate_pin(config, image):
    service = config["services"]["backend"]
    demand(service.get("image") == image and service.get("build") is None
           and service.get("pull_policy") == "never", "backfill_image_pin_unproven")


def pin(directory, sha, compose_file, env_file, project, prefix, network):
    demand(re.fullmatch(r"[a-f0-9]{40}", sha), "candidate_sha_invalid")
    images = (Path(directory).parents[1] / "candidate.images.tsv").read_text().splitlines()
    backend = [line.split("\t") for line in images if line.startswith("backend\t")]
    demand(len(backend) == 1 and backend[0][2] == PREFIX + "backend:" + sha,
           "candidate_image_snapshot_invalid")
    physical = backend[0][1]
    demand(re.fullmatch(r"sha256:[a-f0-9]{64}", physical), "candidate_image_id_invalid")
    observed = call(["docker", "image", "inspect", physical, "--format", "{{.Id}}"])
    demand(observed.strip() == physical, "candidate_physical_image_unavailable")
    target = Path(directory) / "pinned-backfill.compose.yml"
    # No resolved environment/credentials are copied. !reset is deliberately
    # verified below: unsupported Compose must reject before running any job.
    payload = 'services:\n  backend:\n    image: "' + physical + '"\n    build: !reset null\n    pull_policy: never\n'
    descriptor = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(payload)
        handle.flush()
        os.fsync(handle.fileno())
    args = ["env", "TOPSV3_RELEASE_SHA=" + sha, "TOPSV3_APP_PREFIX=" + prefix,
            "TOPSV3_APP_NETWORK=" + network, "EFI_ENABLED=false", "EFI_RECONCILIATION_ENABLED=false",
            "EFI_WEBHOOK_REGISTRATION_ENABLED=false", "OUTBOX_EMAIL_ENABLED=false",
            "docker", "compose", "--env-file", env_file, "-f", compose_file, "-f", str(target),
            "-p", project, "config", "--format", "json"]
    config = json.loads(call(args))
    validate_pin(config, physical)
    emit_file(directory, "image-pin.json", {"image": physical, "override_sha256": digest(payload)})
    print("PREVIEW_IMAGE_PIN=PASS")


def check_aggregate(output):
    scopes = {"PUBLICO_GALERIA_DTO_SELECIONADO", "PUBLICO_CARD_DTO_SELECIONADO"}
    measured = {scope: {} for scope in scopes}
    refusals = 0
    lines = output.splitlines()
    demand(lines and lines[-1] == "PUBLIC_PREVIEW_CONTRACT_QUERY_COMPLETE",
           "public_contract_query_incomplete")
    for line in lines[:-1]:
        values = line.split("\t")
        demand(len(values) == 8, "public_contract_output_malformed")
        _, scope, section, category, *counts = values
        demand(all(re.fullmatch(r"[0-9]+", value) for value in counts), "public_contract_count_invalid")
        if scope not in scopes:
            continue
        if (section == "TOTAL" and category == "UNIVERSO") or (section == "CONTRATO" and category in
                ("ACEITA_COMPLETO", "NAO_ACEITA_OU_NAO_COMPROVADO")):
            demand(category not in measured[scope], "public_contract_duplicate_count")
            measured[scope][category] = int(counts[2])
    for counts in measured.values():
        demand(set(counts) == {"UNIVERSO", "ACEITA_COMPLETO", "NAO_ACEITA_OU_NAO_COMPROVADO"},
               "public_contract_totals_incomplete")
        demand(counts["UNIVERSO"] == counts["ACEITA_COMPLETO"] + counts["NAO_ACEITA_OU_NAO_COMPROVADO"],
               "public_contract_totals_inconsistent")
        refusals += counts["NAO_ACEITA_OU_NAO_COMPROVADO"]
    demand(refusals == 0, "public_preview_contract_refused")
    return {scope: counts["UNIVERSO"] for scope, counts in measured.items()}


def public_gate(directory, sql_path, container):
    candidate = inspect(container)
    receipt = json.loads((Path(directory) / "validate" / "container-terminal.json").read_text())
    demand(candidate["Id"] == receipt["id"] and candidate["Image"] == receipt["image"],
           "validate_container_identity_changed")
    demand(not candidate["State"]["Running"] and candidate["State"]["ExitCode"] == 0,
           "validate_container_not_terminal")
    env = environment(candidate)
    prefix, base = env.get("R2_PUBLIC_MEDIA_PREFIX", ""), env.get("R2_PUBLIC_BASE_URL", "")
    demand(prefix.strip() and base.strip(), "candidate_preview_configuration_absent")
    pg = environment(inspect(PREFIX + "postgres"))
    sql = Path(sql_path).read_text(encoding="utf-8")
    demand("BEGIN READ ONLY;" in sql and sql.rstrip().endswith("ROLLBACK;"), "sql_envelope_invalid")
    output = call(["docker", "exec", "-i", PREFIX + "postgres", "psql", "-X", "-qAt", "-F", "\t",
                   "--no-password", "--set=ON_ERROR_STOP=1", "--set=public_media_prefix=" + prefix,
                   "--set=public_base_present=true", "-U", pg["POSTGRES_USER"], "-d", pg["POSTGRES_DB"]],
                  stdin=sql)
    totals = check_aggregate(output)
    emit_file(directory, "public-preview-contract.json", {"sql_sha256": digest(sql),
              "output_sha256": digest(output), "selected_links": totals, "refusals": 0})
    print("PUBLIC_PREVIEW_CONTRACT=PASS refusals=0")


if __name__ == "__main__":
    try:
        action, *arguments = sys.argv[1:]
        {"preflight": preflight, "stopped": stopped, "terminal": terminal,
         "public": public_gate, "pin": pin}[action](*arguments)
    except Exception as error:
        # No subprocess stderr/configuration values reach runner logs.
        reason = str(error) if isinstance(error, RuntimeError) else "observation_failed"
        print("PREVIEW_TRANSITION=FAIL reason=" + reason, file=sys.stderr)
        sys.exit(1)
