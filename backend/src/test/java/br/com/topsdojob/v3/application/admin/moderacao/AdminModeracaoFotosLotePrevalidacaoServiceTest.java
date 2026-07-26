package br.com.topsdojob.v3.application.admin.moderacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecidirFotosLoteRequestDto;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecisaoFotoLoteAcao;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecisaoFotoLoteItemRequestDto;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoUsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

class AdminModeracaoFotosLotePrevalidacaoServiceTest {

    private final AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
    private final AnuncioMidiaRepository midiaRepository = mock(AnuncioMidiaRepository.class);
    private final ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
    private final DocumentoUsuarioRepository documentoRepository =
            mock(DocumentoUsuarioRepository.class);
    private final AdminModeracaoFotosLotePrevalidacaoService service =
            new AdminModeracaoFotosLotePrevalidacaoService(
                    anuncioRepository,
                    midiaRepository,
                    arquivoRepository,
                    documentoRepository);
    private final UUID anuncioId = UUID.randomUUID();
    private final AdminUserPrincipal ator = principal(PapelUsuario.MODERADOR);

    @BeforeEach
    void setUp() {
        AnuncioEntity anuncio = mock(AnuncioEntity.class);
        when(anuncio.getStatus()).thenReturn(StatusAnuncio.PENDENTE_REVISAO);
        when(anuncioRepository.findByIdForModeration(anuncioId)).thenReturn(Optional.of(anuncio));
        when(documentoRepository
                .existsByArquivoMidiaIdAndRemovidoEmIsNullAndExpurgadoEmIsNull(any()))
                .thenReturn(false);
    }

    @Test
    void prevalidaTodoOLoteMistoSemCompartilharObservacoes() {
        Fixture livre = fixture(StatusAnuncioMidia.PENDENTE, StatusArquivoMidia.PENDENTE);
        Fixture restrita = fixture(StatusAnuncioMidia.AJUSTE_SOLICITADO, StatusArquivoMidia.PENDENTE);
        Fixture excluir = fixture(StatusAnuncioMidia.PENDENTE, StatusArquivoMidia.PENDENTE);
        preparar(livre, restrita, excluir);

        var resultado = service.validar(anuncioId, new AdminDecidirFotosLoteRequestDto(List.of(
                item(livre.midiaId(), AdminDecisaoFotoLoteAcao.APROVAR, VisibilidadeMidia.LIVRE, "residual"),
                item(restrita.midiaId(), AdminDecisaoFotoLoteAcao.APROVAR, VisibilidadeMidia.RESTRITA_18, "contexto individual"),
                item(excluir.midiaId(), AdminDecisaoFotoLoteAcao.EXCLUIR, null, null))), ator);

        assertThat(resultado.itens()).hasSize(3);
        assertThat(resultado.itens().get(0).observacao()).isNull();
        assertThat(resultado.itens().get(1).observacao()).isEqualTo("contexto individual");
        assertThat(resultado.itens().get(2).classificacao()).isNull();
        assertThat(resultado.itens()).noneMatch(
                AdminModeracaoFotosLotePrevalidacaoService.ItemValidado::jaProcessada);
    }

    @Test
    void rejeitaMediaDuplicadaAntesDeQualquerExecucao() {
        Fixture foto = fixture(StatusAnuncioMidia.PENDENTE, StatusArquivoMidia.PENDENTE);

        assertThatThrownBy(() -> service.validar(
                anuncioId,
                new AdminDecidirFotosLoteRequestDto(List.of(
                        item(foto.midiaId(), AdminDecisaoFotoLoteAcao.APROVAR, VisibilidadeMidia.LIVRE, null),
                        item(foto.midiaId(), AdminDecisaoFotoLoteAcao.EXCLUIR, null, null))),
                ator))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400")
                .hasMessageContaining("nao duplicado");
    }

    @Test
    void rejeitaVideoNoContratoDeFotos() {
        Fixture video = fixture(StatusAnuncioMidia.PENDENTE, StatusArquivoMidia.PENDENTE);
        when(video.midia().getTipo()).thenReturn(TipoAnuncioMidia.VIDEO);
        preparar(video);

        assertThatThrownBy(() -> service.validar(
                anuncioId,
                new AdminDecidirFotosLoteRequestDto(List.of(
                        item(video.midiaId(), AdminDecisaoFotoLoteAcao.APROVAR, VisibilidadeMidia.RESTRITA_18, null))),
                ator))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400")
                .hasMessageContaining("videos e stories");
    }

