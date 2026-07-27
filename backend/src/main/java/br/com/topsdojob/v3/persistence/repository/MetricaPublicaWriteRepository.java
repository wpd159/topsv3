package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.metrica.CliqueWhatsappEntity;
import br.com.topsdojob.v3.persistence.entity.metrica.EventoVisualizacaoEntity;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MetricaPublicaWriteRepository {

    private final JdbcTemplate jdbcTemplate;

    public MetricaPublicaWriteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int inserirVisualizacaoSeAusente(EventoVisualizacaoEntity evento) {
        return jdbcTemplate.update("""
                INSERT INTO evento_visualizacao (
                  id, anuncio_id, visitante_hash, ip_hash, user_agent_hash, referer_hash,
                  origem_pais, origem_uf, origem_cidade, dispositivo, request_id, criado_em
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """,
                evento.getId(),
                evento.getAnuncioId(),
                evento.getVisitanteHash(),
                evento.getIpHash(),
                evento.getUserAgentHash(),
                evento.getRefererHash(),
                evento.getOrigemPais(),
                evento.getOrigemUf(),
                evento.getOrigemCidade(),
                evento.getDispositivo() == null ? null : evento.getDispositivo().name(),
                evento.getRequestId(),
                evento.getCriadoEm());
    }

    public int inserirCliqueSeAusente(CliqueWhatsappEntity clique) {
        return jdbcTemplate.update("""
                INSERT INTO clique_whatsapp (
                  id, anuncio_id, visitante_hash, ip_hash, user_agent_hash,
                  origem_pais, origem_uf, origem_cidade, dispositivo, permitido,
                  motivo_bloqueio, request_id, criado_em
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """,
                clique.getId(),
                clique.getAnuncioId(),
                clique.getVisitanteHash(),
                clique.getIpHash(),
                clique.getUserAgentHash(),
                clique.getOrigemPais(),
                clique.getOrigemUf(),
                clique.getOrigemCidade(),
                clique.getDispositivo() == null ? null : clique.getDispositivo().name(),
                clique.getPermitido(),
                clique.getMotivoBloqueio(),
                clique.getRequestId(),
                clique.getCriadoEm());
    }

    public void incrementarVisualizacaoDiaria(
            UUID id,
            UUID anuncioId,
            LocalDate dataReferencia,
            String origemUf,
            String origemCidade,
            String origemUfChave,
            String origemCidadeChave,
            OffsetDateTime atualizadoEm) {
        jdbcTemplate.update("""
                INSERT INTO agregado_visualizacao_diaria (
                  id, anuncio_id, data_referencia, origem_uf, origem_cidade,
                  origem_uf_chave, origem_cidade_chave, total_visualizacoes,
                  visitantes_estimados, atualizado_em
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 1, 0, ?)
                ON CONFLICT (
                  anuncio_id, data_referencia, origem_uf_chave, origem_cidade_chave
                ) DO UPDATE SET
                  total_visualizacoes = agregado_visualizacao_diaria.total_visualizacoes + 1,
                  atualizado_em = EXCLUDED.atualizado_em
                """,
                id,
                anuncioId,
                dataReferencia,
                origemUf,
                origemCidade,
                origemUfChave,
                origemCidadeChave,
                atualizadoEm);
    }

    public void incrementarCliqueDiario(
            UUID id,
            UUID anuncioId,
            LocalDate dataReferencia,
            String origemUf,
            String origemCidade,
            String origemUfChave,
            String origemCidadeChave,
            OffsetDateTime atualizadoEm) {
        jdbcTemplate.update("""
                INSERT INTO agregado_clique_whatsapp_diario (
                  id, anuncio_id, data_referencia, origem_uf, origem_cidade,
                  origem_uf_chave, origem_cidade_chave, total_cliques,
                  visitantes_estimados, atualizado_em
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 1, 0, ?)
                ON CONFLICT (
                  anuncio_id, data_referencia, origem_uf_chave, origem_cidade_chave
                ) DO UPDATE SET
                  total_cliques = agregado_clique_whatsapp_diario.total_cliques + 1,
                  atualizado_em = EXCLUDED.atualizado_em
                """,
                id,
                anuncioId,
                dataReferencia,
                origemUf,
                origemCidade,
                origemUfChave,
                origemCidadeChave,
                atualizadoEm);
    }
}
