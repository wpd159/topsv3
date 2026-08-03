package br.com.topsdojob.v3.application.admin.stories;

import static br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo.STORIES;

import br.com.topsdojob.v3.application.admin.creditos.AdminCreditoOperacaoService;
import br.com.topsdojob.v3.application.admin.stories.dto.AdminStoryConfiguracaoDto;
import br.com.topsdojob.v3.application.admin.stories.dto.AdminStoryConfiguracaoRequest;
import br.com.topsdojob.v3.persistence.entity.midia.StoryConfiguracaoComercialEntity;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumRepository;
import br.com.topsdojob.v3.persistence.repository.StoryConfiguracaoComercialRepository;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminStoryConfiguracaoService {

  public static final int DURACAO_HORAS = 24;
  private static final UUID STORIES_BENEFICIO_ID =
      UUID.fromString("f3000000-0000-4000-8000-000000000007");

  private final StoryConfiguracaoComercialRepository configuracaoRepository;
  private final BeneficioPremiumRepository beneficioRepository;
  private final AdminCreditoOperacaoService auditoriaService;

  public AdminStoryConfiguracaoService(
      StoryConfiguracaoComercialRepository configuracaoRepository,
      BeneficioPremiumRepository beneficioRepository,
      AdminCreditoOperacaoService auditoriaService) {
    this.configuracaoRepository = configuracaoRepository;
    this.beneficioRepository = beneficioRepository;
    this.auditoriaService = auditoriaService;
  }

  @Transactional(readOnly = true)
  public AdminStoryConfiguracaoDto consultar() {
    return configuracaoRepository
        .findById(StoryConfiguracaoComercialEntity.SINGLETON_ID)
        .map(this::toDto)
        .orElseGet(() -> new AdminStoryConfiguracaoDto(
            false, false, null, DURACAO_HORAS, null, null));
  }

  @Transactional
  public AdminStoryConfiguracaoDto salvar(
      AdminStoryConfiguracaoRequest request,
      AdminUserPrincipal administrador,
      String requestId) {
    validarAdministrador(administrador);
    if (request == null || request.ativo() == null || request.custoCreditos() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status e custo obrigatorios");
    }
    if (request.custoCreditos() < 0 || request.custoCreditos() > 1_000_000) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "custo em creditos invalido");
    }

    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    StoryConfiguracaoComercialEntity configuracao = configuracaoRepository.findForUpdate().orElse(null);
    if (configuracao == null && request.versao() != null) {
      throw conflitoConcorrente();
    }
    if (configuracao != null && !Objects.equals(configuracao.getVersao(), request.versao())) {
      throw conflitoConcorrente();
    }

    Map<String, Object> antes = configuracao == null
        ? Map.of("configurada", false)
        : Map.of(
            "configurada", true,
            "ativo", Boolean.TRUE.equals(configuracao.getAtivo()),
            "custoCreditos", configuracao.getCustoCreditos(),
            "versao", configuracao.getVersao());

    garantirIdentidadeTecnica(agora);
    if (configuracao == null) {
      configuracao = StoryConfiguracaoComercialEntity.criar(
          request.ativo(), request.custoCreditos(), administrador.usuarioId(), agora);
    } else {
      configuracao.atualizar(
          request.ativo(), request.custoCreditos(), administrador.usuarioId(), agora);
    }
    try {
      configuracao = configuracaoRepository.saveAndFlush(configuracao);
    } catch (DataIntegrityViolationException exception) {
      throw conflitoConcorrente();
    }

    auditoriaService.auditar(
        administrador.usuarioId(),
        "STORY_CONFIGURACAO_ATUALIZAR",
        "STORY_CONFIGURACAO_COMERCIAL",
        UUID.nameUUIDFromBytes("story-configuracao-comercial".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
        antes,
        Map.of(
            "configurada", true,
            "ativo", request.ativo(),
            "custoCreditos", request.custoCreditos(),
            "duracaoHoras", DURACAO_HORAS,
            "versao", configuracao.getVersao()),
        requestId);
    return toDto(configuracao);
  }

  private void garantirIdentidadeTecnica(OffsetDateTime agora) {
    if (beneficioRepository.findByCodigo(STORIES).isPresent()) {
      return;
    }
    beneficioRepository.inserirCatalogoSeAusente(
        STORIES_BENEFICIO_ID,
        STORIES,
        "Stories",
        "Identidade tecnica para ledger e ativacoes de Stories",
        "ANUNCIO",
        false,
        true,
        0,
        agora);
    if (beneficioRepository.findByCodigo(STORIES).isEmpty()) {
      throw new IllegalStateException("identidade tecnica de Stories indisponivel");
    }
  }

  private AdminStoryConfiguracaoDto toDto(StoryConfiguracaoComercialEntity entity) {
    return new AdminStoryConfiguracaoDto(
        true,
        Boolean.TRUE.equals(entity.getAtivo()),
        entity.getCustoCreditos(),
        DURACAO_HORAS,
        entity.getVersao(),
        entity.getAtualizadoEm());
  }

  private void validarAdministrador(AdminUserPrincipal administrador) {
    if (administrador == null || !administrador.isEnabled()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "sessao administrativa obrigatoria");
    }
  }

  private ResponseStatusException conflitoConcorrente() {
    return new ResponseStatusException(
        HttpStatus.CONFLICT,
        "a configuracao de Stories foi alterada por outra sessao");
  }
}
