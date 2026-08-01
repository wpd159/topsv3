package br.com.topsdojob.v3.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

class AnuncioRepositoryRelacionadosQueryTest {

    @Test
    void consultaRelacionadosAplicaContratoPublicoComercialNoBanco() throws Exception {
        Method method = AnuncioRepository.class.getMethod(
                "findRelacionadosPagos",
                UUID.class,
                String.class,
                UUID.class,
                boolean.class,
                OffsetDateTime.class,
                Pageable.class);
        String query = method.getAnnotation(Query.class).value();

        assertThat(query)
                .contains("a.id <> :anuncioAtualId")
                .contains("a.categoria = :categoria")
                .contains("l.cidade_id = :cidadeId")
                .contains("l.cidade_id <> :cidadeId")
                .contains("a.status = 'PUBLICADO'")
                .contains("a.status_moderacao = 'APROVADO'")
                .contains("dba.status_publicacao = 'PUBLICAVEL'")
                .contains("dba.tem_midia_valida = true")
                .contains("u.status = 'ATIVO'")
                .contains("u.desativado_em is null")
                .contains("u.excluido_em is null")
                .contains("bloqueio.anuncio_desbloqueado_em is null")
                .contains("am.status = 'PUBLICAVEL'")
                .contains("am.visibilidade_midia is not null")
                .contains("arquivo.status_arquivo = 'VALIDADO'")
                .contains("ab.status = 'ATIVA'")
                .contains("ab.revogada_em is null")
                .contains("ab.inicio_em <= :agora")
                .contains("ab.fim_em > :agora")
                .contains("gb.status = 'ATIVO'")
                .contains("gb.validade_fim_em > :agora")
                .contains("ab.origem = 'COMPRA'")
                .contains("coalesce(ab.preco_snapshot, 0) > 0")
                .contains("ab.origem = 'CREDITO'")
                .contains("mc.tipo = 'SAIDA'")
                .contains("mc.direcao = 'DEBITO'")
                .contains("mc.origem = 'BENEFICIO'")
                .contains("mc.referencia_tipo = 'ATIVACAO_BENEFICIO'")
                .contains("mc.referencia_id = ab.id")
                .contains("bp.codigo in")
                .contains("'ANUNCIO_TOPO'")
                .contains("a.publicado_em desc")
                .contains("a.id")
                .doesNotContain("findAll")
                .doesNotContain("Math.random")
                .doesNotContain("CORTESIA'")
                .doesNotContain("CAMPANHA'")
                .doesNotContain("ADMIN'")
                .doesNotContain("IMPORTACAO'");
    }
}
