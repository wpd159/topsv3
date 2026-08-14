package br.com.topsdojob.v3.importacao.integracao;

import br.com.topsdojob.v3.importacao.integracao.MigracaoIntegralStorageConfiguration.DestinoProperties;
import br.com.topsdojob.v3.importacao.integracao.RepositorioCandidatosMidiaLegada.Descritor;
import br.com.topsdojob.v3.importacao.midia.FonteMidiaMigracao;
import br.com.topsdojob.v3.importacao.midia.GeradorChaveDestinoMidiaMigracao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Origem;
import br.com.topsdojob.v3.importacao.midia.PlanejadorMidiaFaseCinco;
import br.com.topsdojob.v3.importacao.midia.PlanejadorMidiaFaseCinco.Candidato;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("migracao-integral")
@ConditionalOnProperty(
    prefix = "app.migracao.integral",
    name = "enabled",
    havingValue = "true")
@ConditionalOnExpression("'${app.migracao.integral.operacao:}' == 'PRODUZIR_MANIFESTO'")
public class ProdutorManifestoMidiaFaseCinco {

  private static final int MAX_TENTATIVAS_LEITURA = 3;

  private final RepositorioCandidatosMidiaLegada candidatos;
  private final FonteMidiaMigracao fonte;
  private final PlanejadorMidiaFaseCinco planejador;
  private final ObjectMapper mapper;
  private final ValidadorManifestoMidiaFaseCinco validador;
  private final RepositorioManifestoMidiaFaseCinco repositorio;

  public ProdutorManifestoMidiaFaseCinco(
      RepositorioCandidatosMidiaLegada candidatos,
      FonteMidiaMigracao fonte,
      DestinoProperties destinoProperties,
      ObjectMapper mapper) {
    this.candidatos = Objects.requireNonNull(candidatos, "adapter de candidatos obrigatorio");
    this.fonte = Objects.requireNonNull(fonte, "fonte de midia obrigatoria");
    this.planejador = new PlanejadorMidiaFaseCinco(
        new GeradorChaveDestinoMidiaMigracao(destinoProperties.r2()));
    this.mapper = mapper;
    this.validador = new ValidadorManifestoMidiaFaseCinco(
        mapper, destinoProperties.r2());
    this.repositorio = new RepositorioManifestoMidiaFaseCinco(mapper);
  }

  public ResultadoProducao produzir(Parametros parametros) {
    Objects.requireNonNull(parametros, "parametros obrigatorios");
    List<Descritor> descritores = candidatos.listar();
    if (descritores.isEmpty()) {
      throw new IllegalStateException("snapshot restaurado nao produziu candidatos de midia");
    }
    Map<Origem, Metadados> metadados = carregarMetadados(descritores);
    List<Candidato> candidatosPlanejador = descritores.stream()
        .map(descritor -> candidato(descritor, metadados.get(descritor.origem())))
        .toList();
    ManifestoMidiaFaseCinco manifesto = planejador.planejar(candidatosPlanejador);
    String fingerprintOrigem = fingerprintDadosOrigem(descritores);
    ArquivoManifestoMidiaFaseCinco arquivo = ArquivoManifestoMidiaFaseCinco.criar(
        parametros.origemId(),
        parametros.snapshotSha256(),
        parametros.execucaoId(),
        parametros.geradoEm(),
        fingerprintOrigem,
        manifesto,
        mapper);
    var escrita = repositorio.gravarAtomico(
        parametros.saida(),
        arquivo,
        validador,
        parametros.origemId(),
        parametros.snapshotSha256());
    return new ResultadoProducao(
        escrita.estado(),
        arquivo.fingerprint(),
        escrita.arquivoSha256(),
        arquivo.contagens(),
        resumoStorage(descritores, metadados));
  }

