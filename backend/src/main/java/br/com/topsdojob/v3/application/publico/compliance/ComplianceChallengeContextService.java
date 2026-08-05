package br.com.topsdojob.v3.application.publico.compliance;

import br.com.topsdojob.v3.application.publico.compliance.dto.VisitorChallengeRequestDto;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.EscopoConteudoVisitante;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StorySelecaoAdministrativaEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.StorySelecaoAdministrativaRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ModoConteudoStory;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusStoryAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ComplianceChallengeContextService {

  private static final String PREFIXO_STORY_ADMIN = "administrativo:";

  private final AnuncioRepository anuncioRepository;
  private final AnuncioMidiaRepository midiaRepository;
  private final ArquivoMidiaRepository arquivoRepository;
  private final StoryAnuncioRepository storyRepository;
  private final StorySelecaoAdministrativaRepository storyAdminRepository;
  private final UsuarioRepository usuarioRepository;

  @Autowired
  public ComplianceChallengeContextService(
      AnuncioRepository anuncioRepository,
      AnuncioMidiaRepository midiaRepository,
      ArquivoMidiaRepository arquivoRepository,
      StoryAnuncioRepository storyRepository,
      StorySelecaoAdministrativaRepository storyAdminRepository,
      UsuarioRepository usuarioRepository) {
    this.anuncioRepository = anuncioRepository;
    this.midiaRepository = midiaRepository;
    this.arquivoRepository = arquivoRepository;
    this.storyRepository = storyRepository;
    this.storyAdminRepository = storyAdminRepository;
    this.usuarioRepository = usuarioRepository;
  }

  ComplianceChallengeContextService(
      AnuncioRepository anuncioRepository,
      AnuncioMidiaRepository midiaRepository,
      StoryAnuncioRepository storyRepository,
      StorySelecaoAdministrativaRepository storyAdminRepository,
      UsuarioRepository usuarioRepository) {
    this(
        anuncioRepository,
        midiaRepository,
        null,
        storyRepository,
        storyAdminRepository,
        usuarioRepository);
  }

  public Contexto validar(
      VisitorChallengeRequestDto request,
      EscopoConteudoVisitante escopo) {
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "contexto protegido obrigatorio");
    }
    UUID anuncioId = request.anuncioId();
    UUID midiaId = request.midiaId();
    if (escopo == EscopoConteudoVisitante.MIDIA_RESTRITA
        || escopo == EscopoConteudoVisitante.CONTEUDO_EXPLICITO) {
      if (midiaId == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "midia protegida obrigatoria");
      }
      AnuncioMidiaEntity midia = midiaRepository.findById(midiaId)
          .orElseThrow(() -> new ResponseStatusException(
              HttpStatus.NOT_FOUND,
              "midia protegida nao encontrada"));
      if (midia.getStatus() != StatusAnuncioMidia.PUBLICAVEL
          || midia.getVisibilidadeMidia() != VisibilidadeMidia.RESTRITA_18) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "midia nao exige verificacao reforcada");
      }
      if (anuncioId != null && !anuncioId.equals(midia.getAnuncioId())) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "midia fora do anuncio informado");
      }
      anuncioId = midia.getAnuncioId();
    }
    String story = null;
    boolean storyIndependente = false;
    if (escopo == EscopoConteudoVisitante.STORY) {
      StoryContext storyContext = validarStory(request.storyId(), anuncioId);
      anuncioId = storyContext.anuncioId();
      midiaId = storyContext.midiaId();
      story = storyContext.referencia();
      storyIndependente = storyContext.independente();
    }
    if (storyIndependente) {
      return new Contexto(null, null, story, sanitizarRota(request.route()));
    }
    if (anuncioId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "anuncio protegido obrigatorio");
    }
    AnuncioEntity anuncio = anuncioRepository.findById(anuncioId)
        .filter(this::publicavelComProprietarioAtivo)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "conteudo protegido nao encontrado"));
    return new Contexto(
        anuncio.getId(),
        midiaId,
        story,
        sanitizarRota(request.route()));
  }

  private StoryContext validarStory(String referencia, UUID anuncioInformado) {
    if (referencia == null || referencia.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "story protegido obrigatorio");
    }
    String storyId = referencia.trim();
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    if (storyId.startsWith(PREFIXO_STORY_ADMIN)) {
      return validarStoryAdministrativo(storyId, anuncioInformado, agora);
    }

    UUID id = uuidStory(storyId);
    StoryAnuncioEntity story = storyRepository
        .findByIdAndStatus(id, StatusStoryAnuncio.PUBLICADO)
        .filter(item -> item.getEncerradoEm() == null)
        .filter(item -> janelaValida(item, agora))
        .orElseThrow(this::storyNaoEncontrado);
    if (story.getModoConteudoEfetivo() == ModoConteudoStory.ANUNCIO) {
      UUID anuncioId = story.getAnuncioId();
      if (anuncioId == null) {
        throw storyNaoEncontrado();
      }
      validarMesmoAnuncio(anuncioInformado, anuncioId);
      return new StoryContext(anuncioId, null, id.toString(), false);
    }
    UsuarioEntity proprietario = usuarioRepository.findById(story.getCriadoPor())
        .filter(this::usuarioAtivo)
        .orElseThrow(this::storyNaoEncontrado);
    if (story.getArquivoMidiaId() != null) {
      if (anuncioInformado != null || arquivoRepository == null) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Story nao pertence a anuncio");
      }
      arquivoRepository.findById(story.getArquivoMidiaId())
          .filter(item -> item.getStatusArquivo() == StatusArquivoMidia.VALIDADO)
          .orElseThrow(this::storyNaoEncontrado);
      return new StoryContext(null, null, id.toString(), true);
    }
    AnuncioMidiaEntity midia = midiaRepository.findById(story.getAnuncioMidiaId())
        .filter(this::storyUsuarioElegivel)
        .orElseThrow(this::storyNaoEncontrado);
    AnuncioEntity anuncio = anuncioRepository.findById(midia.getAnuncioId())
        .filter(item -> item.getUsuarioId().equals(proprietario.getId()))
        .orElseThrow(this::storyNaoEncontrado);
    validarMesmoAnuncio(anuncioInformado, anuncio.getId());
    return new StoryContext(anuncio.getId(), midia.getId(), id.toString(), false);
  }

  private StoryContext validarStoryAdministrativo(
      String referencia,
      UUID anuncioInformado,
      OffsetDateTime agora) {
    UUID midiaId = uuidStory(referencia.substring(PREFIXO_STORY_ADMIN.length()));
    AnuncioMidiaEntity midia = midiaRepository.findById(midiaId)
        .filter(this::storyAdminElegivel)
        .orElseThrow(this::storyNaoEncontrado);
    StorySelecaoAdministrativaEntity selecao = storyAdminRepository
        .findByAtivaTrueOrderByAtivadoEmAscIdAsc().stream()
        .filter(item -> midia.getAnuncioId().equals(item.getAnuncioId()))
        .filter(item -> storyAdminAtivo(item, agora))
        .findFirst()
        .orElseThrow(this::storyNaoEncontrado);
    validarMesmoAnuncio(anuncioInformado, selecao.getAnuncioId());
    return new StoryContext(
        selecao.getAnuncioId(),
        midia.getId(),
        PREFIXO_STORY_ADMIN + midia.getId(),
        false);
  }

  private void validarMesmoAnuncio(UUID anuncioInformado, UUID anuncioReal) {
    if (anuncioInformado != null && !anuncioInformado.equals(anuncioReal)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "story fora do anuncio informado");
    }
  }

  private boolean storyUsuarioElegivel(AnuncioMidiaEntity midia) {
    return midia != null
        && midia.getStatus() == StatusAnuncioMidia.PUBLICAVEL
        && (midia.getTipo() == TipoAnuncioMidia.STORY
            || midia.getFinalidade() == FinalidadeAnuncioMidia.STORY)
        && midia.getVisibilidadeMidia() == VisibilidadeMidia.RESTRITA_18;
  }

  private boolean storyAdminElegivel(AnuncioMidiaEntity midia) {
    return midia != null
        && midia.getStatus() == StatusAnuncioMidia.PUBLICAVEL
        && (midia.getTipo() == TipoAnuncioMidia.FOTO
            || midia.getTipo() == TipoAnuncioMidia.VIDEO);
  }

  private boolean janelaValida(StoryAnuncioEntity story, OffsetDateTime agora) {
    return (story.getInicioEm() == null || !story.getInicioEm().isAfter(agora))
        && (story.getFimEm() == null || story.getFimEm().isAfter(agora));
  }

  private boolean storyAdminAtivo(
      StorySelecaoAdministrativaEntity selecao,
      OffsetDateTime agora) {
    return selecao.isAtiva()
        && selecao.getAnuncioId() != null
        && selecao.getExpiraEm() != null
        && selecao.getExpiraEm().isAfter(agora);
  }

  private UUID uuidStory(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "referencia de story invalida");
    }
  }

  private ResponseStatusException storyNaoEncontrado() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "story protegido nao encontrado");
  }

  private boolean publicavel(AnuncioEntity anuncio) {
    return anuncio.getRemovidoEm() == null
        && anuncio.getStatus() == StatusAnuncio.PUBLICADO
        && anuncio.getStatusModeracao() == StatusModeracaoAnuncio.APROVADO;
  }

  private boolean publicavelComProprietarioAtivo(AnuncioEntity anuncio) {
    return publicavel(anuncio)
        && usuarioRepository.findById(anuncio.getUsuarioId())
            .filter(this::usuarioAtivo)
            .isPresent();
  }

  private boolean usuarioAtivo(UsuarioEntity usuario) {
    return usuario.getStatus() == StatusUsuario.ATIVO
        && usuario.getDesativadoEm() == null
        && usuario.getExcluidoEm() == null;
  }

  private String sanitizarRota(String value) {
    if (value == null || value.isBlank()) {
      return "/";
    }
    String route = value.trim();
    if (!route.startsWith("/") || route.contains("://") || route.length() > 255) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rota protegida invalida");
    }
    return route.replaceAll("[\\r\\n\\t]", "");
  }

  public record Contexto(
      UUID anuncioId,
      UUID midiaId,
      String storyReferencia,
      String rotaSanitizada) {
  }

  private record StoryContext(
      UUID anuncioId,
      UUID midiaId,
      String referencia,
      boolean independente) {
  }
}
