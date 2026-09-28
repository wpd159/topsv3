#!/usr/bin/env python3
"""Bounded diagnostics for the owned, synthetic Origin451 fixture; never a gate."""
import datetime
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import threading
import time


def utc_now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def sanitized(value):
    value = re.sub(r"-----BEGIN [^-]+-----.*?(?:-----END [^-]+-----|$)",
                   "[PEM_REDACTED]", value, flags=re.S)
    value = re.sub(r"https?://[^\s<>\"']+", "[URL_REDACTED]", value)
    value = re.sub(r"(?i)\b(?:bearer|basic)\s+[A-Za-z0-9+/=._-]+", "[AUTH_REDACTED]", value)
    assignment = r'''(["']?[\w.-]*(?:password|secret|token|credential|cookie|authorization|access[_.-]?key|signing[_.-]?(?:key|value)|signature)[\w.-]*["']?)(\s*[:=]\s*)("(?:\\.|[^"\\\r\n])*(?:"|$)|'(?:\\.|[^'\\\r\n])*(?:'|$)|[^\s,;}]+)'''
    def redact(match):
        raw = match.group(3)
        quote = raw[0] if raw[0] in "\"'" else ""
        return match.group(1) + match.group(2) + quote + "[REDACTED]" + quote
    value = re.sub(assignment, redact, value, flags=re.I | re.M)
    value = re.sub(r"\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b", "[EMAIL_REDACTED]", value, flags=re.I)
    value = re.sub(r"\b\d{3}[.]?\d{3}[.]?\d{3}[-]?\d{2}\b", "[PERSONAL_ID_REDACTED]", value)
    value = re.sub(r"(?:\+55\s*)?\(\d{2}\)\s*\d{4,5}[- ]\d{4}\b", "[PHONE_REDACTED]", value)
    return value


def append_call(evidence, record):
    try:
        with (evidence / "docker-calls.jsonl").open("a", encoding="utf-8") as output:
            output.write(json.dumps(record, sort_keys=True) + "\n")
    except OSError:
        # Observability cannot replace the exit of the original command.
        print("ORIGIN451_DIAGNOSTIC_UNAVAILABLE: call_record", file=sys.stderr)


def observe_children(pid, stopped, identities):
    """No argv/env; /proc may be unavailable, including after a fast command."""
    while not stopped.is_set():
        try:
            children = Path(f"/proc/{pid}/task/{pid}/children").read_text().split()
            for child in children:
                executable = Path(os.readlink(f"/proc/{child}/exe")).name
                identities[int(child)] = {"pid": int(child), "parent_pid": pid,
                                          "docker_cli": executable == "docker"}
        except OSError:
            pass
        stopped.wait(0.01)


def docker_call(evidence, seconds, args):
    start_ns, started_at = time.monotonic_ns(), utc_now()
    operation = args[0]
    resource = args[1] if operation in ("start", "rm") and len(args) > 1 and re.fullmatch(r"[a-f0-9]{64}", args[1]) else None
    # Identical GNU timeout contract; no retry or daemon-success inference.
    process = subprocess.Popen(["timeout", "--signal=TERM", "--kill-after=2s", f"{seconds}s", "docker", *args])
    append_call(evidence, {"phase": "begin", "operation": operation, "resource_id": resource,
                           "utc": started_at, "monotonic_ns": start_ns,
                           "observer_pid": os.getpid(), "observer_parent_pid": os.getppid(),
                           "wrapper_pid": process.pid, "timeout_seconds": seconds})
    stopped, identities = threading.Event(), {}
    thread = threading.Thread(target=observe_children, args=(process.pid, stopped, identities), daemon=True)
    thread.start()
    rc = process.wait()
    end_ns, ended_at = time.monotonic_ns(), utc_now()
    stopped.set()
    thread.join(timeout=0.05)
    rc = rc if rc >= 0 else 128 - rc
    children = list(identities.values())
    append_call(evidence, {"phase": "end", "operation": operation, "resource_id": resource,
                           "utc": ended_at, "monotonic_ns": end_ns,
                           "duration_ms": (end_ns - start_ns) / 1_000_000, "exit": rc,
                           "wrapper_pid": process.pid, "children": children,
                           "cli_identity_observed": any(item["docker_cli"] for item in children)})
    return rc


