package br.com.topsdojob.v3.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

class AnuncioRepositoryUsuarioPublicoQueryTest {

  @Test
  void consultaCanonicaAceitaFiltroOpcionalDoUsuarioSemPoliticaParalela() throws Exception {
    Method method = AnuncioRepository.class.getMethod(
        "findPublicosOrdenados",
        String.class,
        String.class,
        UUID.class,
        OffsetDateTime.class,
        long.class,
        Pageable.class);
    Query annotation = method.getAnnotation(Query.class);

    assertFiltroCanonicoDoUsuario(annotation.value());
    assertFiltroCanonicoDoUsuario(annotation.countQuery());
    assertThat(annotation.value())
        .contains("hashtextextended(a.id::text, :seed)")
        .contains("bp.codigo = 'ANUNCIO_TOPO'")
        .contains("ab.status = 'ATIVA'");
    assertThat(annotation.countQuery())
        .doesNotContain("hashtextextended")
        .doesNotContain("order by");
    assertThat(AnuncioRepository.class.getDeclaredMethods())
        .extracting(Method::getName)
        .doesNotContain("findPublicosPorUsuarioOrdenados");
  }

  private void assertFiltroCanonicoDoUsuario(String query) {
    assertThat(query)
        .contains(":usuarioId is null or a.usuario_id = :usuarioId")
        .contains("a.status = 'PUBLICADO'")
        .contains("a.status_moderacao = 'APROVADO'")
        .contains("a.removido_em is null")
        .contains(":categoria is null")
        .contains(":busca is null")
        .doesNotContain("documento_busca_anuncio")
        .doesNotContain("anuncio_bloqueio_juridico")
        .doesNotContain("join usuario u")
        .doesNotContain("email_normalizado")
        .doesNotContain("telefone_normalizado")
        .doesNotContain("cpf_normalizado")
        .doesNotContain("nome_civil");
  }

  @Test
  void resolveUsernamePublicoCanonicoParaUmaUnicaContaAtivaSemExporDadosPessoais() throws Exception {
    Method method = AnuncioRepository.class.getMethod(
        "findUsuarioPublicoPorUsername",
        String.class);
    String query = method.getAnnotation(Query.class).value();

    assertThat(query)
        .contains("lower(btrim(u.nome)) = :username")
        .contains("u.status = 'ATIVO'")
        .contains("u.tipo_conta = 'ANUNCIANTE'")
        .doesNotContain("md5(")
        .doesNotContain("topsv3-public-user-v1")
        .doesNotContain("email_normalizado")
        .doesNotContain("telefone_normalizado")
        .doesNotContain("cpf_normalizado")
        .doesNotContain("nome_civil");
  }
}
