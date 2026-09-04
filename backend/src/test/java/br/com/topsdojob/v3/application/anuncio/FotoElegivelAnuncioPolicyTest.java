package br.com.topsdojob.v3.application.anuncio;

import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.anuncio.FotoElegivelAnuncioPolicy.UltimaFotoAprovadaException;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class FotoElegivelAnuncioPolicyTest {

    private final AnuncioMidiaRepository anuncioMidiaRepository = mock(AnuncioMidiaRepository.class);
    private final ArquivoMidiaRepository arquivoMidiaRepository = mock(ArquivoMidiaRepository.class);
    private final FotoElegivelAnuncioPolicy policy =
            new FotoElegivelAnuncioPolicy(anuncioMidiaRepository, arquivoMidiaRepository);
    private UUID anuncioId;

    @BeforeEach
    void setUp() {
        anuncioId = UUID.randomUUID();
        when(anuncioMidiaRepository.findByAnuncioIdForUpdate(anuncioId)).thenReturn(List.of());
        when(anuncioMidiaRepository.findFotosAprovadasElegiveisIds(anuncioId)).thenReturn(List.of());
        when(anuncioMidiaRepository.findFotosAguardandoDecisaoIds(anuncioId)).thenReturn(List.of());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matrizAprovacao")
    void matrizDeAprovacaoDistingueFotosElegiveisEAguardandoDecisao(
            String cenario,
            int aprovadas,
            int aguardando,
            String mensagemEsperada) {
        when(anuncioMidiaRepository.findFotosAprovadasElegiveisIds(anuncioId))
                .thenReturn(ids(aprovadas));
        when(anuncioMidiaRepository.findFotosAguardandoDecisaoIds(anuncioId))
                .thenReturn(ids(aguardando));

        if (mensagemEsperada == null) {
            assertThatCode(() -> policy.validarParaAprovacao(anuncioId))
                    .as(cenario)
                    .doesNotThrowAnyException();
            return;
        }
        assertThatThrownBy(() -> policy.validarParaAprovacao(anuncioId))
                .as(cenario)
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.getReason()).isEqualTo(mensagemEsperada);
                });
    }

    @Test
    void fotoAprovadaSemPendenciasPermiteAprovacaoETravaArquivosEmOrdem() {
        UUID arquivoMaior = UUID.fromString("00000000-0000-0000-0000-000000000022");
        UUID arquivoMenor = UUID.fromString("00000000-0000-0000-0000-000000000011");
        when(anuncioMidiaRepository.findByAnuncioIdForUpdate(anuncioId)).thenReturn(List.of(
                vinculo(UUID.randomUUID(), arquivoMaior),
                vinculo(UUID.randomUUID(), arquivoMenor)));
        when(anuncioMidiaRepository.findFotosAprovadasElegiveisIds(anuncioId))
                .thenReturn(List.of(UUID.randomUUID()));

        assertThatCode(() -> policy.validarParaAprovacao(anuncioId)).doesNotThrowAnyException();

        InOrder ordem = inOrder(anuncioMidiaRepository, arquivoMidiaRepository);
        ordem.verify(anuncioMidiaRepository).findByAnuncioIdForUpdate(anuncioId);
        ordem.verify(arquivoMidiaRepository).findByIdInForUpdate(List.of(arquivoMenor, arquivoMaior));
        ordem.verify(anuncioMidiaRepository).findFotosAprovadasElegiveisIds(anuncioId);
        ordem.verify(anuncioMidiaRepository).findFotosAguardandoDecisaoIds(anuncioId);
    }

    @ParameterizedTest
    @EnumSource(value = StatusAnuncio.class, names = {"PUBLICADO", "PAUSADO", "APROVADO"})
    void ultimaFotoElegivelDeAnuncioProtegidoNaoPodeSerRemovida(StatusAnuncio status) {
        UUID midiaId = UUID.randomUUID();
        AnuncioEntity anuncio = anuncio(status, StatusModeracaoAnuncio.APROVADO);
        when(anuncioMidiaRepository.findFotosAprovadasElegiveisIds(anuncio.getId()))
                .thenReturn(List.of(midiaId));

        assertThatThrownBy(() -> policy.validarRemocaoIndividual(anuncio, midiaId))
                .isInstanceOf(UltimaFotoAprovadaException.class)
                .hasMessageContaining(FotoElegivelAnuncioPolicy.MENSAGEM_ULTIMA_FOTO_APROVADA);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("midiasNaoElegiveisParaRemocao")
    void remocaoDeMidiaNaoElegivelNaoETratadaComoUltimaFoto(
            String cenario,
            StatusAnuncioMidia status,
            TipoAnuncioMidia tipo) {
        UUID midiaId = UUID.randomUUID();
        UUID fotoAprovadaQuePermanece = UUID.randomUUID();
        AnuncioEntity anuncio = anuncio(StatusAnuncio.PUBLICADO, StatusModeracaoAnuncio.APROVADO);
        when(anuncioMidiaRepository.findByAnuncioIdForUpdate(anuncio.getId()))
                .thenReturn(List.of(vinculo(
                        midiaId,
                        UUID.randomUUID(),
                        tipo,
                        status,
                        tipo == TipoAnuncioMidia.STORY
                                ? FinalidadeAnuncioMidia.STORY
                                : FinalidadeAnuncioMidia.GALERIA)));
        when(anuncioMidiaRepository.findFotosAprovadasElegiveisIds(anuncio.getId()))
                .thenReturn(List.of(fotoAprovadaQuePermanece));

        assertThatCode(() -> policy.validarRemocaoIndividual(anuncio, midiaId))
                .as(cenario)
                .doesNotThrowAnyException();
    }

    @Test
    void fotoAprovadaPodeSerRemovidaQuandoOutraElegivelPermanece() {
        UUID midiaId = UUID.randomUUID();
        AnuncioEntity anuncio = anuncio(StatusAnuncio.PAUSADO, StatusModeracaoAnuncio.APROVADO);
        when(anuncioMidiaRepository.findFotosAprovadasElegiveisIds(anuncio.getId()))
                .thenReturn(List.of(midiaId, UUID.randomUUID()));

        assertThatCode(() -> policy.validarRemocaoIndividual(anuncio, midiaId))
                .doesNotThrowAnyException();
    }

    @Test
    void anuncioNaoAprovadoNaoAtivaGuardDeRemocao() {
        AnuncioEntity anuncio = anuncio(StatusAnuncio.PENDENTE_REVISAO, StatusModeracaoAnuncio.PENDENTE);

        assertThatCode(() -> policy.validarRemocaoIndividual(anuncio, UUID.randomUUID()))
                .doesNotThrowAnyException();

        verify(anuncioMidiaRepository, never()).findByAnuncioIdForUpdate(anuncio.getId());
        verify(anuncioMidiaRepository, never()).findFotosAprovadasElegiveisIds(anuncio.getId());
    }

    @Test
    void detalheUsaAsDuasContagensAutoritativas() {
        when(anuncioMidiaRepository.findFotosAprovadasElegiveisIds(anuncioId))
                .thenReturn(List.of(UUID.randomUUID(), UUID.randomUUID()));
        when(anuncioMidiaRepository.findFotosAguardandoDecisaoIds(anuncioId))
                .thenReturn(List.of(UUID.randomUUID()));

        assertThat(policy.consultarTotais(anuncioId))
                .isEqualTo(new FotoElegivelAnuncioPolicy.Totais(2, 1));
    }

    private static Stream<Arguments> matrizAprovacao() {
        String semFoto = FotoElegivelAnuncioPolicy.MENSAGEM_SEM_FOTO_APROVADA;
        String aguardando = FotoElegivelAnuncioPolicy.MENSAGEM_FOTO_AGUARDANDO_DECISAO;
        return Stream.of(
                Arguments.of("zero fotos", 0, 0, semFoto),
                Arguments.of("somente PENDENTE", 0, 1, aguardando),
                Arguments.of("somente REJEITADA", 0, 0, semFoto),
                Arguments.of("uma PUBLICAVEL e VALIDADA", 1, 0, null),
                Arguments.of("uma aprovada mais uma PENDENTE", 1, 1, aguardando),
                Arguments.of("uma aprovada mais uma AJUSTE_SOLICITADO", 1, 1, aguardando),
                Arguments.of("duas aprovadas mais uma rejeitada", 2, 0, null),
                Arguments.of("somente VIDEO nao conta", 0, 0, semFoto),
                Arguments.of("somente STORY nao conta", 0, 0, semFoto),
                Arguments.of("somente KYC ou documento nao conta", 0, 0, semFoto),
                Arguments.of("midia de outro anuncio nao conta", 0, 0, semFoto),
                Arguments.of("arquivo nao VALIDADO nao conta", 0, 0, semFoto),
                Arguments.of("RESTRITA_18 sem preview pode contar", 1, 0, null));
    }

    private static Stream<Arguments> midiasNaoElegiveisParaRemocao() {
        return Stream.of(
                Arguments.of(
                        "foto PENDENTE pode ser removida",
                        StatusAnuncioMidia.PENDENTE,
                        TipoAnuncioMidia.FOTO),
                Arguments.of(
                        "foto REJEITADA pode ser removida",
                        StatusAnuncioMidia.REJEITADA,
                        TipoAnuncioMidia.FOTO),
                Arguments.of(
                        "foto REMOVIDA inelegivel nao e tratada como ultima foto",
                        StatusAnuncioMidia.REMOVIDA,
                        TipoAnuncioMidia.FOTO),
                Arguments.of(
                        "VIDEO inelegivel pode ser removido",
                        StatusAnuncioMidia.PUBLICAVEL,
                        TipoAnuncioMidia.VIDEO));
    }

    private List<UUID> ids(int quantidade) {
        List<UUID> ids = new ArrayList<>();
        for (int index = 0; index < quantidade; index++) {
            ids.add(UUID.randomUUID());
        }
        return List.copyOf(ids);
    }

    private AnuncioEntity anuncio(StatusAnuncio status, StatusModeracaoAnuncio moderacao) {
        StatusAnuncio statusValidoParaFixture = status == StatusAnuncio.APROVADO
                ? StatusAnuncio.PAUSADO
                : status;
        AnuncioEntity anuncio = AnuncioEntity.criarFixtureHomologacao(
                anuncioId,
                UUID.randomUUID(),
                "anuncio-policy",
                "Anuncio policy",
                "Descricao",
                statusValidoParaFixture,
                moderacao,
                OffsetDateTime.parse("2026-09-04T12:00:00Z"));
        if (status == StatusAnuncio.APROVADO) {
            set(anuncio, "status", StatusAnuncio.APROVADO);
        }
        return anuncio;
    }

    private AnuncioMidiaEntity vinculo(UUID id, UUID arquivoId) {
        return vinculo(
                id,
                arquivoId,
                TipoAnuncioMidia.FOTO,
                StatusAnuncioMidia.PUBLICAVEL,
                FinalidadeAnuncioMidia.GALERIA);
    }

    private AnuncioMidiaEntity vinculo(
            UUID id,
            UUID arquivoId,
            TipoAnuncioMidia tipo,
            StatusAnuncioMidia status,
            FinalidadeAnuncioMidia finalidade) {
        return AnuncioMidiaEntity.criarFixtureHomologacao(
                id,
                anuncioId,
                arquivoId,
                tipo,
                finalidade,
                0,
                status,
                VisibilidadeMidia.LIVRE,
                OffsetDateTime.parse("2026-09-04T12:00:00Z"));
    }
}
