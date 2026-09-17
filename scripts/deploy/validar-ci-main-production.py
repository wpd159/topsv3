#!/usr/bin/env python3
"""Read-only, fail-closed reuse of the exact main CI; never runs a deploy/test suite."""
import datetime as dt
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

REPOSITORY = "wpd159/topsv3"
WORKFLOW = ".github/workflows/ci.yml"
FLAGS = (
    "LOCALIDADES_POSTGRES17_ENABLED", "FOTO_ELEGIVEL_POSTGRES17_ENABLED",
    "PREVIEW_BACKFILL_POSTGRES17_ENABLED", "PREVIEW_JOB_USER_INTEGRATION_ENABLED",
    "PUBLIC_SEARCH_POSTGRES17_ENABLED", "ANUNCIOS_RELACIONADOS_POSTGRES17_ENABLED",
    "ANUNCIANTE_CONCURRENCY_POSTGRES17_ENABLED", "WIZARD_PROGRESS_POSTGRES17_ENABLED",
)
JOBS = {
    "Backend e frontend": (
        "Checkout", "Set up Java 17", "Prepare synthetic PostgreSQL and cold JVM test images",
        "Run full backend Maven gate", "Preserve backend and cold JVM evidence",
        "Preserve real non-root preview job evidence", "Set up Node.js 22",
        "Prepare isolated browser regression tools", "Run frontend dependency, lint and build gates",
        "Preserve browser regression evidence", "Validate isolated infrastructure contract",
    ),
    "Contratos operacionais sinteticos": (
        "Checkout", "Set up Node.js 22", "Prepare local operation test images",
        "Test main CI reuse gate", "Validate composed operation contract",
        "Run synthetic operation safety gates", "Preserve operation evidence",
    ),
    "Origem 451 Spring real - recusa segura": (
        "Checkout candidate without changing its base", "Fetch exact historical source for isolated negative test",
        "Set up Java 17", "Prepare isolated historical Spring test images",
        "Prove real unprepared source is rejected without promotion", "Preserve historical source rejection evidence",
    ),
}
ARTIFACTS = {
    "backend-reconciliation-evidence": "Backend e frontend",
    "preview-job-user-integration-evidence": "Backend e frontend",
    "browser-reconciliation-evidence": "Backend e frontend",
    "operation-reconciliation-evidence": "Contratos operacionais sinteticos",
    "origin451-spring-rejection-evidence": "Origem 451 Spring real - recusa segura",
}
GATES = {
    "testar-stdin-deploy-production": "DEPLOY_STDIN_REGRESSION_TESTS=PASS",
    "testar-gate-banco-production": "DATABASE_SAFETY_GATE_TESTS=PASS",
    "testar-gate-flyway-production": "FLYWAY_DYNAMIC_GATE_TESTS=PASS",
    "testar-backfill-previews-production": "PREVIEW_BACKFILL_DEPLOY_TESTS=PASS",
    "testar-coordenador-transicao-previews-production": "PREVIEW_COORDINATOR_TESTS=PASS",
    "testar-gate-previews-publicos-production": "PUBLIC_PREVIEW_GATE_TESTS=PASS",
    "testar-operacao-production": "PRODUCTION_OPERATION_PROCESS_TESTS=PASS",
    "testar-backup-validado-production": "BACKUP_RESTORE_INTEGRATION_TEST=PASS",
    "testar-operacao-containers-production": "PRODUCTION_OPERATION_CONTAINER_TESTS=PASS",
}
ESSENTIAL_SUITES = (
    "persistence.repository.wizard.WizardProgressPostgres17IntegrationTest",
    "persistence.repository.AnuncioRepositoryRelacionadosPostgres17IntegrationTest",
    "persistence.repository.AnuncioRepositoryBuscaPublicaPostgres17IntegrationTest",
    "persistence.repository.FotoElegivelAnuncioRepositoryPostgres17IntegrationTest",
    "application.publico.anunciante.AnuncianteConcorrenciaPostgres17IntegrationTest",
    "application.publico.anunciante.MinhasMidiasEncerramentoPostgres17IntegrationTest",
    "application.publico.service.LocalidadesConsultaCapacidadePostgres17IntegrationTest",
    "application.publico.service.LocalidadesConsultaPostgres17IntegrationTest",
    "application.publico.service.RotasPublicasPreviewPostgres17IntegrationTest",
    "application.operacional.midia.backfill.PreviewBackfillOwnedDockerResourcesTest",
    "application.operacional.midia.backfill.RestrictedMediaPreviewBackfillPostgres17IntegrationTest",
    "application.operacional.midia.backfill.RestrictedMediaPreviewBackfillApplyPostgres17IntegrationTest",
    "application.anuncio.FotoElegivelAnuncioConcorrenciaPostgres17IntegrationTest",
)


