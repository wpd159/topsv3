package br.com.topsdojob.v3.application.admin.premium;

import static br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo.FOTOS_EXTRA_5;

import br.com.topsdojob.v3.application.publico.anunciante.midia.LimiteMidiasAnuncioService;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumOpcaoEntity;
import br.com.topsdojob.v3.persistence.entity.premium.GrupoAtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumOpcaoRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumRepository;
import br.com.topsdojob.v3.persistence.repository.GrupoAtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAtivacaoBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BeneficioFotosExtrasModeracaoService {

    private final AnuncioMidiaRepository anuncioMidiaRepository;
    private final BeneficioPremiumRepository beneficioRepository;
    private final BeneficioPremiumOpcaoRepository opcaoRepository;
    private final AtivacaoBeneficioRepository ativacaoRepository;
    private final GrupoAtivacaoBeneficioRepository grupoRepository;
    private final AuditoriaEventoRepository auditoriaRepository;
    private final ObjectMapper objectMapper;

    public BeneficioFotosExtrasModeracaoService(
            AnuncioMidiaRepository anuncioMidiaRepository,
            BeneficioPremiumRepository beneficioRepository,
            BeneficioPremiumOpcaoRepository opcaoRepository,
            AtivacaoBeneficioRepository ativacaoRepository,
            GrupoAtivacaoBeneficioRepository grupoRepository,
            AuditoriaEventoRepository auditoriaRepository,
            ObjectMapper objectMapper) {
        this.anuncioMidiaRepository = anuncioMidiaRepository;
        this.beneficioRepository = beneficioRepository;
        this.opcaoRepository = opcaoRepository;
        this.ativacaoRepository = ativacaoRepository;
        this.grupoRepository = grupoRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean iniciarSeCapacidadeAdicionalAprovada(
            UUID anuncioId,
            UUID midiaId,
            UUID atorId,
            String requestId,
            OffsetDateTime aprovadoEm) {
        long fotosPublicaveis = anuncioMidiaRepository.countByAnuncioIdAndTipoAndStatus(
                anuncioId,
                TipoAnuncioMidia.FOTO,
                StatusAnuncioMidia.PUBLICAVEL);
        if (fotosPublicaveis <= LimiteMidiasAnuncioService.FOTOS_BASE) {
            return false;
        }

        var beneficio = beneficioRepository.findByCodigo(FOTOS_EXTRA_5).orElse(null);
        if (beneficio == null) {
            return false;
        }
        List<AtivacaoBeneficioEntity> aguardando = ativacaoRepository.findAguardandoModeracaoForUpdate(
                anuncioId,
                beneficio.getId(),
                StatusAtivacaoBeneficio.AGUARDANDO_MODERACAO);
        if (aguardando.isEmpty()) {
            return false;
        }
        if (aguardando.size() != 1) {
            throw new IllegalStateException("mais de uma ativacao de fotos extras aguarda moderacao");
        }

        AtivacaoBeneficioEntity ativacao = aguardando.get(0);
        BeneficioPremiumOpcaoEntity opcao = opcaoRepository.findById(ativacao.getOpcaoId())
                .orElseThrow(() -> new IllegalStateException("opcao da ativacao de fotos extras ausente"));
        if (opcao.getDuracaoDias() == null || opcao.getDuracaoDias() <= 0) {
            throw new IllegalStateException("duracao da ativacao de fotos extras invalida");
        }
        OffsetDateTime fimEm = aprovadoEm.plusDays(opcao.getDuracaoDias());
        GrupoAtivacaoBeneficioEntity grupo = grupoRepository.findByIdForUpdate(ativacao.getGrupoAtivacaoId())
                .orElseThrow(() -> new IllegalStateException("grupo da ativacao de fotos extras ausente"));
        if (!anuncioId.equals(grupo.getAnuncioId())
                || !ativacao.getUsuarioId().equals(grupo.getUsuarioId())) {
            throw new IllegalStateException("grupo da ativacao de fotos extras divergente");
        }

        String antes = snapshot(ativacao, fotosPublicaveis, null);
        if (!ativacao.iniciarAposModeracao(aprovadoEm, fimEm)) {
            return false;
        }
        grupo.estenderValidadeAte(fimEm, aprovadoEm);
        ativacaoRepository.save(ativacao);
        grupoRepository.save(grupo);
        auditoriaRepository.save(AuditoriaEventoEntity.registrar(
                UUID.randomUUID(),
                atorId,
                "PREMIUM_FOTOS_INICIAR_APOS_MODERACAO",
                "ATIVACAO_BENEFICIO",
                ativacao.getId(),
                antes,
                snapshot(ativacao, fotosPublicaveis, midiaId),
                requestId,
                aprovadoEm));
        return true;
    }

    private String snapshot(
            AtivacaoBeneficioEntity ativacao,
            long fotosPublicaveis,
            UUID midiaId) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("status", ativacao.getStatus().name());
        values.put("inicioEm", ativacao.getInicioEm());
        values.put("fimEm", ativacao.getFimEm());
        values.put("fotosPublicaveis", fotosPublicaveis);
        values.put("midiaAprovadaId", midiaId);
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("falha ao serializar auditoria de fotos extras", exception);
        }
    }
}
