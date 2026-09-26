package br.com.topsdojob.v3.application.admin.moderacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.anuncio.AdminAnuncioMidiaCleanupService;
import br.com.topsdojob.v3.application.admin.moderacao.AdminModeracaoFotosLotePrevalidacaoService.ItemValidado;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecisaoFotoLoteAcao;
import br.com.topsdojob.v3.application.arquivo.ArquivoPublicidadeRegistroService;
import br.com.topsdojob.v3.application.publico.anunciante.MeuAnuncioCicloVidaService;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class AdminModeracaoFotosLoteItemServiceTest {

  private final AdminModeracaoAcaoService moderacaoAcaoService =
      mock(AdminModeracaoAcaoService.class);
  private final AdminAnuncioMidiaCleanupService cleanupService =
      mock(AdminAnuncioMidiaCleanupService.class);
  private final AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
  private final AuditoriaEventoRepository auditoriaRepository =
      mock(AuditoriaEventoRepository.class);
  private final ArquivoPublicidadeRegistroService arquivoPublicidade =
      mock(ArquivoPublicidadeRegistroService.class);
  private final MeuAnuncioCicloVidaService cicloVidaService =
      mock(MeuAnuncioCicloVidaService.class);
  private final AdminModeracaoFotosLoteItemService service =
      new AdminModeracaoFotosLoteItemService(
          moderacaoAcaoService,
          cleanupService,
          anuncioRepository,
          auditoriaRepository,
          new ObjectMapper(),
          arquivoPublicidade,
          cicloVidaService);

  @Test
  void estadoStaleRetornaIdempotenteSemDuplicarAuditoriaDeSucesso() {
    UUID anuncioId = UUID.randomUUID();
    UUID midiaId = UUID.randomUUID();
    AnuncioEntity anuncio = mock(AnuncioEntity.class);
    when(anuncio.getStatus()).thenReturn(StatusAnuncio.PENDENTE_REVISAO);
    when(anuncioRepository.findByIdForModeration(anuncioId)).thenReturn(Optional.of(anuncio));
    when(cleanupService.limparMidia(any(), any(), any()))
        .thenReturn(new AdminAnuncioMidiaCleanupService.Resultado(
            1, 0, 0, 1, 0, false, 0, false))
        .thenReturn(new AdminAnuncioMidiaCleanupService.Resultado(
            0, 0, 0, 0, 0, false, 0, true));
    ItemValidado item = new ItemValidado(
        midiaId, AdminDecisaoFotoLoteAcao.EXCLUIR, null, null, false);
    AdminUserPrincipal ator = principal();

    var primeira = service.executar(anuncioId, item, ator, "request-stale");
    var retry = service.executar(anuncioId, item, ator, "request-stale");

    assertThat(primeira.resultado()).isEqualTo("EXCLUIDA");
    assertThat(retry.resultado()).isEqualTo("JA_PROCESSADA");
    verify(auditoriaRepository, times(1)).save(any());
    verify(arquivoPublicidade, times(1)).registrarEstado(
        org.mockito.ArgumentMatchers.eq(anuncioId),
        org.mockito.ArgumentMatchers.eq("MODERACAO_FOTO_EXCLUIDA"),
        org.mockito.ArgumentMatchers.eq("request-stale"), any());
  }

  @Test
  void exclusaoPublicaEncerraSePromovidaSuprimidaEraUltimaFotoExibivel() {
    UUID anuncioId = UUID.randomUUID();
    UUID midiaId = UUID.randomUUID();
    UUID promovidaId = UUID.randomUUID();
    AnuncioEntity anuncio = mock(AnuncioEntity.class);
    when(anuncio.getStatus()).thenReturn(StatusAnuncio.PUBLICADO);
    when(anuncioRepository.findByIdForModeration(anuncioId)).thenReturn(Optional.of(anuncio));
    when(arquivoPublicidade.midiasExibidasAntesDaRetirada(anuncioId)).thenReturn(Set.of(midiaId));
    when(arquivoPublicidade.prepararRetiradaSemNovaCopia(eq(anuncioId), any(),
        eq("req-promovida"), any(), eq(Set.of(midiaId)))).thenReturn(List.of(promovidaId));
    when(arquivoPublicidade.possuiFotoPublicaSelecionada(anuncioId)).thenReturn(false);
    when(cleanupService.limparMidia(any(), any(), any()))
        .thenReturn(new AdminAnuncioMidiaCleanupService.Resultado(
            1, 0, 0, 1, 0, false, 0, false));
    AdminUserPrincipal ator = principal();

    var resultado = service.executar(anuncioId, new ItemValidado(
        midiaId, AdminDecisaoFotoLoteAcao.EXCLUIR, null, null, false), ator, "req-promovida");

    assertThat(resultado.resultado()).isEqualTo("EXCLUIDA");
    verify(cicloVidaService).encerrarPorFaltaDeMidiaArquivavel(eq(anuncio),
        eq(ator.usuarioId()), eq(midiaId), eq(List.of(promovidaId)),
        eq("req-promovida"), any());
    verify(arquivoPublicidade, never()).registrarEstado(eq(anuncioId),
        eq("MODERACAO_FOTO_EXCLUIDA"), any(), any());
  }

  private AdminUserPrincipal principal() {
    return new AdminUserPrincipal(
        UUID.randomUUID(),
        "Admin",
        "admin@example.invalid",
        "hash",
        List.of(PapelUsuario.ADMIN),
        List.of(),
        List.of(
            new SimpleGrantedAuthority("ROLE_ADMIN"),
            new SimpleGrantedAuthority("MIDIA_REVISAR")),
        true);
  }
}
