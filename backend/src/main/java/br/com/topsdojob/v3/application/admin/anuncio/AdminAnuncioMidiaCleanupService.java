package br.com.topsdojob.v3.application.admin.anuncio;

import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StorySelecaoAdministrativaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.StorySelecaoAdministrativaRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AdminAnuncioMidiaCleanupService {

  private static final Logger LOGGER = LoggerFactory.getLogger(AdminAnuncioMidiaCleanupService.class);

  private final AnuncioMidiaRepository anuncioMidiaRepository;
  private final StoryAnuncioRepository storyRepository;
  private final StorySelecaoAdministrativaRepository storyAdminRepository;
  private final AdminAnuncioMidiaPosCommitCleanupService posCommitCleanupService;

  public AdminAnuncioMidiaCleanupService(
      AnuncioMidiaRepository anuncioMidiaRepository,
      StoryAnuncioRepository storyRepository,
      StorySelecaoAdministrativaRepository storyAdminRepository,
      AdminAnuncioMidiaPosCommitCleanupService posCommitCleanupService) {
    this.anuncioMidiaRepository = anuncioMidiaRepository;
    this.storyRepository = storyRepository;
    this.storyAdminRepository = storyAdminRepository;
    this.posCommitCleanupService = posCommitCleanupService;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public Resultado limpar(UUID anuncioId, OffsetDateTime agora) {
    List<AnuncioMidiaEntity> vinculos = anuncioMidiaRepository.findByAnuncioIdForUpdate(anuncioId);
    return limparVinculos(anuncioId, vinculos, agora, true);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public Resultado limparMidia(UUID anuncioId, UUID midiaId, OffsetDateTime agora) {
    List<AnuncioMidiaEntity> vinculosDoAnuncio =
        anuncioMidiaRepository.findByAnuncioIdForUpdate(anuncioId);
    AnuncioMidiaEntity alvo = vinculosDoAnuncio.stream()
        .filter(item -> midiaId.equals(item.getId()))
        .findFirst()
        .orElseThrow(() -> conflito("MIDIA_NAO_PERTENCE_AO_ANUNCIO"));
    if (alvo.getTipo() != TipoAnuncioMidia.FOTO) {
      throw conflito("SOMENTE_FOTO_PODE_SER_EXCLUIDA_PELO_LOTE");
    }
    if (alvo.getArquivoMidiaId() == null) {
      throw conflito("ARQUIVO_DE_MIDIA_AUSENTE");
    }
    if (alvo.getStatus() == StatusAnuncioMidia.REMOVIDA) {
      return Resultado.resultadoJaProcessado();
    }

    List<UUID> vinculoIds = List.of(alvo.getId());
    var preparacao = posCommitCleanupService.preparar(
        List.of(alvo.getArquivoMidiaId()), vinculoIds);
    if (preparacao.cleanupNecessario()
        && !TransactionSynchronizationManager.isSynchronizationActive()) {
      throw new CleanupException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "TRANSACAO_CLEANUP_INDISPONIVEL",
          null);
    }

    alvo.removerLogicamente(agora);
    anuncioMidiaRepository.saveAndFlush(alvo);
    normalizarOrdem(vinculosDoAnuncio, agora);

    StoryResult storyResult = encerrarStories(anuncioId, vinculoIds, agora, false);
    if (preparacao.cleanupNecessario()) {
      agendarCleanupPosCommit(preparacao.arquivoIds(), vinculoIds);
    }
    return new Resultado(
        1,
        0,
        0,
        preparacao.compartilhadoOuProtegido() ? preparacao.arquivoIds().size() : 0,
        storyResult.encerrados(),
        false,
        preparacao.cleanupNecessario() ? preparacao.objetosCandidatos() : 0,
        false);
  }

  private void normalizarOrdem(
      List<AnuncioMidiaEntity> vinculosDoAnuncio,
      OffsetDateTime agora) {
    List<AnuncioMidiaEntity> ativos = vinculosDoAnuncio.stream()
        .filter(item -> item.getStatus() != StatusAnuncioMidia.REMOVIDA)
        .filter(item -> item.getTipo() != TipoAnuncioMidia.STORY)
        .sorted(Comparator
            .comparing(AnuncioMidiaEntity::getOrdem, Comparator.nullsLast(Integer::compareTo))
            .thenComparing(AnuncioMidiaEntity::getCriadoEm, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(AnuncioMidiaEntity::getId))
        .toList();
    boolean precisaNormalizar = java.util.stream.IntStream.range(0, ativos.size())
        .anyMatch(index -> !Objects.equals(ativos.get(index).getOrdem(), index));
    if (!precisaNormalizar) {
      return;
    }

    int deslocamento = ativos.stream()
        .map(AnuncioMidiaEntity::getOrdem)
        .filter(Objects::nonNull)
        .max(Integer::compareTo)
        .orElse(0) + ativos.size() + 100;
    for (int index = 0; index < ativos.size(); index++) {
      ativos.get(index).reordenar(deslocamento + index, agora);
    }
    anuncioMidiaRepository.saveAllAndFlush(ativos);
    for (int index = 0; index < ativos.size(); index++) {
      ativos.get(index).reordenar(index, agora);
    }
    anuncioMidiaRepository.saveAll(ativos);
  }

  private void agendarCleanupPosCommit(
      Set<UUID> arquivoIds,
      List<UUID> vinculoIds) {
    Set<UUID> arquivosSnapshot = Set.copyOf(arquivoIds);
    List<UUID> vinculosSnapshot = List.copyOf(vinculoIds);
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        try {
          posCommitCleanupService.limparSeContinuarOrfao(
              arquivosSnapshot, vinculosSnapshot);
        } catch (AdminAnuncioMidiaPosCommitCleanupService.CleanupPosCommitException exception) {
          LOGGER.warn(
              "Cleanup pos-commit da midia ficou pendente: codigo={}, arquivos={}",
              exception.codigo(),
              arquivosSnapshot.size());
        } catch (RuntimeException exception) {
          LOGGER.warn(
              "Cleanup pos-commit da midia ficou pendente: codigo=FALHA_TECNICA, arquivos={}",
              arquivosSnapshot.size());
        }
      }
    });
  }

  private Resultado limparVinculos(
      UUID anuncioId,
      List<AnuncioMidiaEntity> vinculos,
      OffsetDateTime agora,
      boolean encerrarStoryAdministrativo) {
    List<AnuncioMidiaEntity> vinculosAtivos = vinculos.stream()
        .filter(item -> item.getStatus() != StatusAnuncioMidia.REMOVIDA)
        .toList();
    List<UUID> vinculoIds = vinculosAtivos.stream()
        .map(AnuncioMidiaEntity::getId)
        .filter(Objects::nonNull)
        .toList();
    Set<UUID> arquivoIds = vinculosAtivos.stream()
        .map(AnuncioMidiaEntity::getArquivoMidiaId)
        .filter(Objects::nonNull)
        .sorted()
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

    Set<UUID> arquivosPlanejados = new LinkedHashSet<>();
    List<AdminAnuncioMidiaPosCommitCleanupService.Preparacao> cleanups = new ArrayList<>();
    int arquivosPreservados = (int) vinculosAtivos.stream()
        .filter(item -> item.getArquivoMidiaId() == null)
        .count();
    int objetosCleanupAgendados = 0;
    for (UUID arquivoId : arquivoIds) {
      if (arquivosPlanejados.contains(arquivoId)) {
        continue;
      }
      var preparacao = posCommitCleanupService.preparar(List.of(arquivoId), vinculoIds);
      Set<UUID> grupo = preparacao.arquivoIds().isEmpty()
          ? Set.of(arquivoId)
          : preparacao.arquivoIds();
      arquivosPlanejados.addAll(grupo);
      int arquivosDoAnuncioNoGrupo = (int) arquivoIds.stream().filter(grupo::contains).count();
      if (!preparacao.cleanupNecessario()) {
        arquivosPreservados += Math.max(1, arquivosDoAnuncioNoGrupo);
        continue;
      }
      if (!TransactionSynchronizationManager.isSynchronizationActive()) {
        throw new CleanupException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "TRANSACAO_CLEANUP_INDISPONIVEL",
            null);
      }
      cleanups.add(preparacao);
      objetosCleanupAgendados += preparacao.objetosCandidatos();
    }

    vinculosAtivos.forEach(item -> item.removerLogicamente(agora));
    if (!vinculosAtivos.isEmpty()) {
      anuncioMidiaRepository.saveAllAndFlush(vinculosAtivos);
    }

    StoryResult storyResult = encerrarStories(
        anuncioId,
        vinculos.stream().map(AnuncioMidiaEntity::getId).toList(),
        agora,
        encerrarStoryAdministrativo);
    cleanups.forEach(preparacao ->
        agendarCleanupPosCommit(preparacao.arquivoIds(), vinculoIds));
    return new Resultado(
        vinculosAtivos.size(),
        0,
        0,
        arquivosPreservados,
        storyResult.encerrados(),
        storyResult.administrativoEncerrado(),
        objetosCleanupAgendados,
        false);
  }

  private StoryResult encerrarStories(
      UUID anuncioId,
      List<UUID> anuncioMidiaIds,
      OffsetDateTime agora,
      boolean encerrarStoryAdministrativo) {
    List<StoryAnuncioEntity> stories = encerrarStoryAdministrativo
        ? storyRepository.findByAnuncioIdForUpdate(anuncioId)
        : anuncioMidiaIds.isEmpty()
            ? List.of()
            : storyRepository.findByAnuncioMidiaIdInForUpdate(anuncioMidiaIds);
    List<StoryAnuncioEntity> alterados = new ArrayList<>();
    for (StoryAnuncioEntity story : stories) {
      if (story.suspenderPorBloqueio(agora)) {
        alterados.add(story);
      }
    }
    if (!alterados.isEmpty()) {
      storyRepository.saveAll(alterados);
    }

    boolean administrativoEncerrado = false;
    if (encerrarStoryAdministrativo) {
      storyAdminRepository.bloquearOperacao();
      List<StorySelecaoAdministrativaEntity> selecoes =
          storyAdminRepository.bloquearAtivasDoAnuncio(anuncioId);
      administrativoEncerrado = !selecoes.isEmpty();
      if (administrativoEncerrado) {
        selecoes.forEach(selecao -> selecao.desativar(agora));
        storyAdminRepository.saveAll(selecoes);
      }
    }
    return new StoryResult(alterados.size(), administrativoEncerrado);
  }

  private CleanupException conflito(String codigo) {
    return new CleanupException(HttpStatus.CONFLICT, codigo, null);
  }

  public record Resultado(
      int midiasRemovidas,
      int objetosExcluidos,
      int objetosJaAusentes,
      int objetosCompartilhadosPreservados,
      int storiesEncerrados,
      boolean storyAdministrativoEncerrado,
      int objetosCleanupAgendados,
      boolean jaProcessado) {

    public Resultado(
        int midiasRemovidas,
        int objetosExcluidos,
        int objetosJaAusentes,
        int objetosCompartilhadosPreservados,
        int storiesEncerrados,
        boolean storyAdministrativoEncerrado) {
      this(
          midiasRemovidas,
          objetosExcluidos,
          objetosJaAusentes,
          objetosCompartilhadosPreservados,
          storiesEncerrados,
          storyAdministrativoEncerrado,
          0,
          false);
    }

    private static Resultado resultadoJaProcessado() {
      return new Resultado(0, 0, 0, 0, 0, false, 0, true);
    }
  }

  public static final class CleanupException extends RuntimeException {
    private final HttpStatus status;
    private final String codigo;

    CleanupException(HttpStatus status, String codigo, Throwable cause) {
      super(codigo, cause);
      this.status = status;
      this.codigo = codigo;
    }

    public HttpStatus status() {
      return status;
    }

    public String codigo() {
      return codigo;
    }
  }

  private record StoryResult(int encerrados, boolean administrativoEncerrado) {
  }
}