  private Map<Origem, Metadados> carregarMetadados(List<Descritor> descritores) {
    Map<Origem, Metadados> resultado = new LinkedHashMap<>();
    descritores.stream()
        .filter(Descritor::referenciaValida)
        .map(Descritor::origem)
        .distinct()
        .sorted(java.util.Comparator.comparing(
            origem -> origem.area() + "|" + origem.localizador()))
        .forEach(origem -> resultado.put(origem, carregar(origem)));
    return Map.copyOf(resultado);
  }

  private Metadados carregar(Origem origem) {
    int retries = 0;
    while (true) {
      try {
        StoredObject objeto = fonte.carregar(origem);
        byte[] conteudo = objeto.content();
        return new Metadados(
            true,
            conteudo.length,
            FingerprintMigracaoIntegral.sha256(conteudo),
            normalizarMime(objeto.contentType(), conteudo),
            retries);
      } catch (FonteMidiaMigracao.ObjetoOrigemAusenteException exception) {
        return Metadados.ausente(retries);
      } catch (FonteMidiaMigracao.OrigemMidiaInvalidaException exception) {
        throw new IllegalStateException(
            "referencia validada pelo adapter foi rejeitada pela fonte read-only", exception);
      } catch (R2StorageException | FonteMidiaMigracao.FalhaTransitoriaOrigemException exception) {
        if (Thread.currentThread().isInterrupted() || retries >= MAX_TENTATIVAS_LEITURA - 1) {
          throw new FonteMidiaMigracao.FalhaTransitoriaOrigemException(exception);
        }
        retries++;
        aguardarRetry(retries);
      }
    }
  }

  private static void aguardarRetry(int retry) {
    try {
      Thread.sleep(200L * retry);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new FonteMidiaMigracao.FalhaTransitoriaOrigemException(exception);
    }
  }

  private Candidato candidato(Descritor descritor, Metadados metadados) {
    Metadados efetivos = metadados == null ? Metadados.ausente() : metadados;
    String mime = mimeEfetivo(efetivos.mimeType(), descritor.mimeType());
    String extensao = descritor.extensao() == null
        ? extensaoPorMime(mime)
        : descritor.extensao();
    return new Candidato(
        descritor.idOrigem(),
        descritor.entidadeTipo(),
        descritor.entidadeOrigemId(),
        descritor.entidadeV3Id(),
        descritor.proprietarioOrigemId(),
        descritor.proprietarioV3Id(),
        descritor.referenciaOrigemId(),
        descritor.finalidade(),
        descritor.tipoMidia(),
        descritor.visibilidade(),
        descritor.estadoModeracao(),
        descritor.referenciaValida() && descritor.referenciaPersistida(),
        efetivos.existe(),
        descritor.capaValida(),
        descritor.ordem(),
        mime,
        efetivos.tamanhoBytes(),
        efetivos.sha256(),
        extensao,
        descritor.origem());
  }

  private String fingerprintDadosOrigem(List<Descritor> descritores) {
    String canonico = descritores.stream()
        .map(Descritor::representacaoCanonica)
        .sorted()
        .reduce("", (esquerda, direita) -> esquerda + direita + "\n");
    return FingerprintMigracaoIntegral.sha256(canonico.getBytes(StandardCharsets.UTF_8));
  }

  private static Map<String, Long> resumoStorage(
      List<Descritor> descritores,
      Map<Origem, Metadados> metadados) {
    long encontradas = metadados.values().stream().filter(Metadados::existe).count();
    long ausentes = metadados.size() - encontradas;
    long invalidas = descritores.stream().filter(item -> !item.referenciaValida()).count();
    long retries = metadados.values().stream().mapToLong(Metadados::retries).sum();
    Map<String, Long> resumo = new LinkedHashMap<>();
    resumo.put("HEAD_SOLICITADOS", (long) metadados.size());
    resumo.put("GET_EXECUTADOS", encontradas);
    resumo.put("ENCONTRADAS", encontradas);
    resumo.put("AUSENTES", ausentes);
    resumo.put("REFERENCIAS_INVALIDAS", invalidas);
    resumo.put("RETRIES", retries);
    resumo.put("MUTACOES", 0L);
    return Map.copyOf(resumo);
  }

