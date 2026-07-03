package br.com.topsdojob.v3.application.admin.premium;

import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumEntity;
import br.com.topsdojob.v3.persistence.entity.premium.GrupoAtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAtivacaoBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusGrupoAtivacaoBeneficio;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PremiumExpiracaoPolicyService {

    public static final int JANELA_VENCENDO_DIAS = 7;

    public PremiumBeneficioCalculado avaliar(
            AtivacaoBeneficioEntity ativacao,
            BeneficioPremiumEntity beneficio,
            GrupoAtivacaoBeneficioEntity grupo,
            OffsetDateTime agora) {
        List<PremiumConsistenciaCodigo> codigos = new ArrayList<>();
        if (ativacao == null) {
            return new PremiumBeneficioCalculado(
                    null,
                    beneficio,
                    grupo,
                    PremiumBeneficioStatusCalculado.INCONSISTENTE,
                    List.of(PremiumConsistenciaCodigo.DATA_INVALIDA),
                    false,
                    true);
        }

        boolean dataInvalida = ativacao.getInicioEm() == null
                || ativacao.getFimEm() == null
                || !ativacao.getFimEm().isAfter(ativacao.getInicioEm());
        if (dataInvalida) {
            codigos.add(PremiumConsistenciaCodigo.DATA_INVALIDA);
        }
        if (ativacao.getOrigem() == null) {
            codigos.add(PremiumConsistenciaCodigo.ORIGEM_DESCONHECIDA);
        }
        if (grupo == null) {
            codigos.add(PremiumConsistenciaCodigo.BENEFICIO_SEM_GRUPO);
        }

        PremiumBeneficioStatusCalculado status = calcularStatus(ativacao, grupo, agora, codigos, dataInvalida);
        boolean venceEmBreve = status == PremiumBeneficioStatusCalculado.VENCENDO;
        boolean inconsistente = codigos.stream().anyMatch(PremiumConsistenciaCodigo::inconsistencia);
        if (codigos.isEmpty()) {
            codigos.add(PremiumConsistenciaCodigo.PREMIUM_OK);
        }
        return new PremiumBeneficioCalculado(
                ativacao,
                beneficio,
                grupo,
                status,
                List.copyOf(codigos),
                venceEmBreve,
                inconsistente);
    }

    private PremiumBeneficioStatusCalculado calcularStatus(
            AtivacaoBeneficioEntity ativacao,
            GrupoAtivacaoBeneficioEntity grupo,
            OffsetDateTime agora,
            List<PremiumConsistenciaCodigo> codigos,
            boolean dataInvalida) {
        if (dataInvalida) {
            return PremiumBeneficioStatusCalculado.INCONSISTENTE;
        }
        if (grupoExpirado(grupo, agora)) {
            if (ativacao.getStatus() == StatusAtivacaoBeneficio.ATIVA) {
                codigos.add(PremiumConsistenciaCodigo.GRUPO_EXPIRADO_COM_BENEFICIO_ATIVO);
            }
            codigos.add(PremiumConsistenciaCodigo.BENEFICIO_EXPIRADO);
            return PremiumBeneficioStatusCalculado.EXPIRADO;
        }
        if (beneficioExpirouAntesDoGrupo(ativacao, grupo, agora)) {
            codigos.add(PremiumConsistenciaCodigo.BENEFICIO_EXPIRADO_ANTES_DO_GRUPO);
        }
        if (ativacao.getStatus() == StatusAtivacaoBeneficio.AGENDADA || ativacao.getInicioEm().isAfter(agora)) {
            codigos.add(PremiumConsistenciaCodigo.BENEFICIO_PENDENTE);
            return PremiumBeneficioStatusCalculado.PENDENTE;
        }
        if (ativacao.getStatus() == StatusAtivacaoBeneficio.EXPIRADA || !ativacao.getFimEm().isAfter(agora)) {
            codigos.add(PremiumConsistenciaCodigo.BENEFICIO_EXPIRADO);
            return PremiumBeneficioStatusCalculado.EXPIRADO;
        }
        if (ativacao.getStatus() == StatusAtivacaoBeneficio.ATIVA) {
            if (!ativacao.getFimEm().isAfter(agora.plusDays(JANELA_VENCENDO_DIAS))) {
                codigos.add(PremiumConsistenciaCodigo.BENEFICIO_VENCE_EM_BREVE);
                return PremiumBeneficioStatusCalculado.VENCENDO;
            }
            codigos.add(PremiumConsistenciaCodigo.BENEFICIO_ATIVO);
            return PremiumBeneficioStatusCalculado.ATIVO;
        }
        return PremiumBeneficioStatusCalculado.INATIVO;
    }

    private boolean grupoExpirado(GrupoAtivacaoBeneficioEntity grupo, OffsetDateTime agora) {
        return grupo != null
                && (grupo.getStatus() == StatusGrupoAtivacaoBeneficio.EXPIRADO
                || (grupo.getValidadeFimEm() != null && !grupo.getValidadeFimEm().isAfter(agora)));
    }

    private boolean beneficioExpirouAntesDoGrupo(
            AtivacaoBeneficioEntity ativacao,
            GrupoAtivacaoBeneficioEntity grupo,
            OffsetDateTime agora) {
        return grupo != null
                && grupo.getValidadeFimEm() != null
                && ativacao.getFimEm() != null
                && ativacao.getFimEm().isBefore(grupo.getValidadeFimEm())
                && !ativacao.getFimEm().isAfter(agora);
    }
}
