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

        assertQueryCanonica(categoria.getAnnotation(Query.class).value());
        assertQueryCanonica(localidade.getAnnotation(Query.class).value());
    }

    private void assertQueryCanonica(String query) {
        assertThat(query)
                .contains("bp.codigo = 'ANUNCIO_TOPO'")
                .contains("ab.status = 'ATIVA'")
                .contains("ab.fim_em > :agora")
                .contains("gb.status = 'ATIVO'")
                .contains("gb.validade_fim_em > :agora")
                .contains("hashtextextended(a.id::text, :seed)")
                .doesNotContain("a.publicado_em desc")
                .doesNotContain("order by a.id");
    }
}
