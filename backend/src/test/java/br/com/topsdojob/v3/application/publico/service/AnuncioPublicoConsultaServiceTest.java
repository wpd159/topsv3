package br.com.topsdojob.v3.application.publico.service;

import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.entity;
import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import br.com.topsdojob.v3.application.metrica.VisualizacaoTotalCanonicaService;
import br.com.topsdojob.v3.application.metrica.VisualizacoesCanonicasDto;
import br.com.topsdojob.v3.application.publico.compliance.ComplianceVisitorAccessService;
import br.com.topsdojob.v3.application.publico.mapper.AnuncioPublicoMapper;
import br.com.topsdojob.v3.application.publico.mapper.MidiaPublicaSeguraPolicy;
import br.com.topsdojob.v3.application.publico.mapper.MidiaPublicaMapper;
import br.com.topsdojob.v3.application.publico.mapper.SeoPublicoMapper;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoMapper;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoFlagsDto;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioLocalizacaoEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.CidadeEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.EstadoEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioLocalizacaoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.BairroRepository;
import br.com.topsdojob.v3.persistence.repository.CidadeRepository;
import br.com.topsdojob.v3.persistence.repository.EstadoRepository;
import br.com.topsdojob.v3.persistence.repository.SeoUrlRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.LocalAtendimentoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ServicoAnuncio;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AnuncioPublicoConsultaServiceTest {

    @Test
    void retornaDetalheComLocalizacaoReal() {
        UUID anuncioId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        UUID estadoId = UUID.randomUUID();
        UUID cidadeId = UUID.randomUUID();
        AnuncioEntity anuncio = entity(AnuncioEntity.class);
        set(anuncio, "id", anuncioId);
        set(anuncio, "usuarioId", usuarioId);
        set(anuncio, "slug", "slug-publico");
        set(anuncio, "titulo", "Perfil publico");
        set(anuncio, "status", StatusAnuncio.PUBLICADO);
        set(anuncio, "statusModeracao", StatusModeracaoAnuncio.APROVADO);
        set(anuncio, "locaisAtendimento", Set.of(
                LocalAtendimentoAnuncio.A_COMBINAR,
                LocalAtendimentoAnuncio.MEU_LOCAL));
        set(anuncio, "servicos", Set.of(ServicoAnuncio.ANAL, ServicoAnuncio.ORAL));
        AnuncioLocalizacaoEntity localizacao = entity(AnuncioLocalizacaoEntity.class);
        set(localizacao, "anuncioId", anuncioId);
        set(localizacao, "estadoId", estadoId);
        set(localizacao, "cidadeId", cidadeId);
        EstadoEntity estado = entity(EstadoEntity.class);
        set(estado, "id", estadoId);
        set(estado, "uf", "GO");
        set(estado, "nome", "Goias");
        CidadeEntity cidade = entity(CidadeEntity.class);
        set(cidade, "id", cidadeId);
        set(cidade, "estadoId", estadoId);
        set(cidade, "nome", "Goiania");
        set(cidade, "slug", "goiania");

        AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
        AnuncioLocalizacaoRepository localizacaoRepository = mock(AnuncioLocalizacaoRepository.class);
        EstadoRepository estadoRepository = mock(EstadoRepository.class);
        CidadeRepository cidadeRepository = mock(CidadeRepository.class);
        when(anuncioRepository.findBySlugAndStatusAndStatusModeracaoAndRemovidoEmIsNull(
                "slug-publico", StatusAnuncio.PUBLICADO, StatusModeracaoAnuncio.APROVADO))
                .thenReturn(Optional.of(anuncio));
        when(localizacaoRepository.findByAnuncioId(anuncioId)).thenReturn(Optional.of(localizacao));
        when(estadoRepository.findById(estadoId)).thenReturn(Optional.of(estado));
        when(cidadeRepository.findById(cidadeId)).thenReturn(Optional.of(cidade));
        AnuncioRepository.PrimeiraPublicacaoAnuncianteProjection primeiraPublicacao =
                mock(AnuncioRepository.PrimeiraPublicacaoAnuncianteProjection.class);
        OffsetDateTime anunciaDesde = OffsetDateTime.parse("2024-07-03T12:00:00Z");
        when(primeiraPublicacao.getUsuarioId()).thenReturn(usuarioId);
        when(primeiraPublicacao.getPrimeiraPublicacaoEm()).thenReturn(anunciaDesde);
        when(anuncioRepository.findPrimeiraPublicacaoByUsuarioIdIn(List.of(usuarioId)))
                .thenReturn(List.of(primeiraPublicacao));
        PremiumPublicoMapper premiumMapper = mock(PremiumPublicoMapper.class);
        when(premiumMapper.flags(anuncio)).thenReturn(
                br.com.topsdojob.v3.application.publico.premium.PremiumPublicoFlagsDto.vazio());
        IdadeAnunciantePublicaService idadeAnuncianteService = mock(IdadeAnunciantePublicaService.class);
        when(idadeAnuncianteService.resolver(usuarioId, false))
                .thenReturn(new IdadeAnunciantePublicaService.Resultado("perfil-publico", 36, false));
        VisualizacaoTotalCanonicaService visualizacaoService = mock(VisualizacaoTotalCanonicaService.class);
        when(visualizacaoService.calcular(anuncioId)).thenReturn(VisualizacoesCanonicasDto.total(45));

        AnuncioPublicoConsultaService service = new AnuncioPublicoConsultaService(
                anuncioRepository,
                localizacaoRepository,
                mock(AnuncioMidiaRepository.class),
                mock(ArquivoMidiaRepository.class),
                new AnuncioPublicoMapper(new MidiaPublicaSeguraPolicy()),
                new MidiaPublicaMapper(new MidiaPublicaUrlService()),
                new MidiaPublicaSeguraPolicy(),
                new SeoPublicoConsultaService(mock(SeoUrlRepository.class), new SeoPublicoMapper()),
                mock(ComplianceVisitorAccessService.class),
                premiumMapper,
                estadoRepository,
                cidadeRepository,
                mock(BairroRepository.class),
                mock(PoliticaContatoPublicoService.class),
                mock(AnuncioSeoIndexabilidadePolicy.class),
                idadeAnuncianteService,
                visualizacaoService);

        var detalhe = service.buscarPorSlug("slug-publico");

        assertThat(detalhe.id()).isEqualTo(anuncioId);
        assertThat(detalhe.localizacao().uf()).isEqualTo("GO");
        assertThat(detalhe.localizacao().cidadeSlug()).isEqualTo("goiania");
        assertThat(detalhe.anunciaDesde()).isEqualTo(anunciaDesde);
        assertThat(detalhe.comLocal()).isTrue();
        assertThat(detalhe.fazAnal()).isTrue();
        assertThat(detalhe.locaisAtendimento()).containsExactlyInAnyOrder("A_COMBINAR", "MEU_LOCAL");
        assertThat(detalhe.servicos()).containsExactlyInAnyOrder("ANAL", "ORAL");
        assertThat(detalhe.username()).isEqualTo("perfil-publico");
        assertThat(detalhe.idade()).isEqualTo(36);
        assertThat(detalhe.idadeOculta()).isFalse();
        assertThat(detalhe.visualizacoes().total()).isEqualTo(45);
    }

    @Test
    void retorna404ParaAnuncioInexistente() {
        AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
        when(anuncioRepository.findBySlugAndStatusAndStatusModeracaoAndRemovidoEmIsNull(
                "slug-local",
                StatusAnuncio.PUBLICADO,
                StatusModeracaoAnuncio.APROVADO))
                .thenReturn(Optional.empty());

        AnuncioPublicoConsultaService service = new AnuncioPublicoConsultaService(
                anuncioRepository,
                mock(AnuncioLocalizacaoRepository.class),
                mock(AnuncioMidiaRepository.class),
                mock(ArquivoMidiaRepository.class),
                new AnuncioPublicoMapper(new MidiaPublicaSeguraPolicy()),
                new MidiaPublicaMapper(new MidiaPublicaUrlService()),
                new MidiaPublicaSeguraPolicy(),
                new SeoPublicoConsultaService(mock(SeoUrlRepository.class), new SeoPublicoMapper()),
                mock(ComplianceVisitorAccessService.class),
                mock(PremiumPublicoMapper.class),
                mock(EstadoRepository.class),
                mock(CidadeRepository.class),
                mock(BairroRepository.class),
                mock(PoliticaContatoPublicoService.class),
                mock(AnuncioSeoIndexabilidadePolicy.class),
                mock(IdadeAnunciantePublicaService.class),
                mock(VisualizacaoTotalCanonicaService.class));

        assertThatThrownBy(() -> service.buscarPorSlug("slug-local"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void carregaMidiasPublicasEmLotePorPagina() {
        UUID anuncioId = UUID.randomUUID();
        UUID outroAnuncioId = UUID.randomUUID();
        UUID arquivoId = UUID.randomUUID();
        AnuncioMidiaEntity vinculo = entity(AnuncioMidiaEntity.class);
        set(vinculo, "id", UUID.randomUUID());
        set(vinculo, "anuncioId", anuncioId);
        set(vinculo, "arquivoMidiaId", arquivoId);
        set(vinculo, "tipo", TipoAnuncioMidia.FOTO);
        set(vinculo, "finalidade", FinalidadeAnuncioMidia.GALERIA);
        set(vinculo, "ordem", 0);
        set(vinculo, "status", StatusAnuncioMidia.PUBLICAVEL);
        set(vinculo, "visibilidadeMidia", VisibilidadeMidia.LIVRE);
        ArquivoMidiaEntity arquivo = entity(ArquivoMidiaEntity.class);
        set(arquivo, "id", arquivoId);
        set(arquivo, "statusArquivo", StatusArquivoMidia.VALIDADO);
        set(arquivo, "mimeType", "image/jpeg");
        set(arquivo, "storageProvider", "LOCAL_MOCK");
        set(arquivo, "bucket", "topsv3-hml-fixture");
        set(arquivo, "chaveObjeto", "fixture/stories/foto-segura.jpg");

        AnuncioMidiaRepository midiaRepository = mock(AnuncioMidiaRepository.class);
        ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
        when(midiaRepository.findByAnuncioIdIn(List.of(anuncioId, outroAnuncioId)))
                .thenReturn(List.of(vinculo));
        when(arquivoRepository.findByIdIn(List.of(arquivoId))).thenReturn(List.of(arquivo));

        AnuncioPublicoConsultaService service = new AnuncioPublicoConsultaService(
                mock(AnuncioRepository.class),
                mock(AnuncioLocalizacaoRepository.class),
                midiaRepository,
                arquivoRepository,
                new AnuncioPublicoMapper(new MidiaPublicaSeguraPolicy()),
                new MidiaPublicaMapper(new MidiaPublicaUrlService("homologacao", "https://v3.esle.cloud")),
                new MidiaPublicaSeguraPolicy(),
                new SeoPublicoConsultaService(mock(SeoUrlRepository.class), new SeoPublicoMapper()),
                mock(ComplianceVisitorAccessService.class),
                mock(PremiumPublicoMapper.class),
                mock(EstadoRepository.class),
                mock(CidadeRepository.class),
                mock(BairroRepository.class),
                mock(PoliticaContatoPublicoService.class),
                mock(AnuncioSeoIndexabilidadePolicy.class),
                mock(IdadeAnunciantePublicaService.class),
                mock(VisualizacaoTotalCanonicaService.class));

        var midias = service.midiasPorAnuncios(
                List.of(anuncioId, outroAnuncioId),
                Map.of(anuncioId, PremiumPublicoFlagsDto.vazio()));

        assertThat(midias.get(anuncioId)).singleElement().satisfies(item -> {
            assertThat(item.tipo()).isEqualTo("FOTO");
            assertThat(item.visibilidadeMidia()).isEqualTo("LIVRE");
            assertThat(item.urlPublica()).isEqualTo("https://v3.esle.cloud/demo-safe-public.svg");
        });
        assertThat(midias.get(outroAnuncioId)).isEmpty();
    }

    @Test
    void storyReavaliaLimiteAtualDeFotosEVideoSemSnapshot() {
        UUID anuncioId = UUID.randomUUID();
        AnuncioEntity anuncio = anuncio(anuncioId, UUID.randomUUID(), "story-limites", "Story limites");
        List<AnuncioMidiaEntity> vinculos = new ArrayList<>();
        List<ArquivoMidiaEntity> arquivos = new ArrayList<>();
        for (int ordem = 0; ordem < 11; ordem++) {
            UUID arquivoId = UUID.randomUUID();
            vinculos.add(vinculo(anuncioId, arquivoId, TipoAnuncioMidia.FOTO, ordem, StatusAnuncioMidia.PUBLICAVEL));
            arquivos.add(arquivo(arquivoId, "image/jpeg"));
        }
        UUID videoArquivoId = UUID.randomUUID();
        vinculos.add(vinculo(anuncioId, videoArquivoId, TipoAnuncioMidia.VIDEO, 20, StatusAnuncioMidia.PUBLICAVEL));
        arquivos.add(arquivo(videoArquivoId, "video/mp4"));
        UUID pendenteArquivoId = UUID.randomUUID();
        vinculos.add(vinculo(anuncioId, pendenteArquivoId, TipoAnuncioMidia.FOTO, 21, StatusAnuncioMidia.PENDENTE));
        arquivos.add(arquivo(pendenteArquivoId, "image/jpeg"));

        AnuncioMidiaRepository midiaRepository = mock(AnuncioMidiaRepository.class);
        ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
        MidiaPublicaUrlService urlService = mock(MidiaPublicaUrlService.class);
        PremiumPublicoMapper premiumMapper = mock(PremiumPublicoMapper.class);
        when(midiaRepository.findByAnuncioId(anuncioId)).thenReturn(vinculos);
        when(arquivoRepository.findByIdIn(any())).thenReturn(arquivos);
        when(urlService.resolver(any(), any())).thenAnswer(invocation -> {
            AnuncioMidiaEntity vinculo = invocation.getArgument(0);
            return new MidiaPublicaUrlService.ResultadoUrlPublica(
                    "/api/public/compliance/visitor/media/" + vinculo.getId(), null);
        });
        PremiumPublicoFlagsDto comExtrasEVideo = new PremiumPublicoFlagsDto(
                false, false, true, false, true, false,
                true, false, true, false, List.of("Fotos extras", "Video"));
        when(premiumMapper.flags(anuncio))
                .thenReturn(comExtrasEVideo)
                .thenReturn(PremiumPublicoFlagsDto.vazio());
        AnuncioPublicoConsultaService service = new AnuncioPublicoConsultaService(
                mock(AnuncioRepository.class),
                mock(AnuncioLocalizacaoRepository.class),
                midiaRepository,
                arquivoRepository,
                new AnuncioPublicoMapper(new MidiaPublicaSeguraPolicy()),
                new MidiaPublicaMapper(urlService),
                new MidiaPublicaSeguraPolicy(),
                mock(SeoPublicoConsultaService.class),
                mock(ComplianceVisitorAccessService.class),
                premiumMapper,
                mock(EstadoRepository.class),
                mock(CidadeRepository.class),
                mock(BairroRepository.class),
                mock(PoliticaContatoPublicoService.class),
                mock(AnuncioSeoIndexabilidadePolicy.class),
                mock(IdadeAnunciantePublicaService.class),
                mock(VisualizacaoTotalCanonicaService.class));

        var comExtras = service.midiasParaStory(anuncio, true);
        var semExtras = service.midiasParaStory(anuncio, true);

        assertThat(comExtras).filteredOn(item -> "FOTO".equals(item.tipo())).hasSize(10);
        assertThat(comExtras).filteredOn(item -> "VIDEO".equals(item.tipo())).hasSize(1);
        assertThat(comExtras).extracting(item -> item.ordem()).isSorted();
        assertThat(semExtras).filteredOn(item -> "FOTO".equals(item.tipo())).hasSize(4);
        assertThat(semExtras).noneMatch(item -> "VIDEO".equals(item.tipo()));
        assertThat(semExtras).allSatisfy(item -> {
            assertThat(item.autorizada()).isTrue();
            assertThat(item.urlPublica()).startsWith("/api/public/compliance/visitor/media/");
            assertThat(item.toString()).doesNotContain("objectKey").doesNotContain("X-Amz-");
        });
    }

    @Test
    void relacionadosUsamLocalSemCompletarEFallbackSomenteQuandoLocalVazio() {
        UUID anuncioAtualId = UUID.randomUUID();
        UUID usuarioAtualId = UUID.randomUUID();
        UUID localId = UUID.randomUUID();
        UUID localDoisId = UUID.randomUUID();
        UUID fallbackId = UUID.randomUUID();
        UUID cidadeAtualId = UUID.randomUUID();
        UUID cidadeFallbackId = UUID.randomUUID();
        UUID estadoId = UUID.randomUUID();
        AnuncioEntity atual = anuncio(anuncioAtualId, usuarioAtualId, "atual", "Atual");
        AnuncioEntity local = anuncio(localId, UUID.randomUUID(), "local", "Local");
        AnuncioEntity localDois = anuncio(localDoisId, UUID.randomUUID(), "local-dois", "Local dois");
        AnuncioEntity fallback = anuncio(fallbackId, UUID.randomUUID(), "fallback", "Fallback");
        AnuncioLocalizacaoEntity localizacaoAtual = localizacao(anuncioAtualId, estadoId, cidadeAtualId);
        AnuncioLocalizacaoEntity localizacaoLocal = localizacao(localId, estadoId, cidadeAtualId);
        AnuncioLocalizacaoEntity localizacaoLocalDois = localizacao(localDoisId, estadoId, cidadeAtualId);
        AnuncioLocalizacaoEntity localizacaoFallback = localizacao(fallbackId, estadoId, cidadeFallbackId);
        EstadoEntity estado = estado(estadoId);
        CidadeEntity cidadeAtual = cidade(cidadeAtualId, estadoId, "Goiania");
        CidadeEntity cidadeFallback = cidade(cidadeFallbackId, estadoId, "Anapolis");

        AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
        AnuncioLocalizacaoRepository localizacaoRepository = mock(AnuncioLocalizacaoRepository.class);
        EstadoRepository estadoRepository = mock(EstadoRepository.class);
        CidadeRepository cidadeRepository = mock(CidadeRepository.class);
        PremiumPublicoMapper premiumMapper = mock(PremiumPublicoMapper.class);
        IdadeAnunciantePublicaService idadeService = mock(IdadeAnunciantePublicaService.class);
        VisualizacaoTotalCanonicaService visualizacaoService = mock(VisualizacaoTotalCanonicaService.class);
        when(anuncioRepository.findBySlugAndStatusAndStatusModeracaoAndRemovidoEmIsNull(
                "atual", StatusAnuncio.PUBLICADO, StatusModeracaoAnuncio.APROVADO))
                .thenReturn(Optional.of(atual));
        when(localizacaoRepository.findByAnuncioId(anuncioAtualId)).thenReturn(Optional.of(localizacaoAtual));
        when(estadoRepository.findById(estadoId)).thenReturn(Optional.of(estado));
        when(cidadeRepository.findById(cidadeAtualId)).thenReturn(Optional.of(cidadeAtual));
        when(premiumMapper.flags(atual)).thenReturn(PremiumPublicoFlagsDto.vazio());
        when(idadeService.resolver(usuarioAtualId, false))
                .thenReturn(new IdadeAnunciantePublicaService.Resultado("Atual", 30, false));
        when(visualizacaoService.calcular(anuncioAtualId)).thenReturn(VisualizacoesCanonicasDto.total(0));
        when(anuncioRepository.findRelacionadosPagos(
                eq(anuncioAtualId), eq("ACOMPANHANTE_FEMININA"), eq(cidadeAtualId), eq(true),
                any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(local));
        when(localizacaoRepository.findByAnuncioIdIn(List.of(localId))).thenReturn(List.of(localizacaoLocal));
        when(cidadeRepository.findAllById(any())).thenReturn(List.of(cidadeAtual));
        when(estadoRepository.findAllById(any())).thenReturn(List.of(estado));
        when(premiumMapper.flagsPorAnuncios(List.of(local)))
                .thenReturn(Map.of(localId, PremiumPublicoFlagsDto.vazio()));
        when(idadeService.resolverPorAnuncios(eq(List.of(local)), any()))
                .thenReturn(Map.of(localId, new IdadeAnunciantePublicaService.Resultado("Local", 28, false)));

        AnuncioPublicoConsultaService service = new AnuncioPublicoConsultaService(
                anuncioRepository,
                localizacaoRepository,
                mock(AnuncioMidiaRepository.class),
                mock(ArquivoMidiaRepository.class),
                new AnuncioPublicoMapper(new MidiaPublicaSeguraPolicy()),
                new MidiaPublicaMapper(new MidiaPublicaUrlService()),
                new MidiaPublicaSeguraPolicy(),
                new SeoPublicoConsultaService(mock(SeoUrlRepository.class), new SeoPublicoMapper()),
                mock(ComplianceVisitorAccessService.class),
                premiumMapper,
                estadoRepository,
                cidadeRepository,
                mock(BairroRepository.class),
                mock(PoliticaContatoPublicoService.class),
                mock(AnuncioSeoIndexabilidadePolicy.class),
                idadeService,
                visualizacaoService);

        var detalheLocal = service.buscarPorSlug("atual");

        assertThat(detalheLocal.relacionados()).extracting(item -> item.id()).containsExactly(localId);
        verify(anuncioRepository, never()).findRelacionadosPagos(
                eq(anuncioAtualId), eq("ACOMPANHANTE_FEMININA"), eq(cidadeAtualId), eq(false),
                any(OffsetDateTime.class), any(Pageable.class));

        clearInvocations(anuncioRepository, localizacaoRepository, cidadeRepository, estadoRepository,
                premiumMapper, idadeService);
        when(anuncioRepository.findRelacionadosPagos(
                eq(anuncioAtualId), eq("ACOMPANHANTE_FEMININA"), eq(cidadeAtualId), eq(true),
                any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(local, localDois));
        when(localizacaoRepository.findByAnuncioIdIn(List.of(localId, localDoisId)))
                .thenReturn(List.of(localizacaoLocal, localizacaoLocalDois));
        when(premiumMapper.flagsPorAnuncios(List.of(local, localDois)))
                .thenReturn(Map.of(
                        localId, PremiumPublicoFlagsDto.vazio(),
                        localDoisId, PremiumPublicoFlagsDto.vazio()));
        when(idadeService.resolverPorAnuncios(eq(List.of(local, localDois)), any()))
                .thenReturn(Map.of(
                        localId, new IdadeAnunciantePublicaService.Resultado("Local", 28, false),
                        localDoisId, new IdadeAnunciantePublicaService.Resultado("Local dois", 29, false)));

        var detalheDoisLocais = service.buscarPorSlug("atual");

        assertThat(detalheDoisLocais.relacionados())
                .extracting(item -> item.id())
                .containsExactly(localId, localDoisId);
        verify(anuncioRepository, never()).findRelacionadosPagos(
                eq(anuncioAtualId), eq("ACOMPANHANTE_FEMININA"), eq(cidadeAtualId), eq(false),
                any(OffsetDateTime.class), any(Pageable.class));

        clearInvocations(anuncioRepository, localizacaoRepository, cidadeRepository, estadoRepository,
                premiumMapper, idadeService);
        when(anuncioRepository.findRelacionadosPagos(
                eq(anuncioAtualId), eq("ACOMPANHANTE_FEMININA"), eq(cidadeAtualId), eq(true),
                any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(List.of());
        when(anuncioRepository.findRelacionadosPagos(
                eq(anuncioAtualId), eq("ACOMPANHANTE_FEMININA"), eq(cidadeAtualId), eq(false),
                any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(fallback));
        when(localizacaoRepository.findByAnuncioId(anuncioAtualId)).thenReturn(Optional.of(localizacaoAtual));
        when(estadoRepository.findById(estadoId)).thenReturn(Optional.of(estado));
        when(cidadeRepository.findById(cidadeAtualId)).thenReturn(Optional.of(cidadeAtual));
        when(localizacaoRepository.findByAnuncioIdIn(List.of(fallbackId))).thenReturn(List.of(localizacaoFallback));
        when(cidadeRepository.findAllById(any())).thenReturn(List.of(cidadeFallback));
        when(estadoRepository.findAllById(any())).thenReturn(List.of(estado));
        when(premiumMapper.flags(atual)).thenReturn(PremiumPublicoFlagsDto.vazio());
        when(premiumMapper.flagsPorAnuncios(List.of(fallback)))
                .thenReturn(Map.of(fallbackId, PremiumPublicoFlagsDto.vazio()));
        when(idadeService.resolver(usuarioAtualId, false))
                .thenReturn(new IdadeAnunciantePublicaService.Resultado("Atual", 30, false));
        when(idadeService.resolverPorAnuncios(eq(List.of(fallback)), any()))
                .thenReturn(Map.of(fallbackId, new IdadeAnunciantePublicaService.Resultado("Fallback", 27, false)));

        var detalheFallback = service.buscarPorSlug("atual");

        assertThat(detalheFallback.relacionados()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(fallbackId);
            assertThat(item.cidadeNome()).isEqualTo("Anapolis");
        });
        verify(anuncioRepository).findRelacionadosPagos(
                eq(anuncioAtualId), eq("ACOMPANHANTE_FEMININA"), eq(cidadeAtualId), eq(false),
                any(OffsetDateTime.class), any(Pageable.class));
    }

    private AnuncioEntity anuncio(UUID id, UUID usuarioId, String slug, String titulo) {
        AnuncioEntity anuncio = entity(AnuncioEntity.class);
        set(anuncio, "id", id);
        set(anuncio, "usuarioId", usuarioId);
        set(anuncio, "slug", slug);
        set(anuncio, "titulo", titulo);
        set(anuncio, "categoria", "ACOMPANHANTE_FEMININA");
        set(anuncio, "status", StatusAnuncio.PUBLICADO);
        set(anuncio, "statusModeracao", StatusModeracaoAnuncio.APROVADO);
        set(anuncio, "locaisAtendimento", Set.of());
        set(anuncio, "servicos", Set.of());
        return anuncio;
    }

    private AnuncioMidiaEntity vinculo(
            UUID anuncioId,
            UUID arquivoId,
            TipoAnuncioMidia tipo,
            int ordem,
            StatusAnuncioMidia status) {
        AnuncioMidiaEntity vinculo = entity(AnuncioMidiaEntity.class);
        set(vinculo, "id", UUID.randomUUID());
        set(vinculo, "anuncioId", anuncioId);
        set(vinculo, "arquivoMidiaId", arquivoId);
        set(vinculo, "tipo", tipo);
        set(vinculo, "finalidade", FinalidadeAnuncioMidia.GALERIA);
        set(vinculo, "ordem", ordem);
        set(vinculo, "status", status);
        set(vinculo, "visibilidadeMidia", VisibilidadeMidia.LIVRE);
        return vinculo;
    }

    private ArquivoMidiaEntity arquivo(UUID id, String mimeType) {
        ArquivoMidiaEntity arquivo = entity(ArquivoMidiaEntity.class);
        set(arquivo, "id", id);
        set(arquivo, "statusArquivo", StatusArquivoMidia.VALIDADO);
        set(arquivo, "mimeType", mimeType);
        return arquivo;
    }

    private AnuncioLocalizacaoEntity localizacao(UUID anuncioId, UUID estadoId, UUID cidadeId) {
        AnuncioLocalizacaoEntity localizacao = entity(AnuncioLocalizacaoEntity.class);
        set(localizacao, "anuncioId", anuncioId);
        set(localizacao, "estadoId", estadoId);
        set(localizacao, "cidadeId", cidadeId);
        return localizacao;
    }

    private EstadoEntity estado(UUID id) {
        EstadoEntity estado = entity(EstadoEntity.class);
        set(estado, "id", id);
        set(estado, "uf", "GO");
        set(estado, "nome", "Goias");
        return estado;
    }

    private CidadeEntity cidade(UUID id, UUID estadoId, String nome) {
        CidadeEntity cidade = entity(CidadeEntity.class);
        set(cidade, "id", id);
        set(cidade, "estadoId", estadoId);
        set(cidade, "nome", nome);
        set(cidade, "slug", nome.toLowerCase());
        return cidade;
    }
}
