package br.com.topsdojob.v3.application.publico.service;

import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.entity;
import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.mapper.AnuncioPublicoMapper;
import br.com.topsdojob.v3.application.publico.mapper.MidiaPublicaSeguraPolicy;
import br.com.topsdojob.v3.application.publico.mapper.MidiaPublicaMapper;
import br.com.topsdojob.v3.application.publico.mapper.SeoPublicoMapper;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoMapper;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioLocalizacaoEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.CidadeEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.EstadoEntity;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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
        var limiteMidiasService = mock(
                br.com.topsdojob.v3.application.publico.anunciante.midia.LimiteMidiasAnuncioService.class);
        when(limiteMidiasService.resolver(anuncioId)).thenReturn(
                new br.com.topsdojob.v3.application.publico.anunciante.midia.LimiteMidiasAnuncioService.Resultado(
                        4, 1, false));

        AnuncioPublicoConsultaService service = new AnuncioPublicoConsultaService(
                anuncioRepository,
                localizacaoRepository,
                mock(AnuncioMidiaRepository.class),
                mock(ArquivoMidiaRepository.class),
                new AnuncioPublicoMapper(new MidiaPublicaSeguraPolicy()),
                new MidiaPublicaMapper(new MidiaPublicaUrlService()),
                new SeoPublicoConsultaService(mock(SeoUrlRepository.class), new SeoPublicoMapper()),
                mock(IdadePublicaService.class),
                premiumMapper,
                estadoRepository,
                cidadeRepository,
                mock(BairroRepository.class),
                mock(PoliticaContatoPublicoService.class),
                mock(AnuncioSeoIndexabilidadePolicy.class),
                idadeAnuncianteService,
                limiteMidiasService);

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
                new SeoPublicoConsultaService(mock(SeoUrlRepository.class), new SeoPublicoMapper()),
                mock(IdadePublicaService.class),
                mock(PremiumPublicoMapper.class),
                mock(EstadoRepository.class),
                mock(CidadeRepository.class),
                mock(BairroRepository.class),
                mock(PoliticaContatoPublicoService.class),
                mock(AnuncioSeoIndexabilidadePolicy.class),
                mock(IdadeAnunciantePublicaService.class),
                mock(br.com.topsdojob.v3.application.publico.anunciante.midia.LimiteMidiasAnuncioService.class));

        assertThatThrownBy(() -> service.buscarPorSlug("slug-local"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