def collect(evidence, identifier, owner):
    if not re.fullmatch(r"[a-f0-9]{64}", identifier):
        raise ValueError("invalid fixture container identity")
    started_ns = time.monotonic_ns()
    deadline = time.monotonic() + 15
    probes = []

    def read(name, args):
        remaining = min(3, deadline - time.monotonic())
        if remaining <= 0:
            probes.append({"source": name, "status": "budget_exhausted"})
            return None
        try:
            result = subprocess.run(args, capture_output=True, timeout=remaining)
            status = "available" if result.returncode == 0 else "unavailable"
            if name == "daemon_journal" and result.returncode == 0 and result.stderr:
                status = "coverage_unproven"
            probes.append({"source": name, "status": status, "exit": result.returncode,
                           "stderr_present": bool(result.stderr),
                           "stdout_truncated": len(result.stdout) > 131072,
                           "stderr_truncated": len(result.stderr) > 131072})
            if name == "container_logs":
                # Docker logs demultiplexes application STDERR to CLI STDERR.
                # Keep both bounded streams (including partial logs on an error).
                return "[docker stdout]\n" + result.stdout[:131072].decode("utf-8", errors="replace") + \
                       "\n[docker stderr]\n" + result.stderr[:131072].decode("utf-8", errors="replace")
            return result.stdout[:131072].decode("utf-8", errors="replace") if result.returncode == 0 else None
        except subprocess.TimeoutExpired:
            probes.append({"source": name, "status": "timeout"})
        except OSError:
            probes.append({"source": name, "status": "unavailable"})
        return None

    def save(name, value):
        def scrub(item):
            if isinstance(item, str):
                return sanitized(item)
            if isinstance(item, dict):
                return {key: scrub(child) for key, child in item.items()}
            if isinstance(item, list):
                return [scrub(child) for child in item]
            return item
        # Sanitize string values before encoding; Error may itself contain JSON.
        clean = json.dumps(scrub(json.loads(value)), sort_keys=True) + "\n" if name.endswith(".json") else sanitized(value)
        (evidence / f"diagnostic-{identifier}-{name}").write_text(clean, encoding="utf-8")

    try:
        # Exclude Config.Env, commands, mounts and healthcheck output.
        template = '{"id":{{json .Id}},"image":{{json .Image}},"owner":{{json (index .Config.Labels "topsv3.origin451.test")}},"state":{{json .State}},"restart_count":{{json .RestartCount}}}'
        raw = read("container_state", ["docker", "inspect", "--format", template, identifier])
        value = json.loads(raw) if raw is not None else {}
        if value.get("id") != identifier or value.get("owner") != owner:
            probes.append({"source": "ownership", "status": "unproven"})
            return 1
        state = value.get("state", {})
        value["state"] = {key: state[key] for key in ("Status", "Running", "Paused", "Restarting", "OOMKilled", "Dead", "Pid", "ExitCode", "Error", "StartedAt", "FinishedAt") if key in state}
        save("state.json", json.dumps(value, sort_keys=True) + "\n")
        logs = read("container_logs", ["docker", "logs", "--timestamps", "--tail", "200", identifier])
        if logs is not None:
            save("logs.txt", logs)
        # Only a measured call supplies the event window; close it before querying.
        calls = [json.loads(line) for line in (evidence / "docker-calls.jsonl").read_text().splitlines()]
        begins = [row for row in calls if row.get("phase") == "begin" and row.get("operation") == "start" and row.get("resource_id") == identifier]
        if not begins:
            probes.append({"source": "event_window", "status": "unavailable"})
            return 0
        since, until = begins[-1]["utc"], utc_now()
        event_format = '{"time_ns":{{.TimeNano}},"action":{{json .Action}},"id":{{json .Actor.ID}},"type":{{json .Type}}}'
        events = read("container_events", ["docker", "events", "--since", since, "--until", until, "--filter", f"container={identifier}", "--format", event_format])
        if events is not None:
            save("events.jsonl", events)
        # Normal journal access only; do not export unrelated host records.
        daemon = read("daemon_journal", ["journalctl", "--no-pager", "-u", "docker", "-u", "containerd", "--since", since, "--until", until, "-n", "200", "-o", "short-iso-precise"])
        if daemon is not None:
            related = [line for line in daemon.splitlines() if identifier[:12] in line]
            save("daemon.txt", "\n".join(related) + ("\n" if related else ""))
            probes[-1]["matching_lines"] = len(related)
            # Empty/unavailable history is not evidence that the daemon was healthy.
        return 0
    except (OSError, ValueError, KeyError):
        probes.append({"source": "diagnostic_collection", "status": "unavailable"})
        return 1
    finally:
        save("collection.json", json.dumps({"probes": probes, "duration_ms": (time.monotonic_ns() - started_ns) / 1_000_000,
                                             "does_not_prove_readiness_or_mutator_termination": True}, sort_keys=True) + "\n")


if __name__ == "__main__":
    mode, folder, *arguments = sys.argv[1:]
    evidence = Path(folder)
    if mode == "call":
        sys.exit(docker_call(evidence, int(arguments[0]), arguments[1:]))
    if mode == "collect":
        sys.exit(collect(evidence, *arguments))
    raise ValueError("unsupported diagnostic mode")
