package br.com.topsdojob.v3.application.publico.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.service.MidiaPublicaUrlService;
import br.com.topsdojob.v3.application.publico.dto.MidiaPublicaDto;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @CsvSource({"4,false,false", "4,false,true", "4,true,false", "4,true,true",
            "10,false,false", "10,false,true", "10,true,false", "10,true,true"})
    void cardPreservaDtoDaGaleriaMaisPolicyComOraculoFixoDeLimitesVideosECarrossel(
            int limite, boolean videoPermitido, boolean carrossel) {
        var vinculos = new ArrayList<AnuncioMidiaEntity>();
        var arquivos = new LinkedHashMap<UUID, ArquivoMidiaEntity>();
        for (int ordem = 0; ordem <= 10; ordem++) {
            adicionar(vinculos, arquivos, idCard(100 + ordem), TipoAnuncioMidia.FOTO,
                    ordem == 0 || ordem == 2 ? VisibilidadeMidia.RESTRITA_18 : VisibilidadeMidia.LIVRE, ordem);
        }
        adicionar(vinculos, arquivos, idCard(201), TipoAnuncioMidia.VIDEO, VisibilidadeMidia.LIVRE, 9);
        adicionar(vinculos, arquivos, idCard(202), TipoAnuncioMidia.VIDEO, VisibilidadeMidia.RESTRITA_18, 0);
        java.util.Collections.reverse(vinculos);
        configurarUrlsPublicasEPreviews();
        var policy = new MidiaPublicaSeguraPolicy();
        // Card priority is free before restricted within each type, not gallery order.
        List<UUID> fotosEsperadas = limite == 4
                ? List.of(idCard(101), idCard(103), idCard(100), idCard(102))
                : List.of(idCard(101), idCard(103), idCard(104), idCard(105), idCard(106),
                        idCard(107), idCard(108), idCard(109), idCard(100), idCard(102));
        var idsEsperados = new ArrayList<UUID>();
        if (videoPermitido) {
            idsEsperados.add(idCard(201));
            if (carrossel) idsEsperados.add(idCard(202));
        }
        idsEsperados.addAll(carrossel ? fotosEsperadas : List.of(idCard(101)));
        var referencia = policy.paraCard(mapper.publicas(vinculos, arquivos, false, limite, videoPermitido),
                carrossel, videoPermitido);
        assertThat(referencia).extracting(MidiaPublicaDto::id).containsExactlyElementsOf(idsEsperados);
        clearInvocations(urlService);

        var resultado = mapper.publicasParaCard(vinculos, arquivos, limite, videoPermitido, carrossel, policy);

        assertThat(resultado).isEqualTo(referencia);
        assertThat(resultado).filteredOn(dto -> "RESTRITA_18".equals(dto.visibilidadeMidia()))
                .allSatisfy(dto -> {
                    assertThat(dto.autorizada()).isFalse();
                    assertThat(dto.urlPublica()).isNull();
                });
        verify(urlService, times(carrossel ? 2 : 0)).resolverPreviewRestrita(any());
        vinculos.forEach(vinculo -> verify(vinculo, never()).reordenar(any(), any()));
    }

    @Test
    void cardComSomenteFotoLivreExibidaNaoConsultaPreviewsDescartados() {
        var vinculos = new ArrayList<AnuncioMidiaEntity>();
        var arquivos = new LinkedHashMap<UUID, ArquivoMidiaEntity>();
        for (int ordem = 0; ordem < 4; ordem++) {
            adicionar(vinculos, arquivos, idCard(100 + ordem), TipoAnuncioMidia.FOTO,
                    ordem == 3 ? VisibilidadeMidia.LIVRE : VisibilidadeMidia.RESTRITA_18, ordem);
        }
        configurarUrlsPublicasEPreviews();
        doThrow(new AssertionError("preview descartado pelo card nao pode ser consultado"))
                .when(urlService).resolverPreviewRestrita(any());

        assertThat(mapper.publicasParaCard(vinculos, arquivos, 4, false, false, new MidiaPublicaSeguraPolicy()))
                .singleElement().satisfies(dto -> {
                    assertThat(dto.id()).isEqualTo(idCard(103));
                    assertThat(dto.autorizada()).isTrue();
                    assertThat(dto.urlPublica()).isEqualTo("/original/" + idCard(103));
                    assertThat(dto.previewUrl()).isNull();
                    assertThat(dto.pendenciaMidia()).isNull();
                });
        verify(urlService, never()).resolverPreviewRestrita(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 10})
    void cardNaoPromoveFotoLivrePosteriorAosSlotsInvalidosOuSemArquivo(int limite) {
        var vinculos = new ArrayList<AnuncioMidiaEntity>();
        var arquivos = new LinkedHashMap<UUID, ArquivoMidiaEntity>();
        for (int ordem = 0; ordem <= limite; ordem++) {
            boolean livre = ordem == 1 || ordem == 2 || ordem == limite;
            adicionar(vinculos, arquivos, idCard(1000 + ordem), TipoAnuncioMidia.FOTO,
                    livre ? VisibilidadeMidia.LIVRE : VisibilidadeMidia.RESTRITA_18, ordem);
        }
        when(arquivos.get(idCard(1001)).getStatusArquivo()).thenReturn(StatusArquivoMidia.REMOVIDO);
        arquivos.remove(idCard(1002));
        java.util.Collections.reverse(vinculos);
        configurarUrlsPublicasEPreviews();
        var policy = new MidiaPublicaSeguraPolicy();
        var referencia = policy.paraCard(mapper.publicas(vinculos, arquivos, false, limite, false), true, false);
        List<UUID> idsEsperados = limite == 4
                ? List.of(idCard(1000), idCard(1003))
                : List.of(idCard(1000), idCard(1003), idCard(1004), idCard(1005),
                        idCard(1006), idCard(1007), idCard(1008), idCard(1009));
        assertThat(referencia).extracting(MidiaPublicaDto::id).containsExactlyElementsOf(idsEsperados);
        clearInvocations(urlService);

        var resultado = mapper.publicasParaCard(vinculos, arquivos, limite, false, true, policy);

        assertThat(resultado).isEqualTo(referencia);
        assertThat(resultado).allSatisfy(dto -> {
            assertThat(dto.autorizada()).isFalse();
            assertThat(dto.urlPublica()).isNull();
        });
        verify(urlService, never()).resolver(any(), any());
        verify(urlService, times(limite - 2)).resolverPreviewRestrita(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cardPreservaUuidComSinalOrdemNulaEPrioridadeLivreSemReordenarGaleria(boolean carrossel) {
        UUID negativoAlto = new UUID(Long.MIN_VALUE, 1);
        UUID negativoBaixo = new UUID(1, Long.MIN_VALUE);
        UUID positivo = new UUID(1, 1);
        UUID ordemNula = idCard(4);
        var vinculos = new ArrayList<AnuncioMidiaEntity>();
        var arquivos = new LinkedHashMap<UUID, ArquivoMidiaEntity>();
        adicionar(vinculos, arquivos, positivo, TipoAnuncioMidia.FOTO, VisibilidadeMidia.LIVRE, 0);
        adicionar(vinculos, arquivos, ordemNula, TipoAnuncioMidia.FOTO, VisibilidadeMidia.LIVRE, null);
        adicionar(vinculos, arquivos, negativoBaixo, TipoAnuncioMidia.FOTO, VisibilidadeMidia.LIVRE, 0);
        adicionar(vinculos, arquivos, negativoAlto, TipoAnuncioMidia.FOTO, VisibilidadeMidia.RESTRITA_18, 0);
        configurarUrlsPublicasEPreviews();
        var policy = new MidiaPublicaSeguraPolicy();
        var galeria = mapper.publicas(vinculos, arquivos, false, 4, false);
        assertThat(galeria).extracting(MidiaPublicaDto::id)
                .containsExactly(negativoAlto, negativoBaixo, positivo, ordemNula);
        var referencia = policy.paraCard(galeria, carrossel, false);
        List<UUID> idsEsperados = carrossel
                ? List.of(negativoBaixo, positivo, ordemNula, negativoAlto) : List.of(negativoBaixo);
        clearInvocations(urlService);

        var resultado = mapper.publicasParaCard(vinculos, arquivos, 4, false, carrossel, policy);

        assertThat(resultado).isEqualTo(referencia);
        assertThat(resultado).extracting(MidiaPublicaDto::id).containsExactlyElementsOf(idsEsperados);
        verify(urlService, times(carrossel ? 1 : 0)).resolverPreviewRestrita(any());
        assertThat(vinculos).extracting(AnuncioMidiaEntity::getOrdem).containsExactly(0, null, 0, 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"presente", "ausente", "retorno_nulo"})
    void cardRestritoPreservaPreviewPendenciaEBloqueioOriginalSemConsultarSegundaFoto(String resposta) {
        var vinculos = new ArrayList<AnuncioMidiaEntity>();
        var arquivos = new LinkedHashMap<UUID, ArquivoMidiaEntity>();
        adicionar(vinculos, arquivos, idCard(100), TipoAnuncioMidia.FOTO, VisibilidadeMidia.RESTRITA_18, 0);
        adicionar(vinculos, arquivos, idCard(101), TipoAnuncioMidia.FOTO, VisibilidadeMidia.RESTRITA_18, 1);
        when(urlService.resolverPreviewRestrita(any())).thenAnswer(call -> {
            if (resposta.equals("retorno_nulo")) return null;
            ArquivoMidiaEntity arquivo = call.getArgument(0);
            return new MidiaPublicaUrlService.ResultadoUrlPublica(
                    resposta.equals("presente") ? "/preview/" + arquivo.getId() : null,
                    resposta.equals("presente") ? null : "PENDENTE_DERIVACAO_RESTRITA");
        });
        var policy = new MidiaPublicaSeguraPolicy();
        var referencia = policy.paraCard(mapper.publicas(vinculos, arquivos, false, 4, false), false, false);
        clearInvocations(urlService);

        var resultado = mapper.publicasParaCard(vinculos, arquivos, 4, false, false, policy);

        assertThat(resultado).isEqualTo(referencia).singleElement().satisfies(dto -> {
            assertThat(dto.id()).isEqualTo(idCard(100));
            assertThat(dto.autorizada()).isFalse();
            assertThat(dto.urlPublica()).isNull();
            assertThat(dto.previewUrl()).isEqualTo(resposta.equals("presente") ? "/preview/" + idCard(100) : null);
            assertThat(dto.pendenciaMidia()).isEqualTo(resposta.equals("presente")
                    ? "MIDIA_RESTRITA_IDADE" : "PENDENTE_DERIVACAO_RESTRITA");
        });
        verify(urlService).resolverPreviewRestrita(arquivos.get(idCard(100)));
        verify(urlService, never()).resolverPreviewRestrita(arquivos.get(idCard(101)));
        verify(urlService, never()).resolver(any(), any());
    }

    @Test
    void cardMantemExclusoesDeStoryStatusVisibilidadeEVideoSemBeneficio() {
        var vinculos = new ArrayList<AnuncioMidiaEntity>();
        var arquivos = new LinkedHashMap<UUID, ArquivoMidiaEntity>();
        adicionar(vinculos, arquivos, idCard(1), TipoAnuncioMidia.FOTO, VisibilidadeMidia.LIVRE, 10);
        adicionar(vinculos, arquivos, idCard(2), TipoAnuncioMidia.FOTO, VisibilidadeMidia.RESTRITA_18, 0);
        when(vinculos.get(1).getStatus()).thenReturn(StatusAnuncioMidia.PENDENTE);
        adicionar(vinculos, arquivos, idCard(3), TipoAnuncioMidia.FOTO, VisibilidadeMidia.RESTRITA_18, 0);
        when(vinculos.get(2).getStatus()).thenReturn(StatusAnuncioMidia.REJEITADA);
        adicionar(vinculos, arquivos, idCard(4), TipoAnuncioMidia.STORY, VisibilidadeMidia.RESTRITA_18, 0);
        adicionar(vinculos, arquivos, idCard(5), TipoAnuncioMidia.FOTO, VisibilidadeMidia.RESTRITA_18, 0);
        when(vinculos.get(4).getFinalidade()).thenReturn(FinalidadeAnuncioMidia.STORY);
        adicionar(vinculos, arquivos, idCard(6), TipoAnuncioMidia.FOTO, null, 0);
        adicionar(vinculos, arquivos, idCard(7), TipoAnuncioMidia.VIDEO, VisibilidadeMidia.LIVRE, 0);
        configurarUrlsPublicasEPreviews();

        assertThat(mapper.publicasParaCard(vinculos, arquivos, 4, false, true, new MidiaPublicaSeguraPolicy()))
                .extracting(MidiaPublicaDto::id).containsExactly(idCard(1));
        verify(urlService, never()).resolverPreviewRestrita(any());
        verify(urlService, times(1)).resolver(any(), any());
    }

    private static UUID idCard(long valor) {
        return new UUID(0, valor);
    }

    private void adicionar(List<AnuncioMidiaEntity> vinculos, Map<UUID, ArquivoMidiaEntity> arquivos,
            UUID id, TipoAnuncioMidia tipo, VisibilidadeMidia visibilidade, Integer ordem) {
        vinculos.add(midia(id, tipo, visibilidade, id, ordem));
        ArquivoMidiaEntity arquivo = arquivo(id);
        when(arquivo.getLargura()).thenReturn(800);
        when(arquivo.getAltura()).thenReturn(600);
        if (tipo == TipoAnuncioMidia.VIDEO) when(arquivo.getMimeType()).thenReturn("video/mp4");
        arquivos.put(id, arquivo);
    }

    private void configurarUrlsPublicasEPreviews() {
        when(urlService.resolver(any(), any())).thenAnswer(call -> {
            AnuncioMidiaEntity vinculo = call.getArgument(0);
            return new MidiaPublicaUrlService.ResultadoUrlPublica("/original/" + vinculo.getId(), null);
        });
        when(urlService.resolverPreviewRestrita(any())).thenAnswer(call -> {
            ArquivoMidiaEntity arquivo = call.getArgument(0);
            return new MidiaPublicaUrlService.ResultadoUrlPublica("/preview/" + arquivo.getId(), null);
        });
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
