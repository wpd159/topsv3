package br.com.topsdojob.v3.application.operacional.midia;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicReference;
import br.com.topsdojob.v3.application.operacional.midia.MidiaRestritaRegularizacaoService.EstadoCommit;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(
    name = "app.restricted-media-preview-reconciliation.enabled",
    havingValue = "true")
public class MidiaRestritaRegularizacaoRunner implements ApplicationRunner {

  private final MidiaRestritaRegularizacaoService service;
  private final ConfigurableApplicationContext applicationContext;
  private final String mode;
  private final boolean applyConfirmed;
  private final int batchSize;
  private final Path reportPath;
  private boolean journalInicializado;
  private String beforeSha256;

  public MidiaRestritaRegularizacaoRunner(
      MidiaRestritaRegularizacaoService service,
      ConfigurableApplicationContext applicationContext,
      @Value("${app.restricted-media-preview-reconciliation.mode:PLAN}") String mode,
      @Value("${app.restricted-media-preview-reconciliation.apply-confirmed:false}")
          boolean applyConfirmed,
      @Value("${app.restricted-media-preview-reconciliation.batch-size:200}") int batchSize,
      @Value("${app.restricted-media-preview-reconciliation.report-path:}") String reportPath) {
    this.service = service;
    this.applicationContext = applicationContext;
    this.mode = mode == null ? "PLAN" : mode.trim().toUpperCase(Locale.ROOT);
    this.applyConfirmed = applyConfirmed;
    this.batchSize = batchSize;
    this.reportPath = reportPath == null || reportPath.isBlank() ? null : Path.of(reportPath);
  }

  @Override
  public void run(ApplicationArguments args) {
    AtomicReference<EstadoCommit> commit = new AtomicReference<>(EstadoCommit.NOT_STARTED);
    Throwable falhaOriginal = null;
    try {
      validarExecucao();
      registrarEstado("STARTED", commit.get());
      MidiaRestritaRegularizacaoService.Resultado plano = service.planejar();
      escreverRelatorio(plano);
      registrarEstado("PLAN_READY", commit.get());
      MidiaRestritaRegularizacaoService.Aplicacao aplicacao =
          new MidiaRestritaRegularizacaoService.Aplicacao(0, 0, 0);
      MidiaRestritaRegularizacaoService.Validacao validacao =
          new MidiaRestritaRegularizacaoService.Validacao(0, -1, -1, -1, -1);
      MidiaRestritaRegularizacaoService.Resultado inventarioFinal = plano;

      if ("APPLY".equals(mode)) {
        capturarValoresAnteriores(plano);
        registrarEstado("BEFORE_CAPTURED", commit.get());
        commit.set(EstadoCommit.UNKNOWN);
        registrarEstado("APPLY_STARTED", commit.get());
        aplicacao = service.aplicar(plano, batchSize, commit::set);
        if (commit.get() != EstadoCommit.COMMITTED) {
          throw new IllegalStateException("Conclusao transacional do APPLY nao comprovada");
        }
        registrarEstado("APPLY_FINISHED", commit.get());
        inventarioFinal = service.planejar();
        validacao = service.validarPersistencia(inventarioFinal, batchSize);
        exigirValidacao(validacao);
        registrarEstado("VALIDATION_PASSED", commit.get());
      } else if ("VALIDATE".equals(mode)) {
        validacao = service.validarPersistencia(plano, batchSize);
        exigirValidacao(validacao);
        registrarEstado("VALIDATION_PASSED", commit.get());
      }

      imprimirResultado(inventarioFinal, aplicacao, validacao);
      if (inventarioFinal.ausentes() != 0 || inventarioFinal.inconsistentes() != 0
          || inventarioFinal.naoComprovados() != 0
          || inventarioFinal.disponiveis() != inventarioFinal.arquivos()) {
        throw new IllegalStateException("Inventario de previews nao comprovado");
      }
      registrarEstado("SUCCEEDED", commit.get());
    } catch (RuntimeException | Error exception) {
      falhaOriginal = exception;
      registrarFalhaSemMascarar(exception, commit.get());
      throw exception;
    } finally {
      try {
        applicationContext.close();
      } catch (RuntimeException | Error closeFailure) {
        if (falhaOriginal != null) {
          falhaOriginal.addSuppressed(closeFailure);
        } else {
          registrarFalhaSemMascarar(closeFailure, commit.get());
          throw closeFailure;
        }
      }
    }
  }

  private void registrarFalhaSemMascarar(Throwable original, EstadoCommit commit) {
    try {
      registrarEstado("FAILED", commit);
    } catch (RuntimeException journalFailure) {
      original.addSuppressed(journalFailure);
    }
  }

