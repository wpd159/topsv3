package br.com.topsdojob.v3.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

class AnuncioRepositorySeoPublicoQueryTest {

    @Test
    void listagensPublicasUsamPrioridadeAtivaEAleatoriedadePaginavelNoBanco() throws Exception {
        Method categoria = AnuncioRepository.class.getMethod(
                "findPublicosOrdenados",
                String.class,
                String.class,
                java.util.UUID.class,
                java.time.OffsetDateTime.class,
                long.class,
                org.springframework.data.domain.Pageable.class);
        Method localidade = AnuncioRepository.class.getMethod(
                "findPublicosPorLocalidadeOrdenados",
                java.util.UUID.class,
                java.util.UUID.class,
                java.util.UUID.class,
                java.time.OffsetDateTime.class,
                long.class,
                org.springframework.data.domain.Pageable.class);

        Query categoriaQuery = categoria.getAnnotation(Query.class);
        Query localidadeQuery = localidade.getAnnotation(Query.class);
        assertQueryCanonica(categoriaQuery.value());
        assertQueryCanonica(localidadeQuery.value());
        assertProprietarioAtivo(categoriaQuery.countQuery());
        assertProprietarioAtivo(localidadeQuery.countQuery());
        assertThat(categoriaQuery.countQuery())
                .contains(":categoria = 'VENDA_DE_CONTEUDO'")
                .contains("from anuncio_servicos av")
                .contains("av.servico = 'VIDEOCHAMADA'")
                .contains("a.categoria = :categoria")
                .contains("a.atendimento_exclusivamente_virtual = false")
                .contains(":busca is null")
                .contains(":usuarioId is null or a.usuario_id = :usuarioId")
                .doesNotContain("join anuncio_servicos")
                .doesNotContain("hashtextextended")
                .doesNotContain("order by");
        assertThat(localidadeQuery.countQuery())
                .contains("a.atendimento_exclusivamente_virtual = false")
                .contains("l.estado_id = :estadoId")
                .contains(":cidadeId is null or l.cidade_id = :cidadeId")
                .contains(":bairroId is null or l.bairro_id = :bairroId")
                .doesNotContain("hashtextextended")
                .doesNotContain("order by");
    }

    private void assertQueryCanonica(String query) {
        assertThat(query)
                .contains("a.status = 'PUBLICADO'")
                .contains("a.removido_em is null")
                .contains("bp.codigo = 'ANUNCIO_TOPO'")
                .contains("bp.afeta_ranking = true")
                .contains("ab.status = 'ATIVA'")
                .contains("ab.revogada_em is null")
                .contains("ab.inicio_em <= :agora")
                .contains("ab.fim_em > :agora")
                .contains("gb.status = 'ATIVO'")
                .contains("gb.validade_fim_em > :agora")
                .contains("hashtextextended(a.id::text, :seed)")
                .contains("a.id")
                .doesNotContain("OCULTAR_IDADE")
                .doesNotContain("FOTOS_EXTRA_5")
                .doesNotContain("WHATSAPP_CARD")
                .doesNotContain("a.publicado_em desc")
                .doesNotContain("order by a.id");
        assertProprietarioAtivo(query);
    }

    private void assertProprietarioAtivo(String query) {
        assertThat(query)
                .contains("join usuario u on u.id = a.usuario_id")
                .contains("u.status = 'ATIVO'")
                .contains("u.tipo_conta = 'ANUNCIANTE'")
                .contains("u.desativado_em is null")
                .contains("u.excluido_em is null");
    }
}