  private static String mimeEfetivo(String storage, String declarado) {
    String normalizadoStorage = limparMime(storage);
    if (normalizadoStorage != null && !"application/octet-stream".equals(normalizadoStorage)) {
      return normalizadoStorage;
    }
    return limparMime(declarado);
  }

  private static String normalizarMime(String valor, byte[] conteudo) {
    String normalizado = limparMime(valor);
    if (normalizado != null && !"application/octet-stream".equals(normalizado)) {
      return normalizado;
    }
    return detectarMime(conteudo);
  }

  private static String limparMime(String valor) {
    if (valor == null || valor.isBlank()) {
      return null;
    }
    return valor.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
  }

  private static String detectarMime(byte[] conteudo) {
    if (conteudo.length >= 3
        && (conteudo[0] & 0xff) == 0xff
        && (conteudo[1] & 0xff) == 0xd8
        && (conteudo[2] & 0xff) == 0xff) {
      return "image/jpeg";
    }
    if (conteudo.length >= 8
        && conteudo[0] == (byte) 0x89
        && conteudo[1] == 'P'
        && conteudo[2] == 'N'
        && conteudo[3] == 'G') {
      return "image/png";
    }
    if (conteudo.length >= 12
        && conteudo[0] == 'R'
        && conteudo[1] == 'I'
        && conteudo[2] == 'F'
        && conteudo[3] == 'F'
        && conteudo[8] == 'W'
        && conteudo[9] == 'E'
        && conteudo[10] == 'B'
        && conteudo[11] == 'P') {
      return "image/webp";
    }
    if (conteudo.length >= 4
        && conteudo[0] == '%'
        && conteudo[1] == 'P'
        && conteudo[2] == 'D'
        && conteudo[3] == 'F') {
      return "application/pdf";
    }
    if (conteudo.length >= 12
        && conteudo[4] == 'f'
        && conteudo[5] == 't'
        && conteudo[6] == 'y'
        && conteudo[7] == 'p') {
      String marca = new String(conteudo, 8, 4, StandardCharsets.US_ASCII);
      return marca.startsWith("qt") ? "video/quicktime" : "video/mp4";
    }
    return null;
  }

  private static String extensaoPorMime(String mime) {
    return switch (mime == null ? "" : mime) {
      case "image/jpeg" -> "jpg";
      case "image/png" -> "png";
      case "image/webp" -> "webp";
      case "video/mp4" -> "mp4";
      case "video/quicktime" -> "mov";
      case "application/pdf" -> "pdf";
      default -> null;
    };
  }

  private record Metadados(
      boolean existe,
      long tamanhoBytes,
      String sha256,
      String mimeType,
      int retries) {

    static Metadados ausente() {
      return ausente(0);
    }

    static Metadados ausente(int retries) {
      return new Metadados(false, 0, null, null, retries);
    }
  }

  public record Parametros(
      String origemId,
      String snapshotSha256,
      String execucaoId,
      OffsetDateTime geradoEm,
      Path saida) {

    public Parametros {
      origemId = obrigatorio(origemId, "origemId");
      snapshotSha256 = obrigatorio(snapshotSha256, "snapshotSha256")
          .toLowerCase(Locale.ROOT);
      if (!snapshotSha256.matches("[0-9a-f]{64}")) {
        throw new IllegalArgumentException("snapshotSha256 invalido");
      }
      execucaoId = obrigatorio(execucaoId, "execucaoId");
      if (geradoEm == null || saida == null) {
        throw new IllegalArgumentException("geracao e saida do manifesto sao obrigatorias");
      }
    }
  }

  public record ResultadoProducao(
      RepositorioManifestoMidiaFaseCinco.EstadoEscrita estado,
      String fingerprint,
      String arquivoSha256,
      Map<String, Long> contagens,
      Map<String, Long> storage) {
  }

  private static String obrigatorio(String valor, String campo) {
    if (valor == null || valor.isBlank()) {
      throw new IllegalArgumentException(campo + " deve ser informado");
    }
    return valor.trim();
  }
}
