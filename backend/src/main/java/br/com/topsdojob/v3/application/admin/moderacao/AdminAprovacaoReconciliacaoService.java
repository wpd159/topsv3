package br.com.topsdojob.v3.application.admin.moderacao;

import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Reads committed evidence of one approval operation; never infers its outcome from the current ad status. */
@Service
public class AdminAprovacaoReconciliacaoService {
  private final NamedParameterJdbcTemplate jdbc;

  public AdminAprovacaoReconciliacaoService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true)
  public Resultado consultar(
      UUID anuncioId, UUID operacaoId, Integer versaoEsperada, UUID revisaoEsperada,
      AdminUserPrincipal actor) {
    if (actor == null || actor.usuarioId() == null) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "acesso negado");
    }
    if (anuncioId == null || operacaoId == null || versaoEsperada == null || versaoEsperada < 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "operacao, anuncio e versao obrigatorios");
    }
    Map<String, ?> parametros = Map.of(
        "anuncioId", anuncioId,
        "anuncioTexto", anuncioId.toString(),
        "operacaoTexto", operacaoId.toString(),
        "versaoTexto", versaoEsperada.toString(),
        "atorId", actor.usuarioId());

    // These rows must share the PostgreSQL transaction identity as well as the actor, timestamp,
    // revision and client operation. Matching application fields alone cannot prove a common commit.
    // If restored/migrated evidence loses this physical identity, fail closed as inconclusive.
    List<UUID> decisoes = jdbc.query("""
        SELECT r.id
        FROM auditoria_evento e
        JOIN revisao_anuncio r ON r.id = e.recurso_id AND r.anuncio_id = :anuncioId
        JOIN decisao_moderacao d ON d.revisao_anuncio_id = r.id
          AND d.decisao = 'APROVAR' AND d.ator_usuario_id = :atorId
          AND d.criado_em = e.criado_em
          AND d.xmin = e.xmin
        WHERE e.acao = 'MODERACAO_REVISAO_DECIDIR'
          AND e.recurso_tipo = 'REVISAO_ANUNCIO' AND e.resultado = 'SUCESSO'
          AND e.ator_usuario_id = :atorId
          AND e.depois_json ->> 'operacaoIdCliente' = :operacaoTexto
          AND e.depois_json ->> 'anuncioId' = :anuncioTexto
          AND e.depois_json ->> 'revisaoId' = CAST(r.id AS text)
          AND e.depois_json ->> 'versaoAnuncioAntes' = :versaoTexto
          AND e.depois_json ->> 'decisao' = 'APROVAR'
          AND e.depois_json ->> 'statusRevisao' = 'APROVADA'
          AND e.depois_json ->> 'statusAnuncio' = 'PUBLICADO'
          AND e.depois_json ->> 'statusModeracao' = 'APROVADO'
        LIMIT 2
        """, parametros, (rs, row) -> rs.getObject(1, UUID.class));
    if (decisoes.size() == 1 && (revisaoEsperada == null || revisaoEsperada.equals(decisoes.get(0)))) {
      return new Resultado("CONFIRMADA", anuncioId, operacaoId, decisoes.get(0), versaoEsperada);
    }
    if (!decisoes.isEmpty()) {
      return inconclusiva(anuncioId, operacaoId, versaoEsperada);
    }

    // Legacy publication regularization is a committed effect of this operation, but it is not
    // a newly recorded approval decision. Keep the distinction explicit in the response.
    List<Evidencia> regularizacoes = jdbc.query("""
        SELECT e.recurso_tipo, e.recurso_id
        FROM auditoria_evento e
        LEFT JOIN revisao_anuncio r ON r.id = e.recurso_id
          AND e.recurso_tipo = 'REVISAO_ANUNCIO' AND r.anuncio_id = :anuncioId
        WHERE e.acao IN ('MODERACAO_REVISAO_PUBLICACAO_REGULARIZAR',
                        'MODERACAO_ANUNCIO_PUBLICACAO_REGULARIZAR')
          AND e.resultado = 'SUCESSO' AND e.ator_usuario_id = :atorId
          AND ((e.recurso_tipo = 'REVISAO_ANUNCIO' AND r.id IS NOT NULL)
            OR (e.recurso_tipo = 'ANUNCIO' AND e.recurso_id = :anuncioId))
          AND e.depois_json ->> 'operacaoIdCliente' = :operacaoTexto
          AND e.depois_json ->> 'anuncioId' = :anuncioTexto
          AND e.depois_json ->> 'versaoAnuncioAntes' = :versaoTexto
          AND e.depois_json ->> 'decisao' = 'APROVAR'
          AND (e.recurso_tipo <> 'REVISAO_ANUNCIO'
            OR e.depois_json ->> 'revisaoId' = CAST(r.id AS text))
          AND e.depois_json ->> 'statusAnuncio' = 'PUBLICADO'
          AND e.depois_json ->> 'statusModeracao' = 'APROVADO'
        LIMIT 2
        """, parametros, (rs, row) -> new Evidencia(
            rs.getString(1), rs.getObject(2, UUID.class)));
    if (regularizacoes.size() == 1) {
      Evidencia evidencia = regularizacoes.get(0);
      boolean revisaoCompativel = "REVISAO_ANUNCIO".equals(evidencia.recursoTipo())
          ? revisaoEsperada == null || revisaoEsperada.equals(evidencia.recursoId())
          : revisaoEsperada == null;
      if (revisaoCompativel) {
        UUID revisao = "REVISAO_ANUNCIO".equals(evidencia.recursoTipo())
            ? evidencia.recursoId() : null;
        return new Resultado("PUBLICACAO_REGULARIZADA", anuncioId, operacaoId, revisao, versaoEsperada);
      }
    }
    return inconclusiva(anuncioId, operacaoId, versaoEsperada);
  }

  private Resultado inconclusiva(UUID anuncioId, UUID operacaoId, Integer versaoEsperada) {
    return new Resultado("INCONCLUSIVA", anuncioId, operacaoId, null, versaoEsperada);
  }

  public record Resultado(
      String estado, UUID anuncioId, UUID operacaoIdCliente,
      UUID revisaoIdConfirmada, Integer versaoAnuncioAntes) { }

  private record Evidencia(String recursoTipo, UUID recursoId) { }
}