class Rejected(ValueError):
    pass


def require(condition, reason):
    if not condition:
        raise Rejected(reason)


def timestamp(value):
    parsed = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
    require(parsed.utcoffset() is not None, "timestamp de evidencia sem fuso")
    return parsed


def step_source(text, name):
    matches = re.findall(r"^      - name: " + re.escape(name) + r"\r?\n.*?(?=^      - |^  \S|\Z)", text, re.M | re.S)
    require(len(matches) == 1, "configuracao CI: etapa ausente/ambigua: " + name)
    return matches[0]


def validate_configuration(text):
    sources = {}
    for key, checkout in (("verify", "Checkout"), ("verify-operation", "Checkout"), ("verify-origin451", "Checkout candidate without changing its base")):
        match = re.search(r"^  " + key + r":\r?\n.*?(?=^  \S|\Z)", text, re.M | re.S)
        require(match is not None, "job CI ausente: " + key)
        source = match[0]
        sources[key] = source
        require('runs-on: ubuntu-latest' in source, "runner CI nao equivalente")
        checkout_source = step_source(source, checkout)
        expected_checkout = f"- name: {checkout} uses: actions/checkout@v4 with: fetch-depth: 1"
        require(" ".join(checkout_source.split()) == expected_checkout, "checkout CI nao corresponde implicitamente ao SHA do evento")
    for key in ("verify", "verify-origin451"):
        java = step_source(sources[key], "Set up Java 17")
        require('distribution: temurin' in java and 'java-version: "17"' in java, "Java CI nao equivalente")
    for key in ("verify", "verify-operation"):
        require('node-version: "22.13.1"' in step_source(sources[key], "Set up Node.js 22"), "Node CI nao equivalente")
    for name, images in (
        ("Prepare synthetic PostgreSQL and cold JVM test images", "postgres:17.10-alpine postgres:17-alpine flyway/flyway:12.10.0 eclipse-temurin:17.0.13_11-jre"),
        ("Prepare local operation test images", "node:22.13.1-alpine postgres:17.10-alpine flyway/flyway:12.10.0 nginx:1.27-alpine python:3.12-slim-bookworm"),
    ):
        require("for test_image in " + images + "; do" in step_source(text, name), "imagens de teste CI nao equivalentes")
    backend = step_source(text, "Run full backend Maven gate")
    require(re.search(r"^        run: mvn --batch-mode --no-transfer-progress verify\s*$", backend, re.M), "comando Maven nao equivalente")
    for flag in FLAGS:
        require(re.search(r'^          ' + flag + r': "true"\s*$', backend, re.M), "integracao desabilitada: " + flag)
    operation = step_source(text, "Run synthetic operation safety gates")
    require("python3 ./scripts/deploy/testar-transicao-previews-production.py" in operation, "observador Python ausente")
    require("for gate in " + " ".join(GATES) + "; do" in operation, "cobertura operacional nao equivalente")
    for command in ('bash "./scripts/deploy/$gate.sh"', 'codes=("${PIPESTATUS[@]}")', 'exit "$first_failure"'):
        require(command in operation, "propagacao do resultado operacional ausente")
    require(not re.search(r"continue-on-error:|MAVEN_OPTS:|JAVA_TOOL_OPTIONS:|JDK_JAVA_OPTIONS:|maven\.test\.skip|\-DskipTests", text), "override CI nao revisado")
    require(text.count("runs-on: ubuntu-latest") == len(JOBS), "ambiente CI nao equivalente")


def complete_list(data, key):
    items = data[key]
    require(data["total_count"] == len(items), "resposta incompleta: " + key)
    return items


def successful(value, label):
    require(value.get("status") == "completed" and value.get("conclusion") == "success", label + " incompleto/reprovado/omitido")