  private void registrarEstado(String stage, EstadoCommit commit) {
    // Print before journal I/O so a failed post-commit fsync cannot hide the confirmed commit.
    System.out.printf(Locale.ROOT,
        "RESTRICTED_MEDIA_PREVIEW_RECONCILIATION_PHASE mode=%s stage=%s commit=%s%s%n",
        mode, stage, commit, beforeSha256 == null ? "" : " before_sha256=" + beforeSha256);
    if (!journalInicializado && !"STARTED".equals(stage)) return;
    Path journal = Path.of(reportPath.toAbsolutePath().normalize() + ".state.jsonl");
    String line = "{\"mode\":\"" + mode + "\",\"stage\":\"" + stage
        + "\",\"commit\":\"" + commit + "\",\"time_utc\":\"" + Instant.now() + "\""
        + (beforeSha256 == null ? "" : ",\"before_sha256\":\"" + beforeSha256 + "\"") + "}\n";
    try {
      Files.createDirectories(journal.getParent());
      try (FileChannel channel = journalInicializado
          ? FileChannel.open(journal, StandardOpenOption.WRITE, StandardOpenOption.APPEND)
          : FileChannel.open(journal, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)) {
        ByteBuffer bytes = StandardCharsets.UTF_8.encode(line);
        while (bytes.hasRemaining()) channel.write(bytes);
        channel.force(true);
      }
      if (!journalInicializado) forcarDiretorioPosix(journal.getParent());
      journalInicializado = true;
    } catch (Exception exception) {
      throw new IllegalStateException("Falha ao preservar estado externo do backfill", exception);
    }
  }

