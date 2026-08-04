package br.com.topsdojob.v3.application.admin.premium;

import br.com.topsdojob.v3.application.admin.creditos.AdminCreditoOperacaoService;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumCatalogoUpdateRequest;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumOpcaoUpdateRequest;
import br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo;
import br.com.topsdojob.v3.application.premium.PremiumCatalogoService;
import br.com.topsdojob.v3.application.premium.dto.PremiumCatalogoDto;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumOpcaoEntity;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumOpcaoRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumRepository;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminPremiumCatalogoService {

    private static final Set<Integer> DURACOES_PERMITIDAS = Set.of(1, 7, 14, 30);

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
        if (PremiumBeneficioCodigo.STORIES.equals(beneficio.getCodigo())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Stories utiliza configuracao comercial propria");
        }
        if (request.atualizadoEm() == null) {
            throw badRequest("ultima atualizacao obrigatoria");
        }
        OffsetDateTime marcadorAtual = normalizarMarcador(beneficio.getAtualizadoEm());
        OffsetDateTime marcadorRecebido = normalizarMarcador(request.atualizadoEm());
        if (marcadorAtual == null || !marcadorAtual.toInstant().equals(marcadorRecebido.toInstant())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "catalogo alterado por outra sessao administrativa");
        }
        String nome = texto(request.nome(), 2, 120, "nome invalido");
        String descricao = texto(request.descricao(), 5, 500, "descricao invalida");
        int ordem = inteiroNaoNegativo(request.ordemExibicao(), "ordem invalida");
        if (request.ativo() == null || request.opcoes() == null || request.opcoes().isEmpty()) {
            throw badRequest("status e duracoes obrigatorios");
        }
        Map<Integer, AdminPremiumOpcaoUpdateRequest> recebidas = new HashMap<>();
        for (AdminPremiumOpcaoUpdateRequest opcao : request.opcoes()) {
            if (opcao == null || opcao.duracaoDias() == null || !DURACOES_PERMITIDAS.contains(opcao.duracaoDias())) {
                throw badRequest("duracao permitida: 1, 7, 14 ou 30 dias");
            }
            if (recebidas.put(opcao.duracaoDias(), opcao) != null) {
                throw badRequest("duracao duplicada");
            }
            inteiroNaoNegativo(opcao.custoCreditos(), "custo em creditos invalido");
            inteiroNaoNegativo(opcao.ordemExibicao(), "ordem da duracao invalida");
            if (opcao.ativo() == null) {
                throw badRequest("status da duracao obrigatorio");
            }
        }
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        if (!agora.isAfter(marcadorAtual)) {
            agora = marcadorAtual.plusNanos(1_000);
        }
        Map<String, Object> antes = Map.of(
                "nome", beneficio.getNome(),
                "ativo", Boolean.TRUE.equals(beneficio.getAtivo()),
                "ordem", beneficio.getOrdemExibicao() == null ? 0 : beneficio.getOrdemExibicao());
        beneficio.atualizarCatalogo(nome, descricao, request.ativo(), ordem, agora);
        beneficioRepository.save(beneficio);
        for (int duracao : DURACOES_PERMITIDAS) {
            AdminPremiumOpcaoUpdateRequest recebida = recebidas.get(duracao);
            var atual = opcaoRepository
                    .findFirstByBeneficioIdAndDuracaoDiasOrderByVersaoRegraDesc(beneficioId, duracao)
                    .orElse(null);
            if (recebida == null) {
                if (atual != null) {
                    atual.atualizar(atual.getCustoCreditos(), false, atual.getOrdemExibicao(), agora);
                    opcaoRepository.save(atual);
                }
                continue;
            }
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
        }
        auditoriaService.auditar(
                administrador.usuarioId(),
                "PREMIUM_CATALOGO_ATUALIZAR",
                "BENEFICIO_PREMIUM",
                beneficioId,
                antes,
                Map.of("nome", nome, "ativo", request.ativo(), "ordem", ordem, "duracoes", recebidas.keySet()),
                requestId);
        return catalogoService.catalogoAdministrativo().stream()
                .filter(item -> item.id().equals(beneficioId))
                .findFirst()
                .orElseThrow();
    }

    private void validarAdministrador(AdminUserPrincipal administrador) {
        if (administrador == null || !administrador.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "sessao administrativa obrigatoria");
        }
    }

    private OffsetDateTime normalizarMarcador(OffsetDateTime value) {
        return value == null
                ? null
                : value.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
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

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