def archive_files(raw, digest):
    require(len(raw) <= 64 * 1024 * 1024, "artefato excede limite de leitura")
    require(digest == "sha256:" + hashlib.sha256(raw).hexdigest(), "digest do artefato divergente")
    archive = zipfile.ZipFile(io.BytesIO(raw))
    entries = archive.infolist()
    require(sum(x.file_size for x in entries) <= 256 * 1024 * 1024, "artefato expandido excede limite")
    files = {}
    for entry in entries:
        path = PurePosixPath(entry.filename)
        require(not path.is_absolute() and ".." not in path.parts and "\\" not in entry.filename, "caminho de artefato invalido")
        if not entry.is_dir():
            require(entry.filename not in files, "entrada de artefato duplicada")
            files[entry.filename] = archive.read(entry)
    return files


def validate_backend(files):
    suites = {}
    for name, body in files.items():
        if PurePosixPath(name).name.startswith("TEST-") and name.endswith(".xml"):
            require(b"<!DOCTYPE" not in body and b"<!ENTITY" not in body, "XML de testes invalido")
            suite = ET.fromstring(body)
            require(suite.tag == "testsuite", "relatorio Surefire invalido")
            suite_name = suite.attrib["name"]
            require(suite_name not in suites, "suite duplicada")
            require(int(suite.attrib["failures"]) == 0 and int(suite.attrib["errors"]) == 0, "falha no relatorio Surefire")
            cases = suite.findall("testcase")
            require(len(cases) == int(suite.attrib["tests"]), "relatorio Surefire incompleto")
            require(not suite.findall(".//failure") and not suite.findall(".//error"), "falha em testcase")
            suites[suite_name] = suite
    for suffix in ESSENTIAL_SUITES:
        name = "br.com.topsdojob.v3." + suffix
        require(name in suites, "suite essencial ausente: " + suffix)
        suite = suites[name]
        require(int(suite.attrib["tests"]) > 0 and int(suite.attrib["skipped"]) == 0 and not suite.findall(".//skipped"), "suite essencial omitida: " + suffix)


def validate_operation(files):
    for gate, marker in GATES.items():
        text = files[gate + ".log"].decode("utf-8")
        require(re.search(r"^" + re.escape(marker) + r"(?:\s|$)", text, re.M), "evidencia operacional ausente: " + gate)
    python_log = files["testar-transicao-previews-production.log"].decode("utf-8")
    require(re.search(r"^Ran [1-9][0-9]* tests in ", python_log, re.M) and re.search(r"^OK\s*$", python_log, re.M), "testes Python incompletos")
    require("CONTAINER_TEST_CLEANUP exit=0 owned_resources_only=true" in files["testar-operacao-containers-production.log"].decode(), "cleanup operacional nao comprovado")
    require("original_exit=0 cleanup_exit=0 ambiguous=0" in files["testar-backfill-previews-production.log"].decode(), "cleanup backfill nao comprovado")


