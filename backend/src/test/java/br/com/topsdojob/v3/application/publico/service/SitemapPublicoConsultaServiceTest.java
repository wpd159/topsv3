package br.com.topsdojob.v3.application.publico.service;

import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.entity;
import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.dto.LocalizacaoPublicaDto;
import br.com.topsdojob.v3.application.publico.dto.SitemapAnuncioPublicoDto;
import br.com.topsdojob.v3.application.publico.mapper.MidiaPublicaMapper;
import br.com.topsdojob.v3.persistence.repository.projection.MidiaVinculoLeitura;
import br.com.topsdojob.v3.persistence.repository.projection.ArquivoPublicoLeitura;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoFlagsDto;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoMapper;
import br.com.topsdojob.v3.application.publico.service.MidiaPublicaUrlService.ResultadoUrlPublica;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
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
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.time.OffsetDateTime;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;

class SitemapPublicoConsultaServiceTest {

    @Test
    void listaSomenteIndexaveisComConsultasEmLoteESemDadosInternos() {
        UUID anuncioId = UUID.randomUUID();
        UUID estadoId = UUID.randomUUID();
        UUID cidadeId = UUID.randomUUID();
        OffsetDateTime atualizadoEm = OffsetDateTime.parse("2026-07-11T12:00:00-03:00");

        AnuncioEntity anuncio = entity(AnuncioEntity.class);
        set(anuncio, "id", anuncioId);
        set(anuncio, "slug", "perfil-publico-goiania");
        set(anuncio, "titulo", "Perfil publico Goiania");
        set(anuncio, "descricao", "Descricao publica consistente para indexacao, com informacoes suficientes para apresentar o perfil sem texto generico e com mais de cento e vinte caracteres.");
        set(anuncio, "status", StatusAnuncio.PUBLICADO);
        set(anuncio, "statusModeracao", StatusModeracaoAnuncio.APROVADO);
        set(anuncio, "atualizadoEm", atualizadoEm);

        AnuncioLocalizacaoEntity localizacao = entity(AnuncioLocalizacaoEntity.class);
        set(localizacao, "anuncioId", anuncioId);
        set(localizacao, "estadoId", estadoId);
        set(localizacao, "cidadeId", cidadeId);
        set(localizacao, "atualizadoEm", atualizadoEm.minusDays(1));

        EstadoEntity estado = entity(EstadoEntity.class);
        set(estado, "id", estadoId);
        set(estado, "uf", "GO");
        set(estado, "nome", "Goias");
        CidadeEntity cidade = entity(CidadeEntity.class);
        set(cidade, "id", cidadeId);
        set(cidade, "estadoId", estadoId);
        set(cidade, "nome", "Goiania");
        set(cidade, "slug", "goiania");

        List<AnuncioMidiaEntity> vinculos = new ArrayList<>();
        List<ArquivoMidiaEntity> arquivos = new ArrayList<>();
        for (int ordem = 0; ordem < 1; ordem++) {
            UUID arquivoId = UUID.randomUUID();
            AnuncioMidiaEntity vinculo = entity(AnuncioMidiaEntity.class);
            set(vinculo, "id", UUID.randomUUID());
            set(vinculo, "anuncioId", anuncioId);
            set(vinculo, "arquivoMidiaId", arquivoId);
            set(vinculo, "tipo", TipoAnuncioMidia.FOTO);
            set(vinculo, "finalidade", FinalidadeAnuncioMidia.GALERIA);
            set(vinculo, "ordem", ordem);
            set(vinculo, "status", StatusAnuncioMidia.PUBLICAVEL);
            set(vinculo, "visibilidadeMidia", VisibilidadeMidia.LIVRE);
            set(vinculo, "atualizadoEm", atualizadoEm.minusHours(ordem));
            vinculos.add(vinculo);

            ArquivoMidiaEntity arquivo = entity(ArquivoMidiaEntity.class);
            set(arquivo, "id", arquivoId);
            set(arquivo, "statusArquivo", StatusArquivoMidia.VALIDADO);
            arquivos.add(arquivo);
        }

        AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
        AnuncioLocalizacaoRepository localizacaoRepository = mock(AnuncioLocalizacaoRepository.class);
        AnuncioMidiaRepository midiaRepository = mock(AnuncioMidiaRepository.class);
        ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
        EstadoRepository estadoRepository = mock(EstadoRepository.class);
        CidadeRepository cidadeRepository = mock(CidadeRepository.class);
        BairroRepository bairroRepository = mock(BairroRepository.class);
        MidiaPublicaUrlService urlService = mock(MidiaPublicaUrlService.class);
        PremiumPublicoMapper premiumMapper = mock(PremiumPublicoMapper.class);

        when(anuncioRepository.findPublicosComProprietarioAtivo()).thenReturn(List.of(anuncio));
        when(localizacaoRepository.findByAnuncioIdIn(List.of(anuncioId))).thenReturn(List.of(localizacao));
        when(estadoRepository.findAllById(any())).thenReturn(List.of(estado));
        when(cidadeRepository.findAllById(any())).thenReturn(List.of(cidade));
        when(bairroRepository.findAllById(any())).thenReturn(List.of());
        var leituras = vinculos.stream().map(MidiaVinculoLeitura::de).toList();
        when(midiaRepository.findLeiturasPublicas(List.of(anuncioId))).thenReturn(leituras);
        when(arquivoRepository.findLeiturasPublicas(any())).thenReturn(arquivos.stream().map(ArquivoPublicoLeitura::de).toList());
        when(urlService.resolverLeitura(any(), any())).thenReturn(new ResultadoUrlPublica("/midia/publica.jpg", null));
        when(premiumMapper.flagsMidiaPorAnuncioIds(List.of(anuncioId)))
                .thenReturn(Map.of(anuncioId, PremiumPublicoFlagsDto.vazio()));

        AnuncioSeoElegibilidadeConsultaService elegibilidadeService = new AnuncioSeoElegibilidadeConsultaService(
                midiaRepository,
                arquivoRepository,
                new MidiaPublicaMapper(urlService),
                premiumMapper,
                new AnuncioSeoIndexabilidadePolicy());
        SitemapPublicoConsultaService service = new SitemapPublicoConsultaService(
                anuncioRepository,
                localizacaoRepository,
                estadoRepository,
                cidadeRepository,
                bairroRepository,
                elegibilidadeService);

        var entradas = service.listarAnunciosIndexaveis();

        assertThat(entradas).singleElement().satisfies(entrada -> {
            assertThat(entrada.slug()).isEqualTo("perfil-publico-goiania");
            assertThat(entrada.estadoUf()).isEqualTo("GO");
            assertThat(entrada.cidadeSlug()).isEqualTo("goiania");
            assertThat(entrada.atualizadoEm()).isEqualTo(atualizadoEm);
            assertThat(entrada.publico()).isTrue();
            assertThat(entrada.indexavel()).isTrue();
            assertThat(entrada.toString())
                    .doesNotContain("storage")
                    .doesNotContain("bucket")
                    .doesNotContain("contato")
                    .doesNotContain("whatsapp")
                    .doesNotContain("midia/publica.jpg");
        });
        verify(localizacaoRepository).findByAnuncioIdIn(List.of(anuncioId));
        verify(midiaRepository).findLeiturasPublicas(List.of(anuncioId));
        verify(arquivoRepository).findLeiturasPublicas(any());
    }

