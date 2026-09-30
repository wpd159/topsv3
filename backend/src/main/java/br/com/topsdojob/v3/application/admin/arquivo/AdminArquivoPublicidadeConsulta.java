package br.com.topsdojob.v3.application.admin.arquivo;

import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Busca limitada sobre snapshots, compartilhada pelas duas familias de arquivo. */
public final class AdminArquivoPublicidadeConsulta {
  public static final int LIMITE_RELATORIO = 100;

  private AdminArquivoPublicidadeConsulta() { }

  public enum Situacao { EM_VEICULACAO, ENCERRADA }
  public enum Ordenacao { RECENTES, ANTIGOS }

  public record Filtros(String termo, UUID anuncioId, UUID anuncianteId,
      String beneficio, Situacao situacao, OffsetDateTime inicio, OffsetDateTime fim,
      Ordenacao ordenacao) {
    public Filtros {
      termo = texto(termo, 200);
      beneficio = texto(beneficio, 80);
      if (beneficio != null) {
        beneficio = beneficio.toUpperCase(Locale.ROOT);
        if (!beneficio.matches("[A-Z0-9_]+")) throw invalido("Beneficio invalido");
      }
      if (inicio != null && fim != null && !inicio.isBefore(fim)) {
        throw invalido("O inicio do periodo deve ser anterior ao fim exclusivo");
      }
      ordenacao = ordenacao == null ? Ordenacao.RECENTES : ordenacao;
    }

    public static Filtros todos() {
      return new Filtros(null, null, null, null, null, null, null, null);
    }
  }

  public record RelatorioRequest(Filtros filtros, List<UUID> ids, String fusoHorario) {
    public RelatorioRequest {
      filtros = filtros == null ? Filtros.todos() : filtros;
      if (ids != null && (ids.stream().anyMatch(java.util.Objects::isNull)
          || ids.stream().distinct().count() != ids.size())) {
        throw invalido("A selecao deve conter identificadores unicos e validos");
      }
      ids = ids == null ? List.of() : List.copyOf(ids);
      if (ids.size() > LIMITE_RELATORIO) throw limite();
      fusoHorario = fusoHorario == null || fusoHorario.isBlank()
          ? "America/Sao_Paulo" : fusoHorario.trim();
      try {
        if (fusoHorario.length() > 80) throw new DateTimeException("fuso invalido");
        ZoneId.of(fusoHorario);
      } catch (DateTimeException exception) {
        throw invalido("Fuso horario invalido");
      }
    }
  }

  enum Tipo {
    ANUNCIO("arquivo_publicidade_veiculacao", "arquivo_publicidade_versao",
        "arquivo_publicidade_hold"),
    STORY("arquivo_publicidade_story_veiculacao", "arquivo_publicidade_story_versao",
        "arquivo_publicidade_story_hold");
    final String tabela;
    final String versao;
    final String hold;
    Tipo(String tabela, String versao, String hold) {
      this.tabela = tabela;
      this.versao = versao;
      this.hold = hold;
    }
  }

  record Sql(String fromWhere, Object[] argumentos) {
    Object[] comPagina(int size, long offset) {
      var valores = new ArrayList<>(List.of(argumentos));
      valores.add(size);
      valores.add(offset);
      return valores.toArray();
    }
  }

