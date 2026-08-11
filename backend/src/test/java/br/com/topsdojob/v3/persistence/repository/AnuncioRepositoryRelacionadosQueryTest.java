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
    void consultaRelacionadosAplicaBeneficioVigenteSemOrigemOuPagamentoNoBanco() throws Exception {
        Method method = AnuncioRepository.class.getMethod(
                "findRelacionadosComBeneficioVigente",
                UUID.class,
                String.class,
                UUID.class,
                boolean.class,
                OffsetDateTime.class,
                Pageable.class);
        String query = method.getAnnotation(Query.class).value();
        String elegibilidade = query.substring(0, query.indexOf("order by"));
        String ordenacao = query.substring(query.indexOf("order by"));

        assertThat(elegibilidade)
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
                .contains("ab.anuncio_id = a.id")
                .contains("ab.usuario_id = a.usuario_id")
                .contains("gb.anuncio_id = a.id")
                .contains("gb.usuario_id = a.usuario_id")
                .contains("ab.origem = gb.origem")
                .contains("bp.ativo = true")
                .contains("bp.escopo = 'ANUNCIO'")
                .contains("ab.status = 'ATIVA'")
                .contains("ab.revogada_em is null")
                .contains("ab.inicio_em <= :agora")
                .contains("ab.fim_em > :agora")
                .contains("gb.status = 'ATIVO'")
                .contains("gb.validade_inicio_em <= :agora")
                .contains("gb.validade_fim_em > :agora")
                .doesNotContain("bp.codigo in")
                .doesNotContain("movimento_credito")
                .doesNotContain("preco_snapshot")
                .doesNotContain("custo_creditos_snapshot")
                .doesNotContain("ab.origem = 'COMPRA'")
                .doesNotContain("ab.origem = 'CREDITO'")
                .doesNotContain("ab.origem = 'ADMIN'")
                .doesNotContain("ab.origem = 'CAMPANHA'");

        assertThat(ordenacao)
                .contains("beneficio_topo.codigo = 'ANUNCIO_TOPO'")
                .contains("a.publicado_em desc")
                .contains("a.id");
    }
}
