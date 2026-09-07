package br.com.topsdojob.v3.application.publico.service;

import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.entity;
import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import br.com.topsdojob.v3.application.publico.dto.LocalizacaoPublicaDto;
import br.com.topsdojob.v3.application.publico.dto.MidiaPublicaDto;
import br.com.topsdojob.v3.application.publico.mapper.MidiaPublicaMapper;
import br.com.topsdojob.v3.persistence.repository.projection.MidiaVinculoLeitura;
import br.com.topsdojob.v3.persistence.repository.projection.ArquivoPublicoLeitura;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoMapper;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoFlagsDto;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.net.URI;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.server.ResponseStatusException;

class AnuncioSeoElegibilidadeConsultaServiceLocalidadesTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void coletaMantemSelecaoDoMapperSemAvaliarPoliticaOuResolverFotosLivres(boolean extra) {
        var anuncio = anuncios().get(0);
        var links = new ArrayList<AnuncioMidiaEntity>();
        var files = new LinkedHashMap<UUID, ArquivoMidiaEntity>();
        for (int i = 0; i < 14; i++) {
            var link = entity(AnuncioMidiaEntity.class);
            var file = entity(ArquivoMidiaEntity.class);
            UUID id = new UUID(i == 2 ? Long.MIN_VALUE : 1, i + 1);
            set(link, "id", id);
            set(link, "anuncioId", anuncio.getId());
            set(link, "arquivoMidiaId", id);
            set(link, "tipo", i == 12 ? TipoAnuncioMidia.STORY : TipoAnuncioMidia.FOTO);
            set(link, "finalidade", FinalidadeAnuncioMidia.GALERIA);
            set(link, "status", i == 13 ? StatusAnuncioMidia.REJEITADA : StatusAnuncioMidia.PUBLICAVEL);
            set(link, "visibilidadeMidia", i == 0 ? VisibilidadeMidia.LIVRE : VisibilidadeMidia.RESTRITA_18);
            Integer ordem = i == 11 ? null : Integer.valueOf(i >= 12 ? -1 : i == 3 ? 2 : i);
            set(link, "ordem", ordem);
            set(file, "id", id);
            set(file, "statusArquivo", i == 1 ? StatusArquivoMidia.REMOVIDO : StatusArquivoMidia.VALIDADO);
            links.add(link);
            files.put(id, file);
        }
        java.util.Collections.reverse(links); // Input order must not replace canonical UUID tie-breaking.
        var urls = mock(MidiaPublicaUrlService.class);
        var selected = new ArrayList<UUID>();
        when(urls.resolverPreviewRestrita(any())).thenAnswer(call -> {
            selected.add(((ArquivoMidiaEntity) call.getArgument(0)).getId());
            return new MidiaPublicaUrlService.ResultadoUrlPublica(null, "PENDENTE_DERIVACAO_RESTRITA");
        });
        when(urls.resolver(any(), any())).thenReturn(new MidiaPublicaUrlService.ResultadoUrlPublica("/livre", null));
        var mapper = new MidiaPublicaMapper(urls);
        mapper.publicas(links, files, false, extra ? 10 : 4, false);
        var expected = new ArrayList<UUID>();
        expected.add(new UUID(Long.MIN_VALUE, 3));
        for (int i = 3; i < (extra ? 10 : 4); i++) expected.add(new UUID(1, i + 1));
        // Independent explicit oracle, not equality between two uses of the new selector.
        assertThat(selected).containsExactlyElementsOf(expected);
        selected.clear();
        clearInvocations(urls);
        var linksRepository = mock(AnuncioMidiaRepository.class);
        when(linksRepository.findLeiturasPublicas(any())).thenReturn(links.stream().map(MidiaVinculoLeitura::de).toList());
        var fileRepository = mock(ArquivoMidiaRepository.class);
        when(fileRepository.findIdentidadesPreview(any())).thenAnswer(call -> {
            java.util.Collection<UUID> ids = call.getArgument(0);
            assertThat(ids).doesNotContain(new UUID(1, 1)); // Free archive is not hydrated provisionally.
            assertThat(ids).containsExactlyElementsOf(java.util.stream.Stream.concat(
                    java.util.stream.Stream.of(new UUID(1, 2)), expected.stream()).toList());
            return ids.stream().map(files::get).map(ArquivoPublicoLeitura::de).toList();
        });
        var premium = mock(PremiumPublicoMapper.class);
        when(premium.flagsMidiaPorAnuncioIds(any())).thenReturn(Map.of(anuncio.getId(), new PremiumPublicoFlagsDto(
                false, false, false, false, false, false, extra, false, false, false, List.of())));
        var policy = mock(AnuncioSeoIndexabilidadePolicy.class);
        var service = new AnuncioSeoElegibilidadeConsultaService(linksRepository, fileRepository, mapper, premium, policy);
        org.mockito.Mockito.doAnswer(call -> { selected.add(call.getArgument(0)); return null; })
                .when(urls).coletarPreviewRestrita(any(), any());
        service.coletarPreviewsPorIds(List.of(anuncio.getId()));
        assertThat(selected).containsExactlyElementsOf(expected);
        verify(urls, never()).resolver(any(), any());
        verify(urls, never()).resolverLeitura(any(), any());
        verify(urls, never()).resolverPreviewRestrita(any());
        verify(urls, never()).resolverPreviewRestritaLeitura(any());
        verifyNoInteractions(policy);
    }

    @Test
    void paraAntesDoSegundoAnuncioMesmoSemPreviewRestrito() {
        AtomicLong clock = new AtomicLong();
        MidiaPublicaMapper mapper = mock(MidiaPublicaMapper.class);
        when(mapper.fotosElegiveisLocalidades(any(), any(), anyInt(), anyBoolean())).thenAnswer(call -> {
            clock.set(Duration.ofMillis(100).toNanos());
            return List.of();
        });
        var service = service(mapper);
        var orcamento = new LocalidadesConsultaOrcamento(Duration.ofMillis(100), clock::get);
        assertThatThrownBy(() -> orcamento.executar(() -> service.avaliarLocalidades(anuncios(), Map.of())))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(mapper, times(1)).fotosElegiveisLocalidades(any(), any(), anyInt(), anyBoolean());
        assertThat(LocalidadesConsultaOrcamento.atualOuNulo()).isNull();
    }

    @Test
    void demaisChamadoresSemOrcamentoMantemAvaliacaoCompleta() {
        MidiaPublicaMapper mapper = mock(MidiaPublicaMapper.class);
        when(mapper.publicasLeituras(any(), any(), anyBoolean(), anyInt(), anyBoolean())).thenReturn(List.of());
        assertThat(service(mapper).avaliar(anuncios(), Map.of())).hasSize(2);
        verify(mapper, times(2)).publicasLeituras(any(), any(), anyBoolean(), anyInt(), anyBoolean());
    }

    @Test
    void coletaTambemInterrompeNoOrcamentoSemAnteciparAvaliacaoFinal() {
        AtomicLong clock = new AtomicLong();
        MidiaPublicaMapper mapper = mock(MidiaPublicaMapper.class);
        var links = mock(AnuncioMidiaRepository.class);
        when(links.findLeiturasPublicas(any())).thenAnswer(call -> {
            clock.set(Duration.ofMillis(100).toNanos());
            return List.of();
        });
        var premium = mock(PremiumPublicoMapper.class);
        when(premium.flagsMidiaPorAnuncioIds(any())).thenReturn(Map.of());
        var service = new AnuncioSeoElegibilidadeConsultaService(links, mock(ArquivoMidiaRepository.class),
                mapper, premium, mock(AnuncioSeoIndexabilidadePolicy.class));
        var orcamento = new LocalidadesConsultaOrcamento(Duration.ofMillis(100), clock::get);
        assertThatThrownBy(() -> orcamento.executar(() -> {
            service.coletarPreviews(anuncios());
            return null;
        })).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(mapper);
        assertThat(LocalidadesConsultaOrcamento.atualOuNulo()).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void equivalenciaTemOraculoFixoComPreviewPresenteOuAusente(boolean presente) {
        var contexto = new Contexto();
        var primeiro = anuncioValido(new UUID(0, 1));
        var segundo = anuncioValido(new UUID(0, 2));
        var terceiro = anuncioValido(new UUID(0, 3));
        var livre = link(new UUID(0, 11), primeiro.getId(), VisibilidadeMidia.LIVRE, 0);
        var restrita = link(new UUID(0, 12), primeiro.getId(), VisibilidadeMidia.RESTRITA_18, 1);
        var somenteRestrita = link(new UUID(0, 21), segundo.getId(), VisibilidadeMidia.RESTRITA_18, 0);
        var livreSemUrl = link(new UUID(0, 31), terceiro.getId(), VisibilidadeMidia.LIVRE, 0);
        var outraRestrita = link(new UUID(0, 32), terceiro.getId(), VisibilidadeMidia.RESTRITA_18, 1);
        contexto.dados(List.of(livre, restrita, somenteRestrita, livreSemUrl, outraRestrita), List.of(
                arquivo(livre, StatusArquivoMidia.VALIDADO, "R2", "public", "public/livre.jpg"),
                arquivo(restrita), arquivo(somenteRestrita),
                arquivo(livreSemUrl, StatusArquivoMidia.VALIDADO, "desconhecido", "public", "public/sem.jpg"),
                arquivo(outraRestrita)));
        when(contexto.derivacao.resolverPreviewPublicaLeitura(any(), any())).thenReturn(
                new MidiaRestritaDerivacaoService.ResultadoPreview(
                        presente ? "https://cdn.invalid/preview.jpg" : null,
                        presente ? null : "PENDENTE_DERIVACAO_RESTRITA"));
        var anuncios = List.of(primeiro, segundo, terceiro);
        var localizacoes = Map.of(primeiro.getId(), localizacao(), segundo.getId(), localizacao(),
                terceiro.getId(), localizacao());
        // This expected result is fixed from the policy: only the first ad has a free real URL.
        var esperado = Map.of(primeiro.getId(), true, segundo.getId(), false, terceiro.getId(), false);
        assertThat(indexabilidade(contexto.service.avaliar(anuncios, localizacoes))).isEqualTo(esperado);
        verify(contexto.derivacao, times(3)).resolverPreviewPublicaLeitura(any(), any());
        clearInvocations(contexto.derivacao, contexto.urls, contexto.storage);

        assertThat(indexabilidade(contexto.service.avaliarLocalidades(anuncios, localizacoes)))
                .isEqualTo(esperado);
        contexto.semPreviews();
        verify(contexto.storage).publicUrl(StorageArea.PUBLIC_MEDIA, "public/livre.jpg");
        verifyNoMoreInteractions(contexto.storage);
    }

    @ParameterizedTest
    @ValueSource(strings = {"presente", "ausente", "lento", "erro"})
    void somenteRestritasNaoContamNemResolvemPreviewIndependentementeDoStorage(String resposta) {
        var contexto = new Contexto();
        var anuncio = anuncioValido(new UUID(0, 1));
        var restrita = link(new UUID(0, 11), anuncio.getId(), VisibilidadeMidia.RESTRITA_18, 0);
        contexto.dados(List.of(restrita), List.of(arquivo(restrita)));
        when(contexto.derivacao.resolverPreviewPublicaLeitura(any(), any())).thenAnswer(call -> {
            if (resposta.equals("erro")) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
            if (resposta.equals("lento")) Thread.sleep(10_000);
            return new MidiaRestritaDerivacaoService.ResultadoPreview(
                    resposta.equals("ausente") ? null : "https://cdn.invalid/preview.jpg",
                    resposta.equals("ausente") ? "PENDENTE_DERIVACAO_RESTRITA" : null);
        });

        assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
                assertThat(contexto.avaliar(anuncio).indexavel()).isFalse());
        contexto.semPreviews();
        verifyNoInteractions(contexto.storage);
        verify(contexto.arquivos, never()).findLeiturasPublicas(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"sem_arquivo", "removido", "provider", "bucket", "prefixo", "sem_url"})
    void fotoLivreNaoBastaSemArquivoEUrlPublicaValidos(String invalido) {
        var contexto = new Contexto();
        var anuncio = anuncioValido(new UUID(0, 1));
        var livre = link(new UUID(0, 11), anuncio.getId(), VisibilidadeMidia.LIVRE, 0);
        var file = arquivo(livre,
                invalido.equals("removido") ? StatusArquivoMidia.REMOVIDO : StatusArquivoMidia.VALIDADO,
                invalido.equals("provider") ? "S3_PRIVADO" : "R2",
                invalido.equals("bucket") ? "private" : "public",
                invalido.equals("prefixo") ? "private/livre.jpg" : "public/livre.jpg");
        contexto.dados(List.of(livre), invalido.equals("sem_arquivo") ? List.of() : List.of(file));
        if (invalido.equals("sem_url")) {
            when(contexto.storage.publicUrl(StorageArea.PUBLIC_MEDIA, "public/livre.jpg"))
                    .thenReturn(Optional.empty());
        }

        assertThat(contexto.avaliar(anuncio).indexavel()).isFalse();
        contexto.semPreviews();
        if (invalido.equals("sem_url")) {
            verify(contexto.storage).publicUrl(StorageArea.PUBLIC_MEDIA, "public/livre.jpg");
            verifyNoMoreInteractions(contexto.storage);
        } else {
            verifyNoInteractions(contexto.storage);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"valida", "nome_invalido", "mime_invalido", "base_insegura", "base_com_query"})
    void origemPublicaPreservadaMantemValidacaoSemAcessoRemoto(String origem) {
        var contexto = new Contexto();
        contexto.properties.setPreservedPublicMediaBucket("legacy");
        contexto.properties.setPreservedPublicMediaPrefix("original/");
        contexto.properties.setPreservedPublicBaseUrl(switch (origem) {
            case "base_insegura" -> "http://legacy.invalid";
            case "base_com_query" -> "https://legacy.invalid?token=1";
            default -> "https://legacy.invalid";
        });
        var anuncio = anuncioValido(new UUID(0, 1));
        var livre = link(new UUID(0, 11), anuncio.getId(), VisibilidadeMidia.LIVRE, 0);
        var file = new ArquivoPublicoLeitura(livre.arquivoMidiaId(), "a".repeat(64),
                StatusArquivoMidia.VALIDADO, "R2", "legacy",
                "original/" + (origem.equals("nome_invalido") ? "foto.jpg" : "a".repeat(32) + ".jpg"),
                origem.equals("mime_invalido") ? "video/mp4" : "image/jpeg", 800, 600);
        contexto.dados(List.of(livre), List.of(file));

        assertThat(contexto.avaliar(anuncio).indexavel()).isEqualTo(origem.equals("valida"));
        if (origem.equals("valida")) {
            assertThat(contexto.mapper.fotosElegiveisLocalidades(List.of(livre), Map.of(file.id(), file), 4, false))
                    .singleElement().satisfies(dto -> assertThat(dto.urlPublica())
                            .isEqualTo("https://legacy.invalid/original/" + "a".repeat(32) + ".jpg"));
        }
        contexto.semPreviews();
        verifyNoInteractions(contexto.storage);
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 10})
    void posicoesRestritasInvalidasESemArquivoNaoPromovemLivrePosterior(int limite) {
        var contexto = new Contexto();
        var anuncio = anuncioValido(new UUID(0, 1));
        contexto.extras(anuncio.getId(), limite == 10);
        var links = new ArrayList<MidiaVinculoLeitura>();
        var files = new ArrayList<ArquivoPublicoLeitura>();
        for (int i = 0; i <= limite; i++) {
            var item = link(new UUID(0, 100 + i), anuncio.getId(),
                    i == 1 || i == 2 || i == limite ? VisibilidadeMidia.LIVRE : VisibilidadeMidia.RESTRITA_18, i);
            if (i == 2) item = new MidiaVinculoLeitura(item.id(), item.anuncioId(), null, item.tipo(),
                    item.finalidade(), item.ordem(), item.status(), item.visibilidadeMidia(), item.atualizadoEm());
            links.add(item);
            if (i != 2) files.add(arquivo(item,
                    i == 1 ? StatusArquivoMidia.REMOVIDO : StatusArquivoMidia.VALIDADO,
                    "R2", item.visibilidadeMidia() == VisibilidadeMidia.LIVRE ? "public" : "private",
                    (item.visibilidadeMidia() == VisibilidadeMidia.LIVRE ? "public/" : "private/") + i + ".jpg"));
        }
        java.util.Collections.reverse(links);
        contexto.dados(links, files);
        assertThat(contexto.avaliar(anuncio).indexavel()).isFalse();
        assertThat(contexto.mapper.fotosElegiveisLocalidades(links, arquivosPorId(files), limite, false)).isEmpty();
        verifyNoInteractions(contexto.storage);

        var idUltimaPosicao = new UUID(0, 100 + limite - 1);
        links.replaceAll(item -> item.id().equals(idUltimaPosicao)
                ? link(item.id(), anuncio.getId(), VisibilidadeMidia.LIVRE, limite - 1) : item);
        files.removeIf(item -> item.id().equals(idUltimaPosicao));
        files.add(arquivo(link(idUltimaPosicao, anuncio.getId(), VisibilidadeMidia.LIVRE, limite - 1)));
        contexto.dados(links, files);
        assertThat(contexto.avaliar(anuncio).indexavel()).isTrue();
        assertThat(contexto.mapper.fotosElegiveisLocalidades(links, arquivosPorId(files), limite, false))
                .extracting(MidiaPublicaDto::id).containsExactly(idUltimaPosicao);
        contexto.semPreviews();
    }

    @Test
    void selecaoFocalPreservaOrdemNulaUuidComSinalStatusFinalidadeETipo() {
        var contexto = new Contexto();
        UUID anuncioId = new UUID(0, 1);
        UUID negativoAlto = new UUID(Long.MIN_VALUE, 1);
        UUID negativoBaixo = new UUID(1, Long.MIN_VALUE);
        UUID positivo = new UUID(1, 1);
        UUID ordemNula = new UUID(0, 4);
        var links = new ArrayList<>(List.of(
                link(positivo, anuncioId, VisibilidadeMidia.LIVRE, 0),
                link(ordemNula, anuncioId, VisibilidadeMidia.LIVRE, null),
                link(negativoBaixo, anuncioId, VisibilidadeMidia.LIVRE, 0),
                link(negativoAlto, anuncioId, VisibilidadeMidia.RESTRITA_18, 0)));
        links.add(new MidiaVinculoLeitura(new UUID(0, 10), anuncioId, new UUID(0, 10),
                TipoAnuncioMidia.FOTO, FinalidadeAnuncioMidia.GALERIA, -1,
                StatusAnuncioMidia.REJEITADA, VisibilidadeMidia.LIVRE, null));
        links.add(new MidiaVinculoLeitura(new UUID(0, 11), anuncioId, new UUID(0, 11),
                TipoAnuncioMidia.FOTO, FinalidadeAnuncioMidia.STORY, -1,
                StatusAnuncioMidia.PUBLICAVEL, VisibilidadeMidia.LIVRE, null));
        links.add(new MidiaVinculoLeitura(new UUID(0, 12), anuncioId, new UUID(0, 12),
                TipoAnuncioMidia.STORY, FinalidadeAnuncioMidia.GALERIA, -1,
                StatusAnuncioMidia.PUBLICAVEL, VisibilidadeMidia.LIVRE, null));
        links.add(new MidiaVinculoLeitura(new UUID(0, 13), anuncioId, new UUID(0, 13),
                TipoAnuncioMidia.VIDEO, FinalidadeAnuncioMidia.GALERIA, -1,
                StatusAnuncioMidia.PUBLICAVEL, VisibilidadeMidia.LIVRE, null));
        links.add(new MidiaVinculoLeitura(new UUID(0, 14), anuncioId, new UUID(0, 14),
                null, null, -1, StatusAnuncioMidia.PUBLICAVEL, VisibilidadeMidia.LIVRE, null));
        var files = arquivosPorId(links.stream().map(this::arquivo).toList());
        // The restricted negative-high UUID consumes position 1, even without becoming a DTO.
        assertThat(contexto.mapper.fotosElegiveisLocalidades(links, files, 3, true))
                .extracting(MidiaPublicaDto::id).containsExactly(negativoBaixo, positivo);
        assertThat(contexto.mapper.fotosElegiveisLocalidades(links, files, 4, false))
                .extracting(MidiaPublicaDto::id).containsExactly(negativoBaixo, positivo, ordemNula);
        contexto.semPreviews();
    }

    @ParameterizedTest
    @ValueSource(strings = {"titulo", "descricao", "slug", "publicacao", "moderacao", "removido",
            "uf", "cidade", "cidade_slug"})
    void policyOriginalContinuaDecidindoOsDemaisCriterios(String criterio) {
        var contexto = new Contexto();
        var anuncio = anuncioValido(new UUID(0, 1));
        var livre = link(new UUID(0, 11), anuncio.getId(), VisibilidadeMidia.LIVRE, 0);
        contexto.dados(List.of(livre), List.of(arquivo(livre)));
        assertThat(contexto.avaliar(anuncio).indexavel()).isTrue();
        LocalizacaoPublicaDto local = localizacao();
        switch (criterio) {
            case "titulo" -> set(anuncio, "titulo", "acompanhante 1");
            case "descricao" -> set(anuncio, "descricao", "Curta.");
            case "slug" -> set(anuncio, "slug", "teste");
            case "publicacao" -> set(anuncio, "status", StatusAnuncio.PAUSADO);
            case "moderacao" -> set(anuncio, "statusModeracao", StatusModeracaoAnuncio.PENDENTE);
            case "removido" -> set(anuncio, "removidoEm", OffsetDateTime.parse("2026-01-02T00:00:00Z"));
            case "uf" -> local = new LocalizacaoPublicaDto("", "SP", "Sao Paulo", "sao-paulo", null, null, null);
            case "cidade" -> local = new LocalizacaoPublicaDto("SP", "SP", "", "sao-paulo", null, null, null);
            case "cidade_slug" -> local = new LocalizacaoPublicaDto("SP", "SP", "Sao Paulo", "", null, null, null);
            default -> throw new IllegalArgumentException(criterio);
        }
        assertThat(contexto.service.avaliarLocalidades(List.of(anuncio), Map.of(anuncio.getId(), local))
                .get(anuncio.getId()).indexavel()).isFalse();
        contexto.semPreviews();
    }

    @Test
    void ultimaAtualizacaoIncluiRestritaForaDoLimiteERejeitada() {
        var contexto = new Contexto();
        var anuncio = anuncioValido(new UUID(0, 1));
        var livre = link(new UUID(0, 11), anuncio.getId(), VisibilidadeMidia.LIVRE, 0);
        var atualizado = OffsetDateTime.parse("2026-01-05T12:34:56Z");
        var rejeitada = new MidiaVinculoLeitura(new UUID(0, 12), anuncio.getId(), new UUID(0, 12),
                TipoAnuncioMidia.FOTO, FinalidadeAnuncioMidia.GALERIA, 20,
                StatusAnuncioMidia.REJEITADA, VisibilidadeMidia.RESTRITA_18, atualizado);
        contexto.dados(List.of(livre, rejeitada), List.of(arquivo(livre)));
        assertThat(contexto.avaliar(anuncio)).isEqualTo(
                new AnuncioSeoElegibilidadeConsultaService.Resultado(true, atualizado));
        contexto.semPreviews();
    }

    @Test
    void falhaRealDeConsultaNaoViraResultadoVazio() {
        var contexto = new Contexto();
        var falha = new IllegalStateException("falha real de consulta");
        when(contexto.links.findLeiturasPublicas(any())).thenThrow(falha);
        assertThatThrownBy(() -> contexto.avaliar(anuncioValido(new UUID(0, 1)))).isSameAs(falha);
        contexto.semPreviews();
    }

    @Test
    void galeriaProjetadaPreservaBloqueioOriginalIdadePreviewPendenciaEFalhaTecnica() {
        var contexto = new Contexto();
        var restrita = link(new UUID(0, 11), new UUID(0, 1), VisibilidadeMidia.RESTRITA_18, 0);
        var files = Map.of(restrita.arquivoMidiaId(), arquivo(restrita));
        when(contexto.derivacao.resolverPreviewPublicaLeitura(any(), any())).thenReturn(
                new MidiaRestritaDerivacaoService.ResultadoPreview("https://cdn.invalid/preview.jpg", null));
        assertThat(contexto.mapper.publicasLeituras(List.of(restrita), files, false, 4, false))
                .singleElement().satisfies(dto -> {
                    assertThat(dto.id()).isEqualTo(restrita.id());
                    assertThat(dto.autorizada()).isFalse();
                    assertThat(dto.urlPublica()).isNull();
                    assertThat(dto.previewUrl()).isEqualTo("https://cdn.invalid/preview.jpg");
                    assertThat(dto.pendenciaMidia()).isEqualTo("MIDIA_RESTRITA_IDADE");
                });
        assertThat(contexto.mapper.publicasLeituras(List.of(restrita), files, true, 4, false))
                .singleElement().satisfies(dto -> {
                    assertThat(dto.autorizada()).isTrue();
                    assertThat(dto.urlPublica()).isEqualTo("/api/public/compliance/visitor/media/" + restrita.id());
                    assertThat(dto.previewUrl()).isEqualTo("https://cdn.invalid/preview.jpg");
                    assertThat(dto.pendenciaMidia()).isNull();
                });
        when(contexto.derivacao.resolverPreviewPublicaLeitura(any(), any())).thenReturn(
                new MidiaRestritaDerivacaoService.ResultadoPreview(null, "PENDENTE_DERIVACAO_RESTRITA"));
        assertThat(contexto.mapper.publicasLeituras(List.of(restrita), files, false, 4, false))
                .singleElement().satisfies(dto -> {
                    assertThat(dto.urlPublica()).isNull();
                    assertThat(dto.previewUrl()).isNull();
                    assertThat(dto.pendenciaMidia()).isEqualTo("PENDENTE_DERIVACAO_RESTRITA");
                });
        when(contexto.derivacao.resolverPreviewPublicaLeitura(any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> contexto.mapper.publicasLeituras(List.of(restrita), files, false, 4, false))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(contexto.storage);
    }

    private Map<UUID, Boolean> indexabilidade(Map<UUID, AnuncioSeoElegibilidadeConsultaService.Resultado> resultados) {
        var saida = new LinkedHashMap<UUID, Boolean>();
        resultados.forEach((id, resultado) -> saida.put(id, resultado.indexavel()));
        return saida;
    }

    private Map<UUID, ArquivoPublicoLeitura> arquivosPorId(List<ArquivoPublicoLeitura> files) {
        var resultado = new LinkedHashMap<UUID, ArquivoPublicoLeitura>();
        files.forEach(file -> resultado.put(file.id(), file));
        return resultado;
    }

    private MidiaVinculoLeitura link(UUID id, UUID anuncio, VisibilidadeMidia visibilidade, Integer ordem) {
        return new MidiaVinculoLeitura(id, anuncio, id, TipoAnuncioMidia.FOTO, FinalidadeAnuncioMidia.GALERIA,
                ordem, StatusAnuncioMidia.PUBLICAVEL, visibilidade, OffsetDateTime.parse("2026-01-01T00:00:00Z"));
    }

    private ArquivoPublicoLeitura arquivo(MidiaVinculoLeitura link) {
        boolean livre = link.visibilidadeMidia() == VisibilidadeMidia.LIVRE;
        return arquivo(link, StatusArquivoMidia.VALIDADO, "R2", livre ? "public" : "private",
                (livre ? "public/" : "private/") + link.id() + ".jpg");
    }

    private ArquivoPublicoLeitura arquivo(MidiaVinculoLeitura link, StatusArquivoMidia status,
            String provider, String bucket, String key) {
        return new ArquivoPublicoLeitura(link.arquivoMidiaId(), "a".repeat(64), status,
                provider, bucket, key, "image/jpeg", 800, 600);
    }

    private static AnuncioEntity anuncioValido(UUID id) {
        var anuncio = entity(AnuncioEntity.class);
        set(anuncio, "id", id);
        set(anuncio, "titulo", "Perfil completo e independente");
        set(anuncio, "descricao", "Descricao detalhada e suficientemente extensa do atendimento e do perfil. ".repeat(3));
        set(anuncio, "slug", "perfil-completo-independente-" + id);
        set(anuncio, "status", StatusAnuncio.PUBLICADO);
        set(anuncio, "statusModeracao", StatusModeracaoAnuncio.APROVADO);
        return anuncio;
    }

    private static LocalizacaoPublicaDto localizacao() {
        return new LocalizacaoPublicaDto("SP", "Sao Paulo", "Sao Paulo", "sao-paulo", null, null, null);
    }

    private static final class Contexto {
        final AnuncioMidiaRepository links = mock(AnuncioMidiaRepository.class);
        final ArquivoMidiaRepository arquivos = mock(ArquivoMidiaRepository.class);
        final PremiumPublicoMapper premium = mock(PremiumPublicoMapper.class);
        final MidiaRestritaDerivacaoService derivacao = mock(MidiaRestritaDerivacaoService.class);
        final ObjectStorage storage = mock(ObjectStorage.class);
        final R2StorageProperties properties = new R2StorageProperties();
        final MidiaPublicaUrlService urls;
        final MidiaPublicaMapper mapper;
        final AnuncioSeoElegibilidadeConsultaService service;

        @SuppressWarnings("unchecked")
        Contexto() {
            properties.setPublicMediaBucket("public");
            properties.setPublicMediaPrefix("public/");
            properties.setPrivateMediaBucket("private");
            properties.setPrivateMediaPrefix("private/");
            ObjectProvider<ObjectStorage> provider = mock(ObjectProvider.class);
            when(provider.getIfAvailable()).thenReturn(storage);
            when(storage.publicUrl(any(), any())).thenAnswer(call -> Optional.of(
                    URI.create("https://cdn.invalid/" + call.getArgument(1))));
            urls = spy(new MidiaPublicaUrlService("producao", "https://site.invalid", provider, properties, derivacao));
            mapper = new MidiaPublicaMapper(urls);
            when(premium.flagsMidiaPorAnuncioIds(any())).thenReturn(Map.of());
            service = new AnuncioSeoElegibilidadeConsultaService(links, arquivos, mapper, premium,
                    new AnuncioSeoIndexabilidadePolicy());
        }

        void dados(List<MidiaVinculoLeitura> vinculos, List<ArquivoPublicoLeitura> files) {
            when(links.findLeiturasPublicas(any())).thenReturn(List.copyOf(vinculos));
            org.mockito.Mockito.doAnswer(call -> {
                java.util.Collection<UUID> ids = call.getArgument(0);
                return files.stream().filter(file -> ids.contains(file.id())).toList();
            }).when(arquivos).findLeiturasPublicas(any());
        }

        void extras(UUID anuncio, boolean extra) {
            when(premium.flagsMidiaPorAnuncioIds(any())).thenReturn(Map.of(anuncio,
                    new PremiumPublicoFlagsDto(false, false, false, false, false, false,
                            extra, false, false, false, List.of())));
        }

        AnuncioSeoElegibilidadeConsultaService.Resultado avaliar(AnuncioEntity anuncio) {
            return service.avaliarLocalidades(List.of(anuncio), Map.of(anuncio.getId(), localizacao())).get(anuncio.getId());
        }

        void semPreviews() {
            verifyNoInteractions(derivacao);
            verify(urls, never()).resolverPreviewRestrita(any());
            verify(urls, never()).resolverPreviewRestritaLeitura(any());
            verify(urls, never()).coletarPreviewRestrita(any(), any());
            verify(arquivos, never()).findIdentidadesPreview(any());
        }
    }

    private AnuncioSeoElegibilidadeConsultaService service(MidiaPublicaMapper mapper) {
        var vinculos = mock(AnuncioMidiaRepository.class);
        when(vinculos.findLeiturasPublicas(any())).thenReturn(List.of());
        var premium = mock(PremiumPublicoMapper.class);
        when(premium.flagsMidiaPorAnuncioIds(any())).thenReturn(Map.of());
        return new AnuncioSeoElegibilidadeConsultaService(vinculos, mock(ArquivoMidiaRepository.class),
                mapper, premium, new AnuncioSeoIndexabilidadePolicy());
    }

    private List<AnuncioEntity> anuncios() {
        AnuncioEntity primeiro = entity(AnuncioEntity.class);
        AnuncioEntity segundo = entity(AnuncioEntity.class);
        set(primeiro, "id", UUID.randomUUID());
        set(segundo, "id", UUID.randomUUID());
        return List.of(primeiro, segundo);
    }
}