  static Sql consulta(Tipo tipo, Filtros filtros, List<UUID> ids,
      OffsetDateTime observadoEm) {
    StringBuilder sql = new StringBuilder(" from ").append(tipo.tabela).append(" v ")
        .append(" left join lateral (select av.conteudo_json, ")
        .append("av.comercial_json from ").append(tipo.versao)
        .append(" av where av.veiculacao_id = v.id order by av.numero desc limit 1) x on true ")
        .append(" where 1 = 1 ");
    var argumentos = new ArrayList<Object>();
    if (filtros.termo() != null) {
      sql.append(" and (strpos(lower(v.id::text), ?) > 0 ")
          .append("or strpos(lower(coalesce(v.anuncio_id::text, '')), ?) > 0 ")
          .append("or strpos(lower(v.contratante_usuario_id::text), ?) > 0 ");
      String termo = filtros.termo().toLowerCase(Locale.ROOT);
      argumentos.add(termo);
      argumentos.add(termo);
      argumentos.add(termo);
      if (tipo == Tipo.STORY) {
        sql.append("or strpos(lower(v.story_id::text), ?) > 0 ");
        argumentos.add(termo);
      }
      sql.append("or exists (select 1 from ").append(tipo.versao)
          .append(" busca where busca.veiculacao_id = v.id and ")
          .append("strpos(unaccent(lower(concat_ws(' ', busca.conteudo_json ->> 'titulo', ")
          .append("busca.conteudo_json ->> 'slug', busca.conteudo_json ->> 'nomePublico'))), ")
          .append("unaccent(?)) > 0)) ");
      argumentos.add(termo);
    }
    if (filtros.anuncioId() != null) {
      sql.append(" and v.anuncio_id = ? "); argumentos.add(filtros.anuncioId());
    }
    if (filtros.anuncianteId() != null) {
      sql.append(" and v.contratante_usuario_id = ? "); argumentos.add(filtros.anuncianteId());
    }
    if (filtros.beneficio() != null) {
      sql.append(" and exists (select 1 from ").append(tipo.versao)
          .append(" beneficio where beneficio.veiculacao_id = v.id ")
          .append("and beneficio.comercial_json ->> 'beneficioCodigo' = ?) ");
      argumentos.add(filtros.beneficio());
    }
    if (filtros.situacao() != null) {
      sql.append(filtros.situacao() == Situacao.EM_VEICULACAO
          ? " and (v.fim_em is null or v.fim_em > ?) "
          : " and v.fim_em is not null and v.fim_em <= ? ");
      argumentos.add(observadoEm);
    }
    // Periodo de sobreposicao: inicio inclusivo, fim exclusivo, nao data de captura.
    if (filtros.inicio() != null) {
      sql.append(" and (v.fim_em is null or v.fim_em > ?) "); argumentos.add(filtros.inicio());
    }
    if (filtros.fim() != null) {
      sql.append(" and v.inicio_em < ? "); argumentos.add(filtros.fim());
    }
    if (!ids.isEmpty()) {
      sql.append(" and v.id in (")
          .append(String.join(",", java.util.Collections.nCopies(ids.size(), "?")))
          .append(") ");
      argumentos.addAll(ids);
    }
    return new Sql(sql.toString(), argumentos.toArray());
  }

  static String ordem(Filtros filtros) {
    return filtros.ordenacao() == Ordenacao.ANTIGOS
        ? " order by v.inicio_em asc, v.id asc "
        : " order by v.inicio_em desc, v.id desc ";
  }

  static String resumo(Tipo tipo) {
    return "x.conteudo_json ->> 'slug' as slug, "
        + "x.conteudo_json ->> 'nomePublico' as anunciante_nome, "
        + "x.comercial_json ->> 'beneficioCodigo' as beneficio_codigo, "
        + "(select count(*) from " + tipo.versao + " av where av.veiculacao_id = v.id) as total_versoes, "
        + "exists(select 1 from " + tipo.hold + " h where h.veiculacao_id = v.id "
        + "and h.encerrado_em is null) as preservacao_ativa ";
  }

  static String fimTipo(OffsetDateTime fim, String motivo) {
    if (fim == null) return "SEM_TERMINO_REGISTRADO";
    return motivo != null && motivo.startsWith("LIMITE_AUTOMATICO_")
        ? "LIMITE_PREVISTO" : "ENCERRAMENTO_REGISTRADO";
  }

  static void conferirEscopo(long total, List<UUID> ids) {
    if (total > LIMITE_RELATORIO) throw limite();
    if (!ids.isEmpty() && total != ids.size()) {
      throw invalido("A selecao contem registros ausentes ou fora dos filtros; confira o escopo");
    }
  }

  static void conferirCompletude(long total, List<UUID> ids) {
    if (ids.size() != total || ids.stream().distinct().count() != total) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "O escopo do relatorio ficou inconsistente; nenhum relatorio foi preparado");
    }
  }

  static String escopoSha256(RelatorioRequest request) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(request.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 indisponivel", exception);
    }
  }

  static List<String> lacunasRelatorio() {
    return List.of(
        "Relatorio preparado; nao comprova impressao, recebimento ou entrega de midias.",
        "Os links administrativos consultam o cadastro atual; o conteudo deste relatorio usa snapshots.",
        "Fim automatico e limite previsto, nao sondagem independente do encerramento efetivo.",
        "Retencao ate e data minima de guarda configurada, nao data de exclusao.",
        "PREVENTIVA, DESCONHECIDA e valores nao aferidos nao comprovam pagamento, cobertura juridica ou alcance.",
        "O arquivo e prospectivo; ausencia de versao nao demonstra inexistencia de exposicao anterior.");
  }

  private static String texto(String valor, int limite) {
    if (valor == null || valor.isBlank()) return null;
    String normalizado = valor.trim();
    if (normalizado.length() > limite || normalizado.chars().anyMatch(Character::isISOControl)) {
      throw invalido("Filtro de busca invalido");
    }
    return normalizado;
  }

  private static ResponseStatusException invalido(String mensagem) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
  }

  private static ResponseStatusException limite() {
    return new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
        "O relatorio admite no maximo 100 registros completos; delimite os filtros ou a selecao");
  }
}
