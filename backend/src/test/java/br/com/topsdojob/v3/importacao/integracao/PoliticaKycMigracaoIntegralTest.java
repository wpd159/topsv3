package br.com.topsdojob.v3.importacao.integracao;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.DocumentoKycLegado;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Decisao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Destino;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.EntidadeTipo;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.EstadoModeracao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Finalidade;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Item;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Origem;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.TipoMidia;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.TipoOrigem;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Visibilidade;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PoliticaKycMigracaoIntegralTest {

  private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-08-01T12:00:00Z");

  @Test
  void consolidaDuplicataDoMesmoUsuarioEPreservaValidadoMaisRecentePorPrioridade() {
    DocumentoKycLegado validado = documento(
        "doc-validado", "envio-validado", "usuario-a", "midia-validada", "VALIDADO",
        AGORA.minusDays(10));
    DocumentoKycLegado pendenteMaisNovo = documento(
        "doc-pendente", "envio-pendente", "usuario-a", "midia-pendente", "PENDENTE",
        AGORA.minusDays(1));
    DocumentoKycLegado outroUsuario = documento(
        "doc-outro", "envio-outro", "usuario-b", "midia-outro", "VALIDADO",
        AGORA.minusDays(2));
    ManifestoMidiaFaseCinco manifesto = new ManifestoMidiaFaseCinco(List.of(
        item("midia-validada", "usuario-a"),
        item("midia-pendente", "usuario-a"),
        item("midia-outro", "usuario-b")));

    var resultado = new PoliticaKycMigracaoIntegral().consolidar(
        List.of(pendenteMaisNovo, outroUsuario, validado), manifesto);

    assertThat(resultado.canonicos())
        .extracting(DocumentoKycLegado::idOrigem)
        .containsExactly("doc-validado", "doc-outro");
    assertThat(resultado.historicosConsolidados())
        .extracting(DocumentoKycLegado::idOrigem)
        .containsExactly("doc-pendente");
    assertThat(resultado.quarentena()).isEmpty();
    assertThat(resultado.usuariosAfetados()).isEmpty();
  }

  @Test
  void quarentenaReferenciaCruzadaSemCompartilharDocumentoEBloquearDemaisUsuarios() {
    DocumentoKycLegado validoDoUsuarioAfetado = documento(
        "doc-valido-a", "envio-valido-a", "usuario-a", "midia-valida-a", "VALIDADO",
        AGORA.minusDays(3));
    DocumentoKycLegado origemCorreta = documento(
        "doc-cruzado-a", "envio-a", "usuario-a", "midia-cruzada", "VALIDADO",
        AGORA.minusDays(2));
    DocumentoKycLegado vinculoCruzado = documento(
        "doc-cruzado-b", "envio-b", "usuario-b", "midia-cruzada", "VALIDADO",
        AGORA.minusDays(1));
    DocumentoKycLegado independente = documento(
        "doc-independente", "envio-c", "usuario-c", "midia-independente", "VALIDADO",
        AGORA);
    ManifestoMidiaFaseCinco manifesto = new ManifestoMidiaFaseCinco(List.of(
        item("midia-valida-a", "usuario-a"),
        item("midia-cruzada", "usuario-a"),
        item("midia-independente", "usuario-c")));

    var resultado = new PoliticaKycMigracaoIntegral().consolidar(
        List.of(validoDoUsuarioAfetado, origemCorreta, vinculoCruzado, independente), manifesto);

    assertThat(resultado.quarentena())
        .extracting(DocumentoKycLegado::idOrigem)
        .containsExactly("doc-cruzado-a", "doc-valido-a", "doc-cruzado-b");
    assertThat(resultado.codigoQuarentena(origemCorreta))
        .isEqualTo("KYC_OWNERSHIP_CRUZADO");
    assertThat(resultado.codigoQuarentena(validoDoUsuarioAfetado))
        .isEqualTo("KYC_USUARIO_AFETADO_VINCULO_CRUZADO");
    assertThat(resultado.usuariosAfetados()).containsExactlyInAnyOrder("usuario-a", "usuario-b");
    assertThat(resultado.canonicos())
        .extracting(DocumentoKycLegado::idOrigem)
        .containsExactly("doc-independente");
    assertThat(resultado.manifestoSeguro().itens())
        .filteredOn(item -> item.idOrigem().equals("midia-cruzada"))
        .singleElement()
        .satisfies(item -> {
          assertThat(item.decisao()).isEqualTo(Decisao.QUARENTENA);
          assertThat(item.destino()).isNull();
          assertThat(item.motivo()).isEqualTo("KYC_OWNERSHIP_CRUZADO");
        });
  }

  private static DocumentoKycLegado documento(
      String id,
      String envio,
      String usuario,
      String item,
      String status,
      OffsetDateTime atualizadoEm) {
    return new DocumentoKycLegado(
        id,
        envio,
        usuario,
        item,
        "IDENTIDADE",
        "UNICO",
        status,
        atualizadoEm.minusHours(1),
        atualizadoEm,
        null,
        null,
        null);
  }

  private static Item item(String id, String proprietario) {
    return new Item(
        id,
        EntidadeTipo.KYC,
        id,
        null,
        proprietario,
        null,
        id,
        Finalidade.KYC_IDENTIDADE,
        TipoMidia.DOCUMENTO,
        Visibilidade.PRIVADA,
        EstadoModeracao.NAO_APLICAVEL,
        true,
        false,
        0,
        "image/jpeg",
        8,
        "a".repeat(64),
        new Origem(TipoOrigem.OBJECT_STORAGE, StorageArea.PRIVATE_DOCUMENT, "origem/" + id),
        new Destino(StorageArea.PRIVATE_DOCUMENT, "documentos-sinteticos", "destino/" + id),
        Decisao.IMPORTAR,
        null);
  }
}