    @Test
    void retryIdenticoReconheceFotoJaAprovadaSemNovaDecisao() {
        Fixture foto = fixture(StatusAnuncioMidia.PUBLICAVEL, StatusArquivoMidia.VALIDADO);
        when(foto.midia().getVisibilidadeMidia()).thenReturn(VisibilidadeMidia.LIVRE);
        preparar(foto);

        var resultado = service.validar(
                anuncioId,
                new AdminDecidirFotosLoteRequestDto(List.of(
                        item(foto.midiaId(), AdminDecisaoFotoLoteAcao.APROVAR, VisibilidadeMidia.LIVRE, null))),
                ator);

        assertThat(resultado.itens()).singleElement()
                .satisfies(item -> assertThat(item.jaProcessada()).isTrue());
    }

    @Test
    void permiteExcluirFotoPublicavelEValidada() {
        Fixture foto = fixture(StatusAnuncioMidia.PUBLICAVEL, StatusArquivoMidia.VALIDADO);
        when(foto.midia().getVisibilidadeMidia()).thenReturn(VisibilidadeMidia.RESTRITA_18);
        preparar(foto);

        var resultado = service.validar(
                anuncioId,
                new AdminDecidirFotosLoteRequestDto(List.of(
                        item(foto.midiaId(), AdminDecisaoFotoLoteAcao.EXCLUIR, null, null))),
                ator);

        assertThat(resultado.itens()).singleElement()
                .satisfies(item -> {
                    assertThat(item.decisao()).isEqualTo(AdminDecisaoFotoLoteAcao.EXCLUIR);
                    assertThat(item.jaProcessada()).isFalse();
                });
    }

    @Test
    void retryDeExclusaoReconheceFotoJaRemovida() {
        Fixture foto = fixture(StatusAnuncioMidia.REMOVIDA, StatusArquivoMidia.REMOVIDO);
        preparar(foto);

        var resultado = service.validar(
                anuncioId,
                new AdminDecidirFotosLoteRequestDto(List.of(
                        item(foto.midiaId(), AdminDecisaoFotoLoteAcao.EXCLUIR, null, null))),
                ator);

        assertThat(resultado.itens()).singleElement()
                .satisfies(item -> assertThat(item.jaProcessada()).isTrue());
    }

    @Test
    void anuncioBloqueadoImpedeOLoteAntesDeCarregarMidias() {
        AnuncioEntity anuncio = mock(AnuncioEntity.class);
        when(anuncio.getStatus()).thenReturn(StatusAnuncio.BLOQUEADO);
        when(anuncioRepository.findByIdForModeration(anuncioId)).thenReturn(Optional.of(anuncio));

        assertThatThrownBy(() -> service.validar(
                anuncioId,
                new AdminDecidirFotosLoteRequestDto(List.of(
                        item(UUID.randomUUID(), AdminDecisaoFotoLoteAcao.EXCLUIR, null, null))),
                ator))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409")
                .hasMessageContaining("impede moderacao");
    }

    private void preparar(Fixture... fixtures) {
        when(midiaRepository.findByIdInForUpdate(any()))
                .thenReturn(List.of(fixtures).stream().map(Fixture::midia).toList());
        when(arquivoRepository.findByIdInForUpdate(any()))
                .thenReturn(List.of(fixtures).stream().map(Fixture::arquivo).toList());
    }

    private Fixture fixture(
            StatusAnuncioMidia statusMidia,
            StatusArquivoMidia statusArquivo) {
        UUID midiaId = UUID.randomUUID();
        UUID arquivoId = UUID.randomUUID();
        AnuncioMidiaEntity midia = mock(AnuncioMidiaEntity.class);
        ArquivoMidiaEntity arquivo = mock(ArquivoMidiaEntity.class);
        when(midia.getId()).thenReturn(midiaId);
        when(midia.getAnuncioId()).thenReturn(anuncioId);
        when(midia.getArquivoMidiaId()).thenReturn(arquivoId);
        when(midia.getTipo()).thenReturn(TipoAnuncioMidia.FOTO);
        when(midia.getStatus()).thenReturn(statusMidia);
        when(arquivo.getId()).thenReturn(arquivoId);
        when(arquivo.getStatusArquivo()).thenReturn(statusArquivo);
        return new Fixture(midiaId, midia, arquivo);
    }

    private AdminDecisaoFotoLoteItemRequestDto item(
            UUID mediaId,
            AdminDecisaoFotoLoteAcao decisao,
            VisibilidadeMidia classificacao,
            String observacao) {
        return new AdminDecisaoFotoLoteItemRequestDto(
                mediaId,
                decisao,
                classificacao,
                observacao);
    }

    private static AdminUserPrincipal principal(PapelUsuario papel) {
        return new AdminUserPrincipal(
                UUID.randomUUID(),
                "Moderador",
                "moderador@example.invalid",
                "hash",
                List.of(papel),
                List.of(),
                List.of(
                        new SimpleGrantedAuthority("ROLE_" + papel.name()),
                        new SimpleGrantedAuthority("MIDIA_REVISAR")),
                true);
    }

    private record Fixture(
            UUID midiaId,
            AnuncioMidiaEntity midia,
            ArquivoMidiaEntity arquivo) {
    }
}