  private void capturarValoresAnteriores(MidiaRestritaRegularizacaoService.Resultado plano) {
    if (plano.snapshot() == null) {
      throw new IllegalStateException("Captura privada exige snapshot consistente do PLAN");
    }
    List<Map<String, Object>> arquivos = plano.itens().stream().map(item -> {
      var arquivo = item.arquivo();
      var estado = plano.snapshot().arquivos().get(arquivo.getId());
      if (estado == null) throw new IllegalStateException("Estado anterior do arquivo ausente");
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("arquivo_id", arquivo.getId().toString());
      row.put("status_arquivo", arquivo.getStatusArquivo().name());
      row.put("preview_restrito_tipo", arquivo.getPreviewRestritoTipo() == null
          ? null : arquivo.getPreviewRestritoTipo().name());
      row.put("preview_restrito_chave", arquivo.getPreviewRestritoChave());
      row.put("preview_restrito_pipeline_versao", arquivo.getPreviewRestritoPipelineVersao());
      row.put("preview_restrito_status", estado.statusPreview());
      row.put("preview_restrito_confirmado_em", arquivo.getPreviewRestritoConfirmadoEm() == null
          ? null : arquivo.getPreviewRestritoConfirmadoEm().toString());
      row.put("estado_completo_sha256", estado.completo());
      row.put("estado_nao_preview_sha256", estado.naoPreview());
      return row;
    }).toList();
    List<Map<String, String>> vinculos = plano.snapshot().vinculos().stream().map(estado -> Map.of(
        "anuncio_midia_id", estado.vinculoId().toString(),
        "arquivo_id", estado.arquivoId().toString(),
        "estado_completo_sha256", estado.completo())).toList();
    Path target = Path.of(reportPath.toAbsolutePath().normalize() + ".before.json");
    try {
      byte[] bytes = new ObjectMapper().writeValueAsBytes(Map.of(
          "schema_version", 1, "mode", "APPLY", "arquivos", arquivos, "vinculos", vinculos));
      escreverPrivadoNovo(target, bytes);
      byte[] persistidos = Files.readAllBytes(target);
      if (!MessageDigest.isEqual(bytes, persistidos)) {
        throw new IllegalStateException("Captura privada persistida diverge do snapshot");
      }
      beforeSha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(persistidos));
    } catch (Exception exception) {
      throw new IllegalStateException("Captura privada anterior falhou; APPLY nao iniciado", exception);
    }
  }

  private void escreverPrivadoNovo(Path target, byte[] bytes) throws Exception {
    Set<StandardOpenOption> options = Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    FileAttribute<?> permission;
    if (target.getFileSystem().supportedFileAttributeViews().contains("posix")) {
      permission = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));
    } else if (System.getProperty("os.name", "").startsWith("Windows")
        && target.getFileSystem().supportedFileAttributeViews().contains("acl")) {
      // Windows local tests still use an atomic, owner-only ACL; production has no permissive fallback.
      var owner = target.getFileSystem().getUserPrincipalLookupService()
          .lookupPrincipalByName(System.getProperty("user.name"));
      List<AclEntry> acl = List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW)
          .setPrincipal(owner).setPermissions(EnumSet.allOf(AclEntryPermission.class)).build());
      permission = new FileAttribute<List<AclEntry>>() {
        @Override public String name() { return "acl:acl"; }
        @Override public List<AclEntry> value() { return acl; }
      };
    } else {
      throw new IllegalStateException("Filesystem sem protecao privada atomica suportada");
    }
    try (FileChannel channel = FileChannel.open(target, options, permission)) {
      if ("posix:permissions".equals(permission.name())) {
        if (!Files.getPosixFilePermissions(target).equals(PosixFilePermissions.fromString("rw-------"))) {
          throw new IllegalStateException("Permissoes privadas POSIX nao comprovadas");
        }
      } else {
        var acl = Files.getFileAttributeView(target, AclFileAttributeView.class).getAcl();
        if (!acl.equals(permission.value())) {
          throw new IllegalStateException("ACL privada nao comprovada");
        }
      }
      ByteBuffer buffer = ByteBuffer.wrap(bytes);
      while (buffer.hasRemaining()) channel.write(buffer);
      channel.force(true);
    }
    forcarDiretorioPosix(target.getParent());
  }

  private void forcarDiretorioPosix(Path directory) throws Exception {
    if (directory.getFileSystem().supportedFileAttributeViews().contains("posix")) {
      try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
        channel.force(true);
      }
    }
  }

  private void validarExecucao() {
    if (!"PLAN".equals(mode) && !"APPLY".equals(mode) && !"VALIDATE".equals(mode)) {
      throw new IllegalArgumentException("Modo de reconciliacao invalido");
    }
    if ("APPLY".equals(mode) && !applyConfirmed) {
      throw new IllegalStateException("APPLY exige confirmacao explicita");
    }
    if (batchSize < 1 || batchSize > 1_000) {
      throw new IllegalArgumentException("Tamanho de lote invalido");
    }
    validarCaminhoRelatorio();
  }

  private void exigirValidacao(MidiaRestritaRegularizacaoService.Validacao validacao) {
    if (!validacao.aprovada()) {
      throw new IllegalStateException("Validacao persistida dos previews reprovada");
    }
  }

  private void imprimirResultado(
      MidiaRestritaRegularizacaoService.Resultado plano,
      MidiaRestritaRegularizacaoService.Aplicacao aplicacao,
      MidiaRestritaRegularizacaoService.Validacao validacao) {
    System.out.printf(Locale.ROOT, "%s%n", String.join(" ",
        "RESTRICTED_MEDIA_PREVIEW_RECONCILIATION_RESULT",
        "mode=" + mode,
        "links=" + plano.vinculos(),
        "eligible=" + plano.arquivos(),
        "r2_available=" + plano.disponiveis(),
        "missing=" + plano.ausentes(),
        "inconsistent=" + plano.inconsistentes(),
        "unproven=" + plano.naoComprovados(),
        "r2_pages=" + plano.paginasR2(),
        "db_updated=" + aplicacao.atualizados(),
        "db_unchanged=" + aplicacao.inalterados(),
        "batches=" + aplicacao.lotes(),
        "db_available=" + validacao.disponiveis(),
        "db_unknown=" + validacao.desconhecidos(),
        "db_pending=" + validacao.pendentes(),
        "db_inconsistent=" + validacao.inconsistentes()));
  }

  private void escreverRelatorio(MidiaRestritaRegularizacaoService.Resultado plano) {
    Path absolute = reportPath.toAbsolutePath().normalize();
    StringBuilder report = new StringBuilder("arquivo_hash\tchave_hash\tclassificacao\n");
    for (MidiaRestritaRegularizacaoService.Item item : plano.itens()) {
      report.append(hash(item.arquivo().getId().toString())).append('\t')
          .append(hash(item.chaveEsperada())).append('\t')
          .append(item.classificacao()).append('\n');
    }
    try {
      if (absolute.getParent() != null) {
        Files.createDirectories(absolute.getParent());
      }
      Files.writeString(absolute, report, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    } catch (Exception exception) {
      throw new IllegalStateException("Falha ao escrever relatorio sanitizado", exception);
    }
  }

  private void validarCaminhoRelatorio() {
    if (reportPath == null) {
      throw new IllegalStateException("Caminho externo do relatorio obrigatorio");
    }
    Path absolute = reportPath.toAbsolutePath().normalize();
    if (Files.exists(absolute) || Files.exists(Path.of(absolute + ".state.jsonl"))
        || Files.exists(Path.of(absolute + ".before.json"))) {
      throw new IllegalStateException("Evidencia anterior existente; use novo caminho sem sobrescrever");
    }
    localizarRaizGit().ifPresent(repository -> {
      if (absolute.startsWith(repository)) {
        throw new IllegalArgumentException("Relatorio deve permanecer fora do Git");
      }
    });
  }

  private Optional<Path> localizarRaizGit() {
    Path current = Path.of("").toAbsolutePath().normalize();
    while (current != null) {
      if (Files.exists(current.resolve(".git"))) {
        return Optional.of(current);
      }
      current = current.getParent();
    }
    return Optional.empty();
  }

  private String hash(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 indisponivel", exception);
    }
  }
}
