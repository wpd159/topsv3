package br.com.topsdojob.v3.importacao.integracao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public record ArquivoPacoteMigracaoIntegral(
    String schemaVersion,
    String origemId,
    String snapshotSha256,
    String manifestoFaseCincoArquivo,
    String manifestoFaseCincoSha256,
    Map<String, String> manifestosSha256,
    Map<String, Long> contagens,
    String fingerprint,
    OffsetDateTime geradoEm,
    PacoteMigracaoIntegral pacote) {

  public static final String SCHEMA_VERSION = "topsdojob.migracao.integral/v1";

  public ArquivoPacoteMigracaoIntegral {
    schemaVersion = obrigatorio(schemaVersion, "schemaVersion");
    origemId = obrigatorio(origemId, "origemId");
    snapshotSha256 = hash(snapshotSha256, "snapshotSha256");
    manifestoFaseCincoArquivo = obrigatorio(
        manifestoFaseCincoArquivo, "manifestoFaseCincoArquivo");
    manifestoFaseCincoSha256 = hash(
        manifestoFaseCincoSha256, "manifestoFaseCincoSha256");
    manifestosSha256 = Map.copyOf(manifestosSha256 == null ? Map.of() : manifestosSha256);
    contagens = Map.copyOf(contagens == null ? Map.of() : contagens);
    fingerprint = hash(fingerprint, "fingerprint");
    if (geradoEm == null) {
      throw new IllegalArgumentException("geradoEm deve ser informado");
    }
    if (pacote == null) {
      throw new IllegalArgumentException("pacote deve ser informado");
    }
  }

  public static ArquivoPacoteMigracaoIntegral criar(
      String origemId,
      String snapshotSha256,
      String manifestoFaseCincoArquivo,
      Map<String, String> manifestosSha256,
      OffsetDateTime geradoEm,
      PacoteMigracaoIntegral pacote,
      ObjectMapper mapper) {
    Map<String, Long> contagens = contagens(pacote);
    String manifestoSha = pacote.faseCinco().sha256();
    ArquivoPacoteMigracaoIntegral normalizado = new ArquivoPacoteMigracaoIntegral(
        SCHEMA_VERSION,
        origemId,
        snapshotSha256,
        manifestoFaseCincoArquivo,
        manifestoSha,
        manifestosSha256,
        contagens,
        "0".repeat(64),
        geradoEm,
        pacote);
    String fingerprint = fingerprint(mapper, normalizado);
    return new ArquivoPacoteMigracaoIntegral(
        normalizado.schemaVersion(),
        normalizado.origemId(),
        normalizado.snapshotSha256(),
        normalizado.manifestoFaseCincoArquivo(),
        normalizado.manifestoFaseCincoSha256(),
        normalizado.manifestosSha256(),
        normalizado.contagens(),
        fingerprint,
        normalizado.geradoEm(),
        normalizado.pacote());
  }

  static String fingerprint(ObjectMapper mapper, ArquivoPacoteMigracaoIntegral arquivo) {
    return fingerprint(
        mapper,
        arquivo.schemaVersion(),
        arquivo.origemId(),
        arquivo.snapshotSha256(),
        arquivo.manifestoFaseCincoArquivo(),
        arquivo.manifestoFaseCincoSha256(),
        arquivo.manifestosSha256(),
        arquivo.contagens(),
        arquivo.pacote());
  }

  static Map<String, Long> contagens(PacoteMigracaoIntegral pacote) {
    Map<String, Long> valores = new LinkedHashMap<>();
    valores.put("base.localidades", tamanho(pacote.base().localidades()));
    valores.put("base.usuarios", tamanho(pacote.base().usuarios()));
    valores.put("base.credenciais", tamanho(pacote.base().credenciais()));
    valores.put("base.documentosKyc", tamanho(pacote.base().documentosKyc()));
    valores.put("base.usuariosStaging", tamanho(pacote.base().usuariosStaging()));
    valores.put("base.orfaos", tamanho(pacote.base().orfaos()));
    valores.put("base.favoritos", tamanho(pacote.base().favoritos()));
    valores.put("base.metricas", tamanho(pacote.base().metricas()));
    valores.put("fase1.anuncios", tamanho(pacote.faseUm().anuncios()));
    valores.put("fase1.filhosOrfaos", tamanho(pacote.faseUm().filhosOrfaos()));
    valores.put("fase2.faqs", tamanho(pacote.faseDois().faqs()));
    valores.put("fase2.avisos", tamanho(pacote.faseDois().avisos()));
    valores.put("fase2.categoriasBlog", tamanho(pacote.faseDois().categoriasBlog()));
    valores.put("fase2.postsBlog", tamanho(pacote.faseDois().postsBlog()));
    valores.put(
        "fase2.conteudosInstitucionais",
        tamanho(pacote.faseDois().conteudosInstitucionais()));
    valores.put("fase2.localidadesSeo", tamanho(pacote.faseDois().localidadesSeo()));
    valores.put("fase2.anunciosSeo", tamanho(pacote.faseDois().anunciosSeo()));
    valores.put("fase2.redirects", tamanho(pacote.faseDois().redirects()));
    valores.put("fase3.beneficios", tamanho(pacote.faseTres().beneficios()));
    valores.put("fase3.opcoes", tamanho(pacote.faseTres().opcoes()));
    valores.put("fase3.pacotes", tamanho(pacote.faseTres().pacotes()));
    valores.put("fase3.stories", tamanho(pacote.faseTres().configuracoesStory()));
    valores.put("fase4.pagamentos", tamanho(pacote.faseQuatro().pagamentos()));
    valores.put("fase4.gruposAtivacao", tamanho(pacote.faseQuatro().gruposAtivacao()));
    valores.put("fase4.carteiras", tamanho(pacote.faseQuatro().carteiras()));
    valores.put("fase5.itens", tamanho(pacote.faseCinco().itens()));
    return Map.copyOf(valores);
  }

  private static String fingerprint(
      ObjectMapper mapper,
      String schemaVersion,
      String origemId,
      String snapshotSha256,
      String manifestoFaseCincoArquivo,
      String manifestoFaseCincoSha256,
      Map<String, String> manifestosSha256,
      Map<String, Long> contagens,
      PacoteMigracaoIntegral pacote) {
    Map<String, Object> canonico = new LinkedHashMap<>();
    canonico.put("schemaVersion", schemaVersion);
    canonico.put("origemId", origemId);
    canonico.put("snapshotSha256", snapshotSha256);
    canonico.put("manifestoFaseCincoArquivo", manifestoFaseCincoArquivo);
    canonico.put("manifestoFaseCincoSha256", manifestoFaseCincoSha256);
    canonico.put("manifestosSha256", manifestosSha256);
    canonico.put("contagens", contagens);
    canonico.put("pacote", pacote);
    try {
      SimpleModule datasCanonicas = new SimpleModule("datas-canonicas-migracao");
      datasCanonicas.addSerializer(OffsetDateTime.class, new JsonSerializer<>() {
        @Override
        public void serialize(
            OffsetDateTime valor,
            JsonGenerator gerador,
            SerializerProvider serializers) throws IOException {
          gerador.writeString(valor.withOffsetSameInstant(ZoneOffset.UTC).toString());
        }
      });
      ObjectMapper ordenado = mapper.copy()
          .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
      ordenado.registerModule(datasCanonicas);
      return FingerprintMigracaoIntegral.sha256(ordenado.writeValueAsBytes(canonico));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("falha ao calcular fingerprint do pacote", exception);
    }
  }

  private static long tamanho(java.util.Collection<?> valores) {
    return valores == null ? 0 : valores.size();
  }

  private static String hash(String valor, String campo) {
    String normalizado = obrigatorio(valor, campo).toLowerCase(Locale.ROOT);
    if (!normalizado.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException(campo + " deve ser SHA-256 hexadecimal");
    }
    return normalizado;
  }

  private static String obrigatorio(String valor, String campo) {
    if (valor == null || valor.isBlank()) {
      throw new IllegalArgumentException(campo + " deve ser informado");
    }
    return valor.trim();
  }
}
