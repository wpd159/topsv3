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
  void filtraNoBackendSomenteAnunciosPublicosDoUsuarioResolvido() throws Exception {
    Method method = AnuncioRepository.class.getMethod(
        "findPublicosPorUsuarioOrdenados",
        UUID.class,
        OffsetDateTime.class,
        long.class,
        Pageable.class);
    Query annotation = method.getAnnotation(Query.class);

    assertQueryPublica(annotation.value());
    assertQueryPublica(annotation.countQuery());
    assertThat(annotation.value())
        .contains("hashtextextended(a.id::text, :seed)")
        .contains("bp.codigo = 'ANUNCIO_TOPO'")
        .contains("ab.status = 'ATIVA'");
    assertThat(annotation.countQuery())
        .doesNotContain("hashtextextended")
        .doesNotContain("order by");
  }

  private void assertQueryPublica(String query) {
    assertThat(query)
        .contains("join usuario u on u.id = a.usuario_id")
        .contains("a.usuario_id = :usuarioId")
        .contains("a.status = 'PUBLICADO'")
        .contains("a.status_moderacao = 'APROVADO'")
        .contains("a.publicado_em is not null")
        .contains("a.removido_em is null")
        .contains("u.status = 'ATIVO'")
        .contains("u.tipo_conta = 'ANUNCIANTE'")
        .contains("u.desativado_em is null")
        .contains("u.excluido_em is null")
        .contains("dba.status_publicacao = 'PUBLICAVEL'")
        .contains("dba.tem_midia_valida = true")
        .doesNotContain("lower(u.nome)")
        .doesNotContain("email_normalizado")
        .doesNotContain("telefone_normalizado")
        .doesNotContain("cpf_normalizado")
        .doesNotContain("nome_civil");
  }

  @Test
  void resolveTokenOpacoParaUmaUnicaContaAtivaSemExporDadosPessoais() throws Exception {
    Method method = AnuncioRepository.class.getMethod(
        "findUsuarioPublicoPorUsername",
        String.class);
    String query = method.getAnnotation(Query.class).value();

    assertThat(query)
        .contains("md5('topsv3-public-user-v1:' || u.id::text) = :username")
        .contains("u.status = 'ATIVO'")
        .contains("u.tipo_conta = 'ANUNCIANTE'")
        .doesNotContain("email_normalizado")
        .doesNotContain("telefone_normalizado")
        .doesNotContain("cpf_normalizado")
        .doesNotContain("nome_civil");
  }
}