def validate(sha, api, ci_text):
    require(re.fullmatch(r"[a-f0-9]{40}", sha), "SHA completo obrigatorio")
    validate_configuration(ci_text)
    prefix = "repos/" + REPOSITORY
    repo = api.json(prefix)
    require(repo["full_name"] == REPOSITORY, "repositorio divergente")
    workflow = api.json(prefix + "/actions/workflows/ci.yml")
    require(workflow["path"] == WORKFLOW and workflow["state"] == "active", "workflow CI nao confiavel")
    runs = complete_list(api.json(prefix + f"/actions/workflows/{workflow['id']}/runs?event=push&branch=main&head_sha={sha}&per_page=100"), "workflow_runs")
    require(runs, "CI push/main ausente para o SHA; publicacao bloqueada")
    run_id = max(runs, key=lambda value: value["id"])["id"]
    require(type(run_id) is int and run_id > 0, "ID do run invalido")
    run_path = prefix + f"/actions/runs/{run_id}"
    run = api.json(run_path)
    require(run["id"] == run_id, "ID do run divergente")
    successful(run, "CI mais recente")
    require(run["head_sha"] == sha and run["event"] == "push" and run["head_branch"] == "main", "identidade/evento/ref do CI divergente")
    require(run["workflow_id"] == workflow["id"] and run["path"] == WORKFLOW, "workflow do run divergente")
    for field in ("repository", "head_repository"):
        require(run[field]["id"] == repo["id"] and run[field]["full_name"] == REPOSITORY, "origem do run divergente")
    attempt = run["run_attempt"]
    require(type(attempt) is int and attempt > 0, "tentativa invalida")
    jobs = complete_list(api.json(run_path + f"/attempts/{attempt}/jobs?per_page=100"), "jobs")
    require(len(jobs) == len(JOBS) and {job["name"] for job in jobs} == set(JOBS), "jobs obrigatorios ausentes/ambiguos")
    by_name = {}
    for job in jobs:
        successful(job, "job " + job["name"])
        require(job["run_id"] == run_id and job["head_sha"] == sha and job["run_attempt"] == attempt, "job de outra identidade/tentativa")
        steps = job["steps"]
        require(len({step["name"] for step in steps}) == len(steps), "etapas ambiguas")
        require(set(JOBS[job["name"]]).issubset({step["name"] for step in steps}), "etapa obrigatoria ausente")
        for step in steps:
            successful(step, "etapa " + step["name"])
        by_name[job["name"]] = job
    artifacts = complete_list(api.json(run_path + "/artifacts?per_page=100"), "artifacts")
    selected = {}
    for name, owner in ARTIFACTS.items():
        matches = [item for item in artifacts if item["name"] == name]
        require(len(matches) == 1, "artefato obrigatorio ausente/ambiguo: " + name)
        artifact = matches[0]
        identity = artifact["workflow_run"]
        require(identity["id"] == run_id and identity["head_sha"] == sha and identity["head_branch"] == "main" and identity["repository_id"] == repo["id"] and identity["head_repository_id"] == repo["id"], "identidade do artefato divergente")
        require(artifact["expired"] is False and artifact["size_in_bytes"] > 0, "artefato vazio/expirado")
        job = by_name[owner]
        require(timestamp(job["started_at"]) <= timestamp(artifact["created_at"]) <= timestamp(job["completed_at"]), "artefato fora da tentativa atual")
        selected[name] = artifact
    for name, checker in (("backend-reconciliation-evidence", validate_backend), ("operation-reconciliation-evidence", validate_operation)):
        artifact = selected[name]
        checker(archive_files(api.archive(artifact["id"]), artifact["digest"]))
    refreshed = api.json(run_path)
    require(refreshed == run, "CI mudou durante a conferência; publicacao bloqueada")
    return {"result": "MAIN_CI_REUSE=PASS", "sha": sha, "run_id": run_id, "attempt": attempt, "jobs": len(jobs), "artifacts": len(selected)}


class GitHub:
    def request(self, path):
        # gh handles authenticated redirects; credentials never enter argv or logs.
        result = subprocess.run(["gh", "api", "--hostname", "github.com", "--method", "GET", path], capture_output=True, timeout=90, check=False)
        require(result.returncode == 0, "consulta GitHub recusada/indisponivel; publicacao bloqueada")
        return result.stdout

    def json(self, path):
        return json.loads(self.request(path))

    def archive(self, artifact_id):
        require(type(artifact_id) is int and artifact_id > 0, "ID de artefato invalido")
        return self.request(f"repos/{REPOSITORY}/actions/artifacts/{artifact_id}/zip")


def main():
    try:
        require(len(sys.argv) == 2, "uso: validar-ci-main-production.py SHA")
        sha = sys.argv[1]
        require(os.environ.get("GITHUB_REPOSITORY") == REPOSITORY and os.environ.get("GITHUB_REF") == "refs/heads/main", "deploy deve usar main do repositorio autorizado")
        root = Path(__file__).resolve().parents[2]
        head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
        require(head == sha == os.environ.get("GITHUB_SHA"), "checkout nao corresponde ao SHA do dispatch")
        print(json.dumps(validate(sha, GitHub(), (root / WORKFLOW).read_text(encoding="utf-8"))))
        return 0
    except (Rejected, KeyError, ValueError, TypeError, OSError, subprocess.SubprocessError, zipfile.BadZipFile, ET.ParseError) as error:
        # Missing/malformed evidence must fail closed without exposing API bodies/tokens.
        message = str(error) if isinstance(error, Rejected) else "evidencia indisponivel/invalida (" + type(error).__name__ + ")"
        print("MAIN_CI_REUSE=BLOCKED: " + message, file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
