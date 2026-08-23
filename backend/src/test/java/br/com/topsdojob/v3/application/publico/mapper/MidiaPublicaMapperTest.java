package br.com.topsdojob.v3.application.publico.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.service.MidiaPublicaUrlService;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MidiaPublicaMapperTest {

    private MidiaPublicaUrlService urlService;
    private MidiaPublicaMapper mapper;

    @BeforeEach
    void setUp() {
        urlService = mock(MidiaPublicaUrlService.class);
        mapper = new MidiaPublicaMapper(urlService);
    }

    @Test
    void fotoLivreEhAutorizadaSemIdade() {
        UUID arquivoId = UUID.randomUUID();
        AnuncioMidiaEntity midia = midia(TipoAnuncioMidia.FOTO, VisibilidadeMidia.LIVRE, arquivoId, 0);
        ArquivoMidiaEntity arquivo = arquivo(arquivoId);
        when(urlService.resolver(midia, arquivo)).thenReturn(new MidiaPublicaUrlService.ResultadoUrlPublica("/foto-livre", null));

        var result = mapper.publicas(List.of(midia), Map.of(arquivoId, arquivo), false);

        assertThat(result).singleElement().satisfies(dto -> {
            assertThat(dto.visibilidadeMidia()).isEqualTo("LIVRE");
            assertThat(dto.autorizada()).isTrue();
            assertThat(dto.urlPublica()).isEqualTo("/foto-livre");
        });
    }

    @Test
    void fotoRestritaNaoEntregaOriginalSemIdade() {
        UUID arquivoId = UUID.randomUUID();
        AnuncioMidiaEntity midia = midia(TipoAnuncioMidia.FOTO, VisibilidadeMidia.RESTRITA_18, arquivoId, 0);
        ArquivoMidiaEntity arquivo = arquivo(arquivoId);
        when(urlService.resolverPreviewRestrita(arquivo))
                .thenReturn(new MidiaPublicaUrlService.ResultadoUrlPublica("/restritas-borradas/segura.jpg", null));

        var result = mapper.publicas(List.of(midia), Map.of(arquivoId, arquivo), false);

        assertThat(result).singleElement().satisfies(dto -> {
            assertThat(dto.autorizada()).isFalse();
            assertThat(dto.urlPublica()).isNull();
            assertThat(dto.previewUrl()).isEqualTo("/restritas-borradas/segura.jpg");
            assertThat(dto.pendenciaMidia()).isEqualTo("MIDIA_RESTRITA_IDADE");
        });
    }

    @Test
    void fotoRestritaEntregaSomenteAposIdadeValida() {
        UUID arquivoId = UUID.randomUUID();
        AnuncioMidiaEntity midia = midia(TipoAnuncioMidia.FOTO, VisibilidadeMidia.RESTRITA_18, arquivoId, 0);
        ArquivoMidiaEntity arquivo = arquivo(arquivoId);
        when(urlService.resolverPreviewRestrita(arquivo))
                .thenReturn(new MidiaPublicaUrlService.ResultadoUrlPublica("/restritas-borradas/segura.jpg", null));
        when(urlService.resolver(midia, arquivo)).thenReturn(new MidiaPublicaUrlService.ResultadoUrlPublica("/foto-autorizada", null));

        assertThat(mapper.publicas(List.of(midia), Map.of(arquivoId, arquivo), true))
                .singleElement().satisfies(dto -> {
                    assertThat(dto.urlPublica()).isEqualTo("/foto-autorizada");
                    assertThat(dto.previewUrl()).isEqualTo("/restritas-borradas/segura.jpg");
                });
    }

    @Test
    void falhaDaDerivacaoMantemOriginalAusenteEExplicitaPendencia() {
        UUID arquivoId = UUID.randomUUID();
        AnuncioMidiaEntity midia = midia(
                TipoAnuncioMidia.FOTO,
                VisibilidadeMidia.RESTRITA_18,
                arquivoId,
                0);
        ArquivoMidiaEntity arquivo = arquivo(arquivoId);
        when(urlService.resolverPreviewRestrita(arquivo))
                .thenReturn(new MidiaPublicaUrlService.ResultadoUrlPublica(
                        null,
                        "PENDENTE_DERIVACAO_RESTRITA"));

        assertThat(mapper.publicas(List.of(midia), Map.of(arquivoId, arquivo), false))
                .singleElement().satisfies(dto -> {
                    assertThat(dto.urlPublica()).isNull();
                    assertThat(dto.previewUrl()).isNull();
                    assertThat(dto.pendenciaMidia()).isEqualTo("PENDENTE_DERIVACAO_RESTRITA");
                });
    }

    @Test
    void midiaPendenteOuRejeitadaNaoEhPublicada() {
        UUID arquivoId = UUID.randomUUID();
        AnuncioMidiaEntity pendente = midia(TipoAnuncioMidia.FOTO, null, arquivoId, 0);
        when(pendente.getStatus()).thenReturn(StatusAnuncioMidia.PENDENTE);
        AnuncioMidiaEntity rejeitada = midia(TipoAnuncioMidia.FOTO, VisibilidadeMidia.RESTRITA_18, arquivoId, 1);
        when(rejeitada.getStatus()).thenReturn(StatusAnuncioMidia.REJEITADA);

        assertThat(mapper.publicas(List.of(pendente, rejeitada), Map.of(arquivoId, arquivo(arquivoId)), true)).isEmpty();
    }

    @Test
    void limitaFotosDepoisDeOrdenarEPreservaAsPrimeiras() {
        UUID arquivoZero = UUID.randomUUID();
        UUID arquivoUm = UUID.randomUUID();
        UUID arquivoDois = UUID.randomUUID();
        AnuncioMidiaEntity zero = midia(TipoAnuncioMidia.FOTO, VisibilidadeMidia.LIVRE, arquivoZero, 0);
        AnuncioMidiaEntity um = midia(TipoAnuncioMidia.FOTO, VisibilidadeMidia.LIVRE, arquivoUm, 1);
        AnuncioMidiaEntity dois = midia(TipoAnuncioMidia.FOTO, VisibilidadeMidia.LIVRE, arquivoDois, 2);
        when(urlService.resolver(any(), any())).thenReturn(
                new MidiaPublicaUrlService.ResultadoUrlPublica("/segura", null));

        var resultado = mapper.publicas(
                List.of(dois, zero, um),
                Map.of(arquivoZero, arquivo(arquivoZero), arquivoUm, arquivo(arquivoUm), arquivoDois, arquivo(arquivoDois)),
                false,
                2);

        assertThat(resultado).extracting(item -> item.ordem()).containsExactly(0, 1);
    }

    @Test
    void videoExpiradoPermaneceArmazenadoMasSaiDaRespostaPublica() {
        UUID arquivoId = UUID.randomUUID();
        AnuncioMidiaEntity video = midia(TipoAnuncioMidia.VIDEO, VisibilidadeMidia.RESTRITA_18, arquivoId, 0);
        ArquivoMidiaEntity arquivo = arquivo(arquivoId);
        when(arquivo.getMimeType()).thenReturn("video/mp4");
        when(urlService.resolver(video, arquivo)).thenReturn(
                new MidiaPublicaUrlService.ResultadoUrlPublica("/video-restrito", null));

        var semBeneficio = mapper.publicas(
                List.of(video),
                Map.of(arquivoId, arquivo),
                true,
                4,
                false);
        var comBeneficio = mapper.publicas(
                List.of(video),
                Map.of(arquivoId, arquivo),
                true,
                4,
                true);

        assertThat(semBeneficio).isEmpty();
        assertThat(comBeneficio).hasSize(1);
    }

    @Test
    void videoComOrdemMaiorQueFotoAindaOcupaAPrimeiraPosicao() {
        UUID arquivoFoto = UUID.randomUUID();
        UUID arquivoVideo = UUID.randomUUID();
        AnuncioMidiaEntity foto = midia(TipoAnuncioMidia.FOTO, VisibilidadeMidia.LIVRE, arquivoFoto, 0);
        AnuncioMidiaEntity video = midia(TipoAnuncioMidia.VIDEO, VisibilidadeMidia.LIVRE, arquivoVideo, 10);
        when(urlService.resolver(any(), any())).thenReturn(
                new MidiaPublicaUrlService.ResultadoUrlPublica("/segura", null));

        var resultado = mapper.publicas(
                List.of(foto, video),
                Map.of(arquivoFoto, arquivo(arquivoFoto), arquivoVideo, arquivo(arquivoVideo)),
                false);

        assertThat(resultado).extracting(item -> item.tipo()).containsExactly("VIDEO", "FOTO");
        assertThat(resultado).extracting(item -> item.ordem()).containsExactly(10, 0);
    }

    @Test
    void videoComOrdemMenorQueFotoMantemAOrdenacaoCanonica() {
        UUID arquivoFoto = UUID.randomUUID();
        UUID arquivoVideo = UUID.randomUUID();
        AnuncioMidiaEntity foto = midia(TipoAnuncioMidia.FOTO, VisibilidadeMidia.LIVRE, arquivoFoto, 9);
        AnuncioMidiaEntity video = midia(TipoAnuncioMidia.VIDEO, VisibilidadeMidia.LIVRE, arquivoVideo, 1);
        when(urlService.resolver(any(), any())).thenReturn(
                new MidiaPublicaUrlService.ResultadoUrlPublica("/segura", null));

        var resultado = mapper.publicas(
                List.of(foto, video),
                Map.of(arquivoFoto, arquivo(arquivoFoto), arquivoVideo, arquivo(arquivoVideo)),
                false);

        assertThat(resultado).extracting(item -> item.tipo()).containsExactly("VIDEO", "FOTO");
    }

    @Test
    void ordemNulaFicaNoFimDoRespectivoGrupo() {
        UUID videoComOrdemId = UUID.randomUUID();
        UUID videoSemOrdemId = UUID.randomUUID();
        UUID fotoComOrdemId = UUID.randomUUID();
        UUID fotoSemOrdemId = UUID.randomUUID();
        AnuncioMidiaEntity videoComOrdem = midia(TipoAnuncioMidia.VIDEO, VisibilidadeMidia.LIVRE, videoComOrdemId, 2);
        AnuncioMidiaEntity videoSemOrdem = midia(TipoAnuncioMidia.VIDEO, VisibilidadeMidia.LIVRE, videoSemOrdemId, null);
        AnuncioMidiaEntity fotoComOrdem = midia(TipoAnuncioMidia.FOTO, VisibilidadeMidia.LIVRE, fotoComOrdemId, 1);
        AnuncioMidiaEntity fotoSemOrdem = midia(TipoAnuncioMidia.FOTO, VisibilidadeMidia.LIVRE, fotoSemOrdemId, null);
        when(urlService.resolver(any(), any())).thenReturn(
                new MidiaPublicaUrlService.ResultadoUrlPublica("/segura", null));

        var resultado = mapper.publicas(
                List.of(fotoSemOrdem, videoSemOrdem, fotoComOrdem, videoComOrdem),
                Map.of(
                        videoComOrdemId, arquivo(videoComOrdemId),
                        videoSemOrdemId, arquivo(videoSemOrdemId),
                        fotoComOrdemId, arquivo(fotoComOrdemId),
                        fotoSemOrdemId, arquivo(fotoSemOrdemId)),
                false);

        assertThat(resultado).extracting(item -> item.tipo()).containsExactly("VIDEO", "VIDEO", "FOTO", "FOTO");
        assertThat(resultado).extracting(item -> item.ordem()).containsExactly(2, null, 1, null);
    }

    @Test
    void doisVideosAntecedemVariasFotosSemConsumirOLimiteDeFotos() {
        UUID videoZeroId = UUID.randomUUID();
        UUID videoUmId = UUID.randomUUID();
        UUID fotoZeroId = UUID.randomUUID();
        UUID fotoUmId = UUID.randomUUID();
        UUID fotoDoisId = UUID.randomUUID();
        AnuncioMidiaEntity videoZero = midia(TipoAnuncioMidia.VIDEO, VisibilidadeMidia.LIVRE, videoZeroId, 0);
        AnuncioMidiaEntity videoUm = midia(TipoAnuncioMidia.VIDEO, VisibilidadeMidia.LIVRE, videoUmId, 1);
        AnuncioMidiaEntity fotoZero = midia(TipoAnuncioMidia.FOTO, VisibilidadeMidia.LIVRE, fotoZeroId, 0);
        AnuncioMidiaEntity fotoUm = midia(TipoAnuncioMidia.FOTO, VisibilidadeMidia.LIVRE, fotoUmId, 1);
        AnuncioMidiaEntity fotoDois = midia(TipoAnuncioMidia.FOTO, VisibilidadeMidia.LIVRE, fotoDoisId, 2);
        when(urlService.resolver(any(), any())).thenReturn(
                new MidiaPublicaUrlService.ResultadoUrlPublica("/segura", null));

        var resultado = mapper.publicas(
                List.of(fotoDois, videoUm, fotoZero, videoZero, fotoUm),
                Map.of(
                        videoZeroId, arquivo(videoZeroId),
                        videoUmId, arquivo(videoUmId),
                        fotoZeroId, arquivo(fotoZeroId),
                        fotoUmId, arquivo(fotoUmId),
                        fotoDoisId, arquivo(fotoDoisId)),
                false,
                2,
                true);

        assertThat(resultado).extracting(item -> item.tipo()).containsExactly("VIDEO", "VIDEO", "FOTO", "FOTO");
        assertThat(resultado).extracting(item -> item.ordem()).containsExactly(0, 1, 0, 1);
    }

    @Test
    void desempataMidiasDoMesmoTipoEOrdemPeloId() {
        UUID primeiroId = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID segundoId = UUID.fromString("00000000-0000-4000-8000-000000000002");
        UUID primeiroArquivoId = UUID.randomUUID();
        UUID segundoArquivoId = UUID.randomUUID();
        AnuncioMidiaEntity primeiro = midia(primeiroId, TipoAnuncioMidia.VIDEO, VisibilidadeMidia.LIVRE, primeiroArquivoId, 5);
        AnuncioMidiaEntity segundo = midia(segundoId, TipoAnuncioMidia.VIDEO, VisibilidadeMidia.LIVRE, segundoArquivoId, 5);
        when(urlService.resolver(any(), any())).thenReturn(
                new MidiaPublicaUrlService.ResultadoUrlPublica("/segura", null));

        var resultado = mapper.publicas(
                List.of(segundo, primeiro),
                Map.of(primeiroArquivoId, arquivo(primeiroArquivoId), segundoArquivoId, arquivo(segundoArquivoId)),
                false);

        assertThat(resultado).extracting(item -> item.id()).containsExactly(primeiroId, segundoId);
    }

    @Test
    void storyPermaneceForaDaGaleriaPublica() {
        UUID arquivoId = UUID.randomUUID();
        AnuncioMidiaEntity story = midia(TipoAnuncioMidia.STORY, VisibilidadeMidia.RESTRITA_18, arquivoId, 0);

        assertThat(mapper.publicas(List.of(story), Map.of(arquivoId, arquivo(arquivoId)), true)).isEmpty();
    }

    @Test
    void ordenacaoNaoAlteraAOrdemPersistida() {
        UUID arquivoFoto = UUID.randomUUID();
        UUID arquivoVideo = UUID.randomUUID();
        AnuncioMidiaEntity foto = midia(TipoAnuncioMidia.FOTO, VisibilidadeMidia.LIVRE, arquivoFoto, 0);
        AnuncioMidiaEntity video = midia(TipoAnuncioMidia.VIDEO, VisibilidadeMidia.LIVRE, arquivoVideo, 10);
        when(urlService.resolver(any(), any())).thenReturn(
                new MidiaPublicaUrlService.ResultadoUrlPublica("/segura", null));

        mapper.publicas(
                List.of(foto, video),
                Map.of(arquivoFoto, arquivo(arquivoFoto), arquivoVideo, arquivo(arquivoVideo)),
                false);

        verify(foto, never()).reordenar(any(), any());
        verify(video, never()).reordenar(any(), any());
        assertThat(foto.getOrdem()).isZero();
        assertThat(video.getOrdem()).isEqualTo(10);
    }

    private AnuncioMidiaEntity midia(TipoAnuncioMidia tipo, VisibilidadeMidia visibilidade, UUID arquivoId, Integer ordem) {
        return midia(UUID.randomUUID(), tipo, visibilidade, arquivoId, ordem);
    }

    private AnuncioMidiaEntity midia(
            UUID id,
            TipoAnuncioMidia tipo,
            VisibilidadeMidia visibilidade,
            UUID arquivoId,
            Integer ordem) {
        AnuncioMidiaEntity entity = mock(AnuncioMidiaEntity.class);
        when(entity.getId()).thenReturn(id);
        when(entity.getArquivoMidiaId()).thenReturn(arquivoId);
        when(entity.getTipo()).thenReturn(tipo);
        when(entity.getFinalidade()).thenReturn(tipo == TipoAnuncioMidia.STORY ? FinalidadeAnuncioMidia.STORY : FinalidadeAnuncioMidia.GALERIA);
        when(entity.getStatus()).thenReturn(StatusAnuncioMidia.PUBLICAVEL);
        when(entity.getVisibilidadeMidia()).thenReturn(visibilidade);
        when(entity.getOrdem()).thenReturn(ordem);
        return entity;
    }

    private ArquivoMidiaEntity arquivo(UUID id) {
        ArquivoMidiaEntity entity = mock(ArquivoMidiaEntity.class);
        when(entity.getId()).thenReturn(id);
        when(entity.getStatusArquivo()).thenReturn(StatusArquivoMidia.VALIDADO);
        when(entity.getMimeType()).thenReturn("image/webp");
        return entity;
    }
}
