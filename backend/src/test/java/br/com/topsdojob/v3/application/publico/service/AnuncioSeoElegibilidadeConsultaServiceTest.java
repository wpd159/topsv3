package br.com.topsdojob.v3.application.publico.service;

import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.entity;
import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoMapper;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusDerivadoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnuncioSeoElegibilidadeConsultaServiceTest {

  private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-08-27T12:00:00Z");
  private static final String PREVIEW_KEY = "hml/midias-aprovadas/restritas-borradas/v1/teste.jpg";

  @Test
  void somentePreviewDisponivelEhElegivelEEstadosDesconhecidosFalhamFechado() {
    for (StatusDerivadoMidia status : StatusDerivadoMidia.values()) {
      Fixture fixture = fixture(1);
      aplicarEstado(fixture.arquivos().get(0), status);

      var resultado = fixture.service().avaliar(fixture.anuncios(), Map.of());

      assertThat(resultado.get(fixture.anuncios().get(0).getId()).indexavel())
          .as("estado %s", status)
          .isEqualTo(status == StatusDerivadoMidia.DISPONIVEL);
    }
  }

  @Test
  void milTrezentosEQuarentaEQuatroPreviewsUsamDuasConsultasEmLoteESemStorage() {
    Fixture fixture = fixture(1_344);
    fixture.arquivos().forEach(arquivo -> aplicarEstado(arquivo, StatusDerivadoMidia.DISPONIVEL));

    var resultado = fixture.service().avaliar(fixture.anuncios(), Map.of());

    assertThat(resultado).hasSize(1_344);
    assertThat(resultado.values()).allMatch(AnuncioSeoElegibilidadeConsultaService.Resultado::indexavel);
    verify(fixture.midiaRepository(), times(1)).findByAnuncioIdIn(any());
    verify(fixture.arquivoRepository(), times(1)).findByIdIn(any());
  }

  private Fixture fixture(int total) {
    AnuncioMidiaRepository midiaRepository = mock(AnuncioMidiaRepository.class);
    ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
    PremiumPublicoMapper premiumMapper = mock(PremiumPublicoMapper.class);
    AnuncioSeoIndexabilidadePolicy policy = mock(AnuncioSeoIndexabilidadePolicy.class);
    List<AnuncioEntity> anuncios = new ArrayList<>(total);
    List<AnuncioMidiaEntity> vinculos = new ArrayList<>(total);
    List<ArquivoMidiaEntity> arquivos = new ArrayList<>(total);

    for (int index = 0; index < total; index++) {
      UUID anuncioId = UUID.randomUUID();
      UUID arquivoId = UUID.randomUUID();
      AnuncioEntity anuncio = entity(AnuncioEntity.class);
      set(anuncio, "id", anuncioId);
      anuncios.add(anuncio);

      AnuncioMidiaEntity vinculo = entity(AnuncioMidiaEntity.class);
      set(vinculo, "id", UUID.randomUUID());
      set(vinculo, "anuncioId", anuncioId);
      set(vinculo, "arquivoMidiaId", arquivoId);
      set(vinculo, "tipo", TipoAnuncioMidia.FOTO);
      set(vinculo, "finalidade", FinalidadeAnuncioMidia.GALERIA);
      set(vinculo, "ordem", 0);
      set(vinculo, "status", StatusAnuncioMidia.PUBLICAVEL);
      set(vinculo, "visibilidadeMidia", VisibilidadeMidia.RESTRITA_18);
      vinculos.add(vinculo);

      ArquivoMidiaEntity arquivo = ArquivoMidiaEntity.criarUploadPendente(
          arquivoId,
          "R2",
          "privado",
          "hml/midias-pendentes/" + arquivoId + ".jpg",
          "foto.jpg",
          "image/jpeg",
          100L,
          100,
          100,
          null,
          "a".repeat(64),
          AGORA);
      arquivo.aplicarDecisao(StatusArquivoMidia.VALIDADO);
      arquivos.add(arquivo);
    }

    when(midiaRepository.findByAnuncioIdIn(any())).thenReturn(vinculos);
    when(arquivoRepository.findByIdIn(any())).thenReturn(arquivos);
    when(premiumMapper.flagsPorAnuncios(any())).thenReturn(Map.of());
    when(policy.indexavel(any(), any(), anyBoolean()))
        .thenAnswer(invocation -> invocation.getArgument(2));

    return new Fixture(
        new AnuncioSeoElegibilidadeConsultaService(
            midiaRepository, arquivoRepository, premiumMapper, policy),
        anuncios,
        arquivos,
        midiaRepository,
        arquivoRepository);
  }

  private void aplicarEstado(ArquivoMidiaEntity arquivo, StatusDerivadoMidia status) {
    switch (status) {
      case DESCONHECIDO -> arquivo.marcarPreviewRestritoDesconhecido(PREVIEW_KEY, "v1");
      case PENDENTE -> arquivo.marcarPreviewRestritoPendente(PREVIEW_KEY, "v1");
      case DISPONIVEL -> arquivo.marcarPreviewRestritoDisponivel(PREVIEW_KEY, "v1", AGORA);
      case FALHA -> arquivo.marcarPreviewRestritoFalha(PREVIEW_KEY, "v1");
      case REMOVIDO -> {
        arquivo.marcarPreviewRestritoDisponivel(PREVIEW_KEY, "v1", AGORA);
        arquivo.marcarPreviewRestritoRemovido();
      }
    }
  }

  private record Fixture(
      AnuncioSeoElegibilidadeConsultaService service,
      List<AnuncioEntity> anuncios,
      List<ArquivoMidiaEntity> arquivos,
      AnuncioMidiaRepository midiaRepository,
      ArquivoMidiaRepository arquivoRepository) {
  }
}