    @Test
    void aplicaAoSitemapOMesmoLimiteDeFotosDoDetalhePublico() {
        UUID anuncioId = UUID.randomUUID();
        UUID estadoId = UUID.randomUUID();
        UUID cidadeId = UUID.randomUUID();
        AnuncioEntity anuncio = mock(AnuncioEntity.class);
        when(anuncio.getId()).thenReturn(anuncioId);

        AnuncioLocalizacaoEntity localizacao = mock(AnuncioLocalizacaoEntity.class);
        when(localizacao.getAnuncioId()).thenReturn(anuncioId);
        when(localizacao.getEstadoId()).thenReturn(estadoId);
        when(localizacao.getCidadeId()).thenReturn(cidadeId);
        EstadoEntity estado = mock(EstadoEntity.class);
        when(estado.getId()).thenReturn(estadoId);
        when(estado.getUf()).thenReturn("GO");
        when(estado.getNome()).thenReturn("Goias");
        CidadeEntity cidade = mock(CidadeEntity.class);
        when(cidade.getId()).thenReturn(cidadeId);
        when(cidade.getEstadoId()).thenReturn(estadoId);
        when(cidade.getNome()).thenReturn("Goiania");
        when(cidade.getSlug()).thenReturn("goiania");

        UUID arquivoId = UUID.randomUUID();
        AnuncioMidiaEntity vinculo = mock(AnuncioMidiaEntity.class);
        when(vinculo.getAnuncioId()).thenReturn(anuncioId);
        when(vinculo.getArquivoMidiaId()).thenReturn(arquivoId);
        when(vinculo.getStatus()).thenReturn(StatusAnuncioMidia.PUBLICAVEL);
        when(vinculo.getTipo()).thenReturn(TipoAnuncioMidia.FOTO);
        when(vinculo.getVisibilidadeMidia()).thenReturn(VisibilidadeMidia.LIVRE);
        List<AnuncioMidiaEntity> vinculos = List.of(vinculo);
        AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
        AnuncioLocalizacaoRepository localizacaoRepository = mock(AnuncioLocalizacaoRepository.class);
        AnuncioMidiaRepository midiaRepository = mock(AnuncioMidiaRepository.class);
        ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
        EstadoRepository estadoRepository = mock(EstadoRepository.class);
        CidadeRepository cidadeRepository = mock(CidadeRepository.class);
        BairroRepository bairroRepository = mock(BairroRepository.class);
        MidiaPublicaMapper midiaMapper = mock(MidiaPublicaMapper.class);
        PremiumPublicoMapper premiumMapper = mock(PremiumPublicoMapper.class);
        AnuncioSeoIndexabilidadePolicy policy = mock(AnuncioSeoIndexabilidadePolicy.class);

        var leituras = vinculos.stream().map(MidiaVinculoLeitura::de).toList();
        when(midiaRepository.findLeiturasPublicas(List.of(anuncioId))).thenReturn(leituras);
        when(arquivoRepository.findLeiturasPublicas(any())).thenReturn(List.of());
        when(premiumMapper.flagsMidiaPorAnuncioIds(List.of(anuncioId)))
                .thenReturn(Map.of(anuncioId, PremiumPublicoFlagsDto.vazio()));
        when(midiaMapper.publicasLeituras(eq(vinculos.stream().map(MidiaVinculoLeitura::de).toList()), any(), eq(false), eq(4), eq(false)))
                .thenReturn(List.of());

        AnuncioSeoElegibilidadeConsultaService service = new AnuncioSeoElegibilidadeConsultaService(
                midiaRepository,
                arquivoRepository,
                midiaMapper,
                premiumMapper,
                policy);

        service.avaliar(
                List.of(anuncio),
                Map.of(anuncioId, new br.com.topsdojob.v3.application.publico.dto.LocalizacaoPublicaDto(
                        "GO", "Goias", "Goiania", "goiania", null, null, null)));

        verify(midiaMapper).publicasLeituras(eq(vinculos.stream().map(MidiaVinculoLeitura::de).toList()), any(), eq(false), eq(4), eq(false));
    }

