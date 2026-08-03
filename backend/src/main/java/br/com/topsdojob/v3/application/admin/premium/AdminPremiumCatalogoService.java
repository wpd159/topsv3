package br.com.topsdojob.v3.application.admin.premium;

import static br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo.STORIES;

import br.com.topsdojob.v3.application.admin.creditos.AdminCreditoOperacaoService;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumCatalogoCreateRequest;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumCatalogoUpdateRequest;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumOpcaoUpdateRequest;
import br.com.topsdojob.v3.application.premium.PremiumCatalogoService;
import br.com.topsdojob.v3.application.premium.dto.PremiumCatalogoDto;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumOpcaoEntity;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumOpcaoRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.EscopoBeneficioPremium;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminPremiumCatalogoService {

    private final BeneficioPremiumRepository beneficioRepository;
    private final BeneficioPremiumOpcaoRepository opcaoRepository;
    private final PremiumCatalogoService catalogoService;
    private final AdminCreditoOperacaoService auditoriaService;

    public AdminPremiumCatalogoService(
            BeneficioPremiumRepository beneficioRepository,
            BeneficioPremiumOpcaoRepository opcaoRepository,
            PremiumCatalogoService catalogoService,
            AdminCreditoOperacaoService auditoriaService) {
        this.beneficioRepository = beneficioRepository;
        this.opcaoRepository = opcaoRepository;
        this.catalogoService = catalogoService;
        this.auditoriaService = auditoriaService;
    }

    @Transactional
    public PremiumCatalogoDto criarBeneficio(
            AdminPremiumCatalogoCreateRequest request,
            AdminUserPrincipal administrador,
            String requestId) {
        validarAdministrador(administrador);
        if (request == null) {
            throw badRequest("catalogo obrigatorio");
        }
        String codigo = codigo(request.codigo());
        if (!STORIES.equals(codigo)) {
            throw badRequest("somente o beneficio STORIES pode ser criado por este fluxo");
        }
        if (beneficioRepository.findByCodigo(codigo).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "beneficio ja cadastrado");
        }
        String nome = texto(request.nome(), 2, 120, "nome invalido");
        String descricao = texto(request.descricao(), 5, 500, "descricao invalida");
        int ordem = inteiroNaoNegativo(request.ordemExibicao(), "ordem invalida");
        if (request.ativo() == null) {
            throw badRequest("status obrigatorio");
        }
        Map<Integer, AdminPremiumOpcaoUpdateRequest> recebidas = validarOpcoes(request.opcoes());
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        BeneficioPremiumEntity beneficio = BeneficioPremiumEntity.criarCatalogo(
                UUID.randomUUID(),
                codigo,
                nome,
                descricao,
                EscopoBeneficioPremium.ANUNCIO,
                false,
                request.ativo(),
                ordem,
                agora);
        try {
            beneficioRepository.saveAndFlush(beneficio);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "beneficio ja cadastrado");
        }
        persistirOpcoes(beneficio.getId(), recebidas, agora);
        auditoriaService.auditar(
                administrador.usuarioId(),
                "PREMIUM_CATALOGO_CRIAR",
                "BENEFICIO_PREMIUM",
                beneficio.getId(),
                Map.of(),
                Map.of(
                        "codigo", codigo,
                        "nome", nome,
                        "ativo", request.ativo(),
                        "ordem", ordem,
                        "duracoes", recebidas.keySet()),
                requestId);
        return catalogo(beneficio.getId());
    }

    @Transactional
    public PremiumCatalogoDto atualizarBeneficio(
            UUID beneficioId,
            AdminPremiumCatalogoUpdateRequest request,
            AdminUserPrincipal administrador,
            String requestId) {
        validarAdministrador(administrador);
        if (request == null) {
            throw badRequest("catalogo obrigatorio");
        }
        var beneficio = beneficioRepository.findByIdForUpdate(beneficioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "beneficio nao encontrado"));
        String nome = texto(request.nome(), 2, 120, "nome invalido");
        String descricao = texto(request.descricao(), 5, 500, "descricao invalida");
        int ordem = inteiroNaoNegativo(request.ordemExibicao(), "ordem invalida");
        if (request.ativo() == null) {
            throw badRequest("status obrigatorio");
        }
        Map<Integer, AdminPremiumOpcaoUpdateRequest> recebidas = validarOpcoes(request.opcoes());
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        Map<String, Object> antes = Map.of(
                "nome", beneficio.getNome(),
                "ativo", Boolean.TRUE.equals(beneficio.getAtivo()),
                "ordem", beneficio.getOrdemExibicao() == null ? 0 : beneficio.getOrdemExibicao());
        beneficio.atualizarCatalogo(nome, descricao, request.ativo(), ordem, agora);
        beneficioRepository.save(beneficio);
        persistirOpcoes(beneficioId, recebidas, agora);
        auditoriaService.auditar(
                administrador.usuarioId(),
                "PREMIUM_CATALOGO_ATUALIZAR",
                "BENEFICIO_PREMIUM",
                beneficioId,
                antes,
                Map.of("nome", nome, "ativo", request.ativo(), "ordem", ordem, "duracoes", recebidas.keySet()),
                requestId);
        return catalogo(beneficioId);
    }

    private Map<Integer, AdminPremiumOpcaoUpdateRequest> validarOpcoes(
            List<AdminPremiumOpcaoUpdateRequest> opcoes) {
        if (opcoes == null || opcoes.isEmpty()) {
            throw badRequest("ao menos uma opcao de duracao e custo e obrigatoria");
        }
        Map<Integer, AdminPremiumOpcaoUpdateRequest> recebidas = new HashMap<>();
        for (AdminPremiumOpcaoUpdateRequest opcao : opcoes) {
            if (opcao == null) {
                throw badRequest("opcao invalida");
            }
            int duracao = inteiroPositivo(opcao.duracaoDias(), "duracao em dias invalida");
            if (recebidas.put(duracao, opcao) != null) {
                throw badRequest("duracao duplicada");
            }
            inteiroNaoNegativo(opcao.custoCreditos(), "custo em creditos invalido");
            inteiroNaoNegativo(opcao.ordemExibicao(), "ordem da duracao invalida");
            if (opcao.ativo() == null) {
                throw badRequest("status da duracao obrigatorio");
            }
        }
        return recebidas;
    }

    private void persistirOpcoes(
            UUID beneficioId,
            Map<Integer, AdminPremiumOpcaoUpdateRequest> recebidas,
            OffsetDateTime agora) {
        Map<Integer, BeneficioPremiumOpcaoEntity> atuais = new HashMap<>();
        for (BeneficioPremiumOpcaoEntity opcao :
                opcaoRepository.findByBeneficioIdOrderByOrdemExibicaoAscDuracaoDiasAsc(beneficioId)) {
            atuais.merge(
                    opcao.getDuracaoDias(),
                    opcao,
                    (anterior, candidata) -> inteiro(anterior.getVersaoRegra())
                            >= inteiro(candidata.getVersaoRegra()) ? anterior : candidata);
        }
        atuais.forEach((duracao, atual) -> {
            if (!recebidas.containsKey(duracao) && Boolean.TRUE.equals(atual.getAtivo())) {
                atual.atualizar(
                        inteiro(atual.getCustoCreditos()),
                        false,
                        inteiro(atual.getOrdemExibicao()),
                        agora);
                opcaoRepository.save(atual);
            }
        });
        recebidas.forEach((duracao, recebida) -> {
            BeneficioPremiumOpcaoEntity atual = atuais.get(duracao);
            if (atual == null) {
                atual = BeneficioPremiumOpcaoEntity.criar(
                        UUID.randomUUID(),
                        beneficioId,
                        duracao,
                        recebida.custoCreditos(),
                        recebida.ativo(),
                        recebida.ordemExibicao(),
                        agora);
            } else {
                atual.atualizar(
                        recebida.custoCreditos(),
                        recebida.ativo(),
                        recebida.ordemExibicao(),
                        agora);
            }
            opcaoRepository.save(atual);
        });
    }

    private PremiumCatalogoDto catalogo(UUID beneficioId) {
        return catalogoService.catalogoAdministrativo().stream()
                .filter(item -> item.id().equals(beneficioId))
                .findFirst()
                .orElseThrow();
    }

    private void validarAdministrador(AdminUserPrincipal administrador) {
        if (administrador == null || !administrador.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "sessao administrativa obrigatoria");
        }
        boolean admin = administrador.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        boolean gerenciaPremium = administrador.getAuthorities().stream()
                .anyMatch(authority -> "PREMIUM_GERENCIAR".equals(authority.getAuthority()));
        if (!admin || !gerenciaPremium) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "permissao Premium obrigatoria");
        }
    }

    private String codigo(String value) {
        String codigo = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (codigo.length() < 3 || codigo.length() > 50 || !codigo.matches("[A-Z0-9_]+")) {
            throw badRequest("codigo invalido");
        }
        return codigo;
    }

    private String texto(String value, int min, int max, String error) {
        String texto = value == null ? "" : value.trim();
        if (texto.length() < min || texto.length() > max) {
            throw badRequest(error);
        }
        return texto;
    }

    private int inteiroNaoNegativo(Integer value, String error) {
        if (value == null || value < 0 || value > 1_000_000) {
            throw badRequest(error);
        }
        return value;
    }

    private int inteiroPositivo(Integer value, String error) {
        if (value == null || value <= 0 || value > 1_000_000) {
            throw badRequest(error);
        }
        return value;
    }

    private int inteiro(Integer value) {
        return value == null ? 0 : value;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
