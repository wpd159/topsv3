package br.com.topsdojob.v3.importacao.integracao;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.importacao.integracao.ClassificadorUsuariosStaging.IdentidadeUsuario;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.ClassificacaoUsuarioStaging;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClassificadorUsuariosStagingTest {

  private static final OffsetDateTime CAPTURADO_EM =
      OffsetDateTime.parse("2026-08-01T12:00:00Z");

  @Test
  void classificaFotografiaCompletaSemCriarIdentidadeOuExporDadosBrutos() {
    List<IdentidadeUsuario> usuarios = List.of(
        identidade("usuario-a", null, null, null, "identidade-a"),
        identidade("usuario-b", null, null, null, "identidade-b"));
    List<IdentidadeUsuario> staging = List.of(
        identidade("usuario-a", null, null, null, null),
        identidade("staging-only", null, null, null, "identidade-c"),
        identidade("usuario-b", null, null, null, "identidade-a"),
        identidade("staging-sem-identidade", null, null, null, null),
        identidade("staging-duplicado-a", null, null, null, "identidade-b"),
        identidade("staging-duplicado-b", null, null, null, "identidade-b"));

    var resultado = new ClassificadorUsuariosStaging().classificar(
        usuarios, staging, CAPTURADO_EM);

    assertThat(resultado).hasSize(staging.size());
    assertThat(resultado).filteredOn(
        item -> item.classificacao() == ClassificacaoUsuarioStaging.CORRESPONDENCIA_CANONICA)
        .hasSize(2);
    assertThat(resultado).filteredOn(
        item -> item.classificacao() == ClassificacaoUsuarioStaging.STAGING_ONLY)
        .hasSize(1);
    assertThat(resultado).filteredOn(
        item -> item.classificacao() == ClassificacaoUsuarioStaging.AMBIGUO)
        .hasSize(1);
    assertThat(resultado).filteredOn(
        item -> item.classificacao() == ClassificacaoUsuarioStaging.SEM_IDENTIDADE)
        .hasSize(1);
    assertThat(resultado).filteredOn(
        item -> item.classificacao() == ClassificacaoUsuarioStaging.DUPLICADO)
        .hasSize(1);
    assertThat(resultado).allSatisfy(item -> {
      assertThat(item.fingerprintIdentidade()).matches("[0-9a-f]{64}");
      assertThat(item.toString())
          .doesNotContain("identidade-");
    });
  }

  @Test
  void resultadoIndependeDaOrdemFisicaDasLinhas() {
    List<IdentidadeUsuario> usuarios = List.of(
        identidade("usuario-a", null, null, null, "identidade-a"));
    List<IdentidadeUsuario> staging = List.of(
        identidade("staging-b", null, null, null, "identidade-b"),
        identidade("staging-a", null, null, null, "identidade-a"));

    var classificador = new ClassificadorUsuariosStaging();

    assertThat(classificador.classificar(usuarios, staging, CAPTURADO_EM))
        .isEqualTo(classificador.classificar(
            usuarios, List.of(staging.get(1), staging.get(0)), CAPTURADO_EM));
  }

  private static IdentidadeUsuario identidade(
      String id,
      String email,
      String cpf,
      String telefone,
      String username) {
    return new IdentidadeUsuario(id, email, cpf, telefone, username);
  }
}