    @Test
    void rejeitaAnuncioComMidiaRestritaMesmoQuePublicado() {
        AnuncioEntity anuncio = mock(AnuncioEntity.class);
        when(anuncio.getSlug()).thenReturn("perfil-publico");
        when(anuncio.getTitulo()).thenReturn("Perfil publico completo");
        when(anuncio.getDescricao()).thenReturn("x".repeat(130));
        when(anuncio.getStatus()).thenReturn(StatusAnuncio.PUBLICADO);
        when(anuncio.getStatusModeracao()).thenReturn(StatusModeracaoAnuncio.APROVADO);
        var localizacao = new br.com.topsdojob.v3.application.publico.dto.LocalizacaoPublicaDto(
                "GO", "Goias", "Goiania", "goiania", null, null, null);
        var restritas = java.util.stream.IntStream.range(0, 1)
                .mapToObj(ordem -> new br.com.topsdojob.v3.application.publico.dto.MidiaPublicaDto(
                        UUID.randomUUID(), "FOTO", "GALERIA", ordem, "RESTRITA_18", false, null,
                        "/midia/borrada.jpg", "MIDIA_RESTRITA_IDADE", 1080, 1920, "image/jpeg"))
                .toList();

        assertThat(new AnuncioSeoIndexabilidadePolicy().indexavel(anuncio, localizacao, restritas)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void preservaOraculoDaAvaliacaoGeralOrdemEAtualizacaoSemConsultarPreviews(boolean previewPresente) {
        var contexto = new ContextoSitemap();
        var zeta = contexto.anuncio(10, "perfil-zeta");
        var alfa = contexto.anuncio(20, "perfil-alfa");
        var somenteRestrita = contexto.anuncio(30, "perfil-restrito");
        var semUrl = contexto.anuncio(40, "perfil-sem-url");
        var generico = contexto.anuncio(50, "perfil-generico");
        set(generico, "titulo", "acompanhante 1");
        contexto.foto(zeta, 101, 0, VisibilidadeMidia.LIVRE, StatusArquivoMidia.VALIDADO);
        contexto.foto(zeta, 102, 1, VisibilidadeMidia.RESTRITA_18, StatusArquivoMidia.VALIDADO);
        contexto.foto(alfa, 201, 0, VisibilidadeMidia.LIVRE, StatusArquivoMidia.VALIDADO);
        contexto.foto(somenteRestrita, 301, 0, VisibilidadeMidia.RESTRITA_18, StatusArquivoMidia.VALIDADO);
        var arquivoSemUrl = contexto.foto(semUrl, 401, 0, VisibilidadeMidia.LIVRE, StatusArquivoMidia.VALIDADO);
        contexto.arquivos.replaceAll(arquivo -> arquivo.id().equals(arquivoSemUrl.arquivoMidiaId())
                ? new ArquivoPublicoLeitura(arquivo.id(), arquivo.sha256(), arquivo.statusArquivo(),
                        "PROVEDOR_INVALIDO", arquivo.bucket(), arquivo.chaveObjeto(), arquivo.mimeType(), 800, 600)
                : arquivo);
        contexto.foto(generico, 501, 0, VisibilidadeMidia.LIVRE, StatusArquivoMidia.VALIDADO);
        var atualizacaoForaDaSelecao = INSTANTE.plusDays(7);
        contexto.vinculos.add(new MidiaVinculoLeitura(id(103), zeta.getId(), id(103), TipoAnuncioMidia.FOTO,
                FinalidadeAnuncioMidia.GALERIA, 20, StatusAnuncioMidia.REJEITADA,
                VisibilidadeMidia.RESTRITA_18, atualizacaoForaDaSelecao));
        set(contexto.localizacoes.get(alfa.getId()), "atualizadoEm", INSTANTE.plusDays(8));
        when(contexto.derivacao.resolverPreviewPublicaLeitura(any(), any())).thenReturn(
                new MidiaRestritaDerivacaoService.ResultadoPreview(
                        previewPresente ? "https://cdn.invalid/preview.jpg" : null,
                        previewPresente ? null : MidiaRestritaDerivacaoService.PENDENTE_DERIVACAO_RESTRITA));

        // Fixed policy oracle: a blurred preview never turns the restricted-only ad into a public photo.
        var esperado = Map.of(zeta.getId(), true, alfa.getId(), true, somenteRestrita.getId(), false,
                semUrl.getId(), false, generico.getId(), false);
        var geral = contexto.elegibilidade.avaliar(contexto.anuncios, contexto.locaisPublicos());
        assertThat(indexabilidade(geral)).isEqualTo(esperado);
        assertThat(geral.get(zeta.getId()).ultimaAtualizacaoMidia()).isEqualTo(atualizacaoForaDaSelecao);
        verify(contexto.derivacao, times(2)).resolverPreviewPublicaLeitura(any(), any());
        clearInvocations(contexto.derivacao, contexto.arquivoRepository);

        assertThat(contexto.service.listarAnunciosIndexaveis()).containsExactly(
                new SitemapAnuncioPublicoDto("perfil-alfa", "GO", "goiania", null, INSTANTE.plusDays(8), true, true),
                new SitemapAnuncioPublicoDto("perfil-zeta", "GO", "goiania", null, atualizacaoForaDaSelecao, true, true));
        contexto.semPreviews();
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 10})
    void selecionaEnvelopeCompletoAntesDeFiltrarFotosLivresNosLimitesDoSitemap(int limite) {
        var contexto = new ContextoSitemap();
        // The general-path oracle resolves previews; an absent preview still returns a pending result.
        when(contexto.derivacao.resolverPreviewPublicaLeitura(any(), any())).thenReturn(
                new MidiaRestritaDerivacaoService.ResultadoPreview(
                        null, MidiaRestritaDerivacaoService.PENDENTE_DERIVACAO_RESTRITA));
        var fora = contexto.anuncio(10, "perfil-foto-fora");
        var dentro = contexto.anuncio(20, "perfil-foto-dentro");
        var flags = new PremiumPublicoFlagsDto(false, false, false, false, false, false,
                limite == 10, false, false, false, List.of());
        contexto.flags.put(fora.getId(), flags);
        contexto.flags.put(dentro.getId(), flags);
        for (var anuncio : contexto.anuncios) {
            for (int ordem = 0; ordem <= limite; ordem++) {
                boolean livre = ordem == 1 || ordem == 2 || ordem == limite
                        || (anuncio == dentro && ordem == limite - 1);
                long identidade = anuncio.getId().getLeastSignificantBits() * 100 + ordem;
                var vinculo = contexto.foto(anuncio, identidade, ordem,
                        livre ? VisibilidadeMidia.LIVRE : VisibilidadeMidia.RESTRITA_18,
                        ordem == 1 ? StatusArquivoMidia.REMOVIDO : StatusArquivoMidia.VALIDADO);
                if (ordem == 2) contexto.arquivos.removeIf(arquivo -> arquivo.id().equals(vinculo.arquivoMidiaId()));
            }
        }
        java.util.Collections.reverse(contexto.vinculos);
        var esperado = Map.of(fora.getId(), false, dentro.getId(), true);
        assertThat(indexabilidade(contexto.elegibilidade.avaliar(contexto.anuncios, contexto.locaisPublicos())))
                .isEqualTo(esperado);
        clearInvocations(contexto.derivacao, contexto.arquivoRepository);

        assertThat(contexto.service.listarAnunciosIndexaveis()).containsExactly(
                new SitemapAnuncioPublicoDto("perfil-foto-dentro", "GO", "goiania", null, INSTANTE, true, true));
        // Restricted/removed/missing positions are consumed; neither photo immediately after the limit is read.
        assertThat(contexto.ultimaConsultaArquivos).containsExactlyInAnyOrder(
                id(1001), id(1002), id(2001), id(2002), id(2000 + limite - 1));
        verify(contexto.arquivoRepository).findLeiturasPublicas(any());
        contexto.semPreviews();
    }

    @ParameterizedTest
    @ValueSource(strings = {"anuncio", "publicacao", "ultima_publicacao", "localizacao", "midia_fora_limite"})
    void preservaMaiorAtualizacaoDeCadaFonteNoDtoFinal(String fonte) {
        var contexto = new ContextoSitemap();
        var anuncio = contexto.anuncio(10, "perfil-atualizado");
        contexto.foto(anuncio, 101, 0, VisibilidadeMidia.LIVRE, StatusArquivoMidia.VALIDADO);
        var ultima = INSTANTE.plusDays(9);
        switch (fonte) {
            case "anuncio" -> set(anuncio, "atualizadoEm", ultima);
            case "publicacao" -> set(anuncio, "publicadoEm", ultima);
            case "ultima_publicacao" -> set(anuncio, "ultimaPublicacaoEm", ultima);
            case "localizacao" -> set(contexto.localizacoes.get(anuncio.getId()), "atualizadoEm", ultima);
            case "midia_fora_limite" -> contexto.vinculos.add(new MidiaVinculoLeitura(
                    id(102), anuncio.getId(), id(102), TipoAnuncioMidia.FOTO, FinalidadeAnuncioMidia.GALERIA,
                    20, StatusAnuncioMidia.REJEITADA, VisibilidadeMidia.RESTRITA_18, ultima));
            default -> throw new IllegalArgumentException(fonte);
        }

        assertThat(contexto.service.listarAnunciosIndexaveis()).containsExactly(
                new SitemapAnuncioPublicoDto("perfil-atualizado", "GO", "goiania", null, ultima, true, true));
        contexto.semPreviews();
    }

    @Test
    void previewIrrelevanteNaoPodeSerConsultadoMesmoQuandoSuaResolucaoFalharia() {
        var contexto = new ContextoSitemap();
        var anuncio = contexto.anuncio(10, "perfil-sem-dependencia-remota");
        contexto.foto(anuncio, 101, 0, VisibilidadeMidia.LIVRE, StatusArquivoMidia.VALIDADO);
        contexto.foto(anuncio, 102, 1, VisibilidadeMidia.RESTRITA_18, StatusArquivoMidia.VALIDADO);
        when(contexto.derivacao.resolverPreviewPublicaLeitura(any(), any()))
                .thenThrow(new AssertionError("sitemap nao pode resolver preview"));

        assertThat(contexto.service.listarAnunciosIndexaveis()).containsExactly(
                new SitemapAnuncioPublicoDto("perfil-sem-dependencia-remota", "GO", "goiania", null, INSTANTE, true, true));
        contexto.semPreviews();
    }

    @Test
    void falhaDaConsultaNecessariaNaoViraSitemapVazio() {
        var contexto = new ContextoSitemap();
        contexto.anuncio(10, "perfil-falha-consulta");
        var falha = new IllegalStateException("falha de consulta sintetica");
        when(contexto.midiaRepository.findLeiturasPublicas(any())).thenThrow(falha);

        assertThatThrownBy(contexto.service::listarAnunciosIndexaveis).isSameAs(falha);
        contexto.semPreviews();
    }

    private static final OffsetDateTime INSTANTE = OffsetDateTime.parse("2026-01-01T00:00:00Z");

    private static UUID id(long valor) {
        return new UUID(0, valor);
    }

    private static Map<UUID, Boolean> indexabilidade(
            Map<UUID, AnuncioSeoElegibilidadeConsultaService.Resultado> resultados) {
        var valores = new LinkedHashMap<UUID, Boolean>();
        resultados.forEach((id, resultado) -> valores.put(id, resultado.indexavel()));
        return valores;
    }

    /** Real selection, URL validation and policy; deterministic repository rows and no network. */
    private static final class ContextoSitemap {
        final AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
        final AnuncioLocalizacaoRepository localizacaoRepository = mock(AnuncioLocalizacaoRepository.class);
        final AnuncioMidiaRepository midiaRepository = mock(AnuncioMidiaRepository.class);
        final ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
        final EstadoRepository estadoRepository = mock(EstadoRepository.class);
        final CidadeRepository cidadeRepository = mock(CidadeRepository.class);
        final BairroRepository bairroRepository = mock(BairroRepository.class);
        final PremiumPublicoMapper premium = mock(PremiumPublicoMapper.class);
        final MidiaRestritaDerivacaoService derivacao = mock(MidiaRestritaDerivacaoService.class);
        final ObjectStorage storage = mock(ObjectStorage.class);
        final List<AnuncioEntity> anuncios = new ArrayList<>();
        final Map<UUID, AnuncioLocalizacaoEntity> localizacoes = new LinkedHashMap<>();
        final List<MidiaVinculoLeitura> vinculos = new ArrayList<>();
        final List<ArquivoPublicoLeitura> arquivos = new ArrayList<>();
        final Map<UUID, PremiumPublicoFlagsDto> flags = new LinkedHashMap<>();
        List<UUID> ultimaConsultaArquivos = List.of();
        final AnuncioSeoElegibilidadeConsultaService elegibilidade;
        final SitemapPublicoConsultaService service;

        @SuppressWarnings("unchecked")
        ContextoSitemap() {
            var estado = entity(EstadoEntity.class);
            set(estado, "id", id(1)); set(estado, "uf", "GO"); set(estado, "nome", "Goias");
            var cidade = entity(CidadeEntity.class);
            set(cidade, "id", id(2)); set(cidade, "estadoId", id(1));
            set(cidade, "nome", "Goiania"); set(cidade, "slug", "goiania");
            when(anuncioRepository.findPublicosComProprietarioAtivo()).thenAnswer(call -> List.copyOf(anuncios));
            when(localizacaoRepository.findByAnuncioIdIn(any())).thenAnswer(call -> List.copyOf(localizacoes.values()));
            when(estadoRepository.findAllById(any())).thenReturn(List.of(estado));
            when(cidadeRepository.findAllById(any())).thenReturn(List.of(cidade));
            when(bairroRepository.findAllById(any())).thenReturn(List.of());
            when(midiaRepository.findLeiturasPublicas(any())).thenAnswer(call -> List.copyOf(vinculos));
            when(arquivoRepository.findLeiturasPublicas(any())).thenAnswer(call -> {
                Collection<UUID> ids = call.getArgument(0);
                ultimaConsultaArquivos = List.copyOf(ids);
                return arquivos.stream().filter(arquivo -> ids.contains(arquivo.id())).toList();
            });
            when(premium.flagsMidiaPorAnuncioIds(any())).thenAnswer(call -> Map.copyOf(flags));
            var properties = new R2StorageProperties();
            properties.setPublicMediaBucket("public"); properties.setPublicMediaPrefix("public/");
            properties.setPrivateMediaBucket("private"); properties.setPrivateMediaPrefix("private/");
            ObjectProvider<ObjectStorage> provider = mock(ObjectProvider.class);
            when(provider.getIfAvailable()).thenReturn(storage);
            when(storage.publicUrl(any(), any())).thenAnswer(call -> Optional.of(
                    URI.create("https://cdn.invalid/" + call.getArgument(1))));
            var urls = new MidiaPublicaUrlService("producao", "https://site.invalid", provider, properties, derivacao);
            elegibilidade = new AnuncioSeoElegibilidadeConsultaService(midiaRepository, arquivoRepository,
                    new MidiaPublicaMapper(urls), premium, new AnuncioSeoIndexabilidadePolicy());
            service = new SitemapPublicoConsultaService(anuncioRepository, localizacaoRepository,
                    estadoRepository, cidadeRepository, bairroRepository, elegibilidade);
        }

        AnuncioEntity anuncio(long identidade, String slug) {
            var anuncio = entity(AnuncioEntity.class);
            set(anuncio, "id", id(identidade)); set(anuncio, "slug", slug);
            set(anuncio, "titulo", "Perfil completo " + slug); set(anuncio, "descricao", "x".repeat(130));
            set(anuncio, "status", StatusAnuncio.PUBLICADO); set(anuncio, "statusModeracao", StatusModeracaoAnuncio.APROVADO);
            set(anuncio, "atualizadoEm", INSTANTE); set(anuncio, "publicadoEm", INSTANTE);
            var local = entity(AnuncioLocalizacaoEntity.class);
            set(local, "anuncioId", anuncio.getId()); set(local, "estadoId", id(1)); set(local, "cidadeId", id(2));
            set(local, "atualizadoEm", INSTANTE);
            anuncios.add(anuncio); localizacoes.put(anuncio.getId(), local);
            return anuncio;
        }

        MidiaVinculoLeitura foto(AnuncioEntity anuncio, long identidade, int ordem,
                VisibilidadeMidia visibilidade, StatusArquivoMidia statusArquivo) {
            var vinculo = new MidiaVinculoLeitura(id(identidade), anuncio.getId(), id(identidade),
                    TipoAnuncioMidia.FOTO, FinalidadeAnuncioMidia.GALERIA, ordem,
                    StatusAnuncioMidia.PUBLICAVEL, visibilidade, INSTANTE);
            vinculos.add(vinculo);
            boolean livre = visibilidade == VisibilidadeMidia.LIVRE;
            arquivos.add(new ArquivoPublicoLeitura(vinculo.arquivoMidiaId(), "a".repeat(64), statusArquivo,
                    "R2", livre ? "public" : "private", (livre ? "public/" : "private/") + identidade + ".jpg",
                    "image/jpeg", 800, 600));
            return vinculo;
        }

        Map<UUID, LocalizacaoPublicaDto> locaisPublicos() {
            var locais = new LinkedHashMap<UUID, LocalizacaoPublicaDto>();
            anuncios.forEach(anuncio -> locais.put(anuncio.getId(),
                    new LocalizacaoPublicaDto("GO", "Goias", "Goiania", "goiania", null, null, null)));
            return locais;
        }

        void semPreviews() {
            verifyNoInteractions(derivacao);
            verify(storage, never()).exists(any(), any());
            verify(arquivoRepository, never()).findIdentidadesPreview(any());
        }
    }
}
