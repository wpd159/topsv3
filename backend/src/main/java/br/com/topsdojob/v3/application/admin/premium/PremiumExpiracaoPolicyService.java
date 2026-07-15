package br.com.topsdojob.v3.application.admin.premium;

import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumEntity;
import br.com.topsdojob.v3.persistence.entity.premium.GrupoAtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAtivacaoBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusGrupoAtivacaoBeneficio;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

        if (agora == null) {
            codigos.add(PremiumConsistenciaCodigo.REFERENCIA_TEMPORAL_INVALIDA);
        }
        if (ativacao.getInicioEm() == null
                || ativacao.getFimEm() == null
                || !ativacao.getFimEm().isAfter(ativacao.getInicioEm())) {
            codigos.add(PremiumConsistenciaCodigo.DATA_INVALIDA);
        }
        if (ativacao.getOrigem() == null) {
            codigos.add(PremiumConsistenciaCodigo.ORIGEM_DESCONHECIDA);
        }
        if (ativacao.getStatus() == null) {
            codigos.add(PremiumConsistenciaCodigo.STATUS_INVALIDO);
        }
        if (beneficio == null || !Objects.equals(beneficio.getId(), ativacao.getBeneficioId())) {
            codigos.add(PremiumConsistenciaCodigo.BENEFICIO_NAO_ENCONTRADO);
        }
        if (grupo == null) {
            codigos.add(PremiumConsistenciaCodigo.BENEFICIO_SEM_GRUPO);
        } else {
            validarGrupo(ativacao, grupo, codigos);
        }

        boolean inconsistenteAntesDoStatus = codigos.stream()
                .anyMatch(PremiumConsistenciaCodigo::inconsistencia);
        PremiumBeneficioStatusCalculado status = inconsistenteAntesDoStatus
                ? PremiumBeneficioStatusCalculado.INCONSISTENTE
                : calcularStatus(ativacao, beneficio, grupo, agora, codigos);
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
            BeneficioPremiumEntity beneficio,
            GrupoAtivacaoBeneficioEntity grupo,
            OffsetDateTime agora,
            List<PremiumConsistenciaCodigo> codigos) {
        if (!Boolean.TRUE.equals(beneficio.getAtivo())) {
            codigos.add(PremiumConsistenciaCodigo.BENEFICIO_CATALOGO_INATIVO);
            return PremiumBeneficioStatusCalculado.INATIVO;
        }
        if (canceladaOuRevogada(ativacao)) {
            codigos.add(PremiumConsistenciaCodigo.BENEFICIO_CANCELADO_OU_REVOGADO);
            return PremiumBeneficioStatusCalculado.INATIVO;
        }
        if (grupoCanceladoOuRevogado(grupo)) {
            codigos.add(PremiumConsistenciaCodigo.GRUPO_INATIVO);
            return PremiumBeneficioStatusCalculado.INATIVO;
        }
        if (grupo.getStatus() == StatusGrupoAtivacaoBeneficio.PLANEJADO
                || grupo.getValidadeInicioEm().isAfter(agora)) {
            codigos.add(PremiumConsistenciaCodigo.GRUPO_PENDENTE);
            return PremiumBeneficioStatusCalculado.PENDENTE;
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
        if (ativacao.getStatus() == StatusAtivacaoBeneficio.AGENDADA
                || ativacao.getInicioEm().isAfter(agora)) {
            codigos.add(PremiumConsistenciaCodigo.BENEFICIO_PENDENTE);
            return PremiumBeneficioStatusCalculado.PENDENTE;
        }
        if (ativacao.getStatus() == StatusAtivacaoBeneficio.EXPIRADA
                || !ativacao.getFimEm().isAfter(agora)) {
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
        codigos.add(PremiumConsistenciaCodigo.BENEFICIO_INATIVO);
        return PremiumBeneficioStatusCalculado.INATIVO;
    }

    private void validarGrupo(
            AtivacaoBeneficioEntity ativacao,
            GrupoAtivacaoBeneficioEntity grupo,
            List<PremiumConsistenciaCodigo> codigos) {
        if (grupo.getValidadeInicioEm() == null
                || grupo.getValidadeFimEm() == null
                || !grupo.getValidadeFimEm().isAfter(grupo.getValidadeInicioEm())) {
            codigos.add(PremiumConsistenciaCodigo.DATA_INVALIDA);
            return;
        }
        if (grupo.getStatus() == null) {
            codigos.add(PremiumConsistenciaCodigo.STATUS_INVALIDO);
        }
        if (grupo.getOrigem() == null || ativacao.getOrigem() != grupo.getOrigem()) {
            codigos.add(PremiumConsistenciaCodigo.ORIGEM_GRUPO_DIVERGENTE);
        }
        if (!Objects.equals(grupo.getId(), ativacao.getGrupoAtivacaoId())
                || !Objects.equals(grupo.getUsuarioId(), ativacao.getUsuarioId())
                || !Objects.equals(grupo.getAnuncioId(), ativacao.getAnuncioId())) {
            codigos.add(PremiumConsistenciaCodigo.VINCULO_GRUPO_DIVERGENTE);
        }
        if (ativacao.getInicioEm() != null
                && ativacao.getFimEm() != null
                && (ativacao.getInicioEm().isBefore(grupo.getValidadeInicioEm())
                || ativacao.getFimEm().isAfter(grupo.getValidadeFimEm()))) {
            codigos.add(PremiumConsistenciaCodigo.BENEFICIO_FORA_JANELA_GRUPO);
        }
    }

    private boolean canceladaOuRevogada(AtivacaoBeneficioEntity ativacao) {
        return ativacao.getStatus() == StatusAtivacaoBeneficio.CANCELADA
                || ativacao.getStatus() == StatusAtivacaoBeneficio.REVOGADA
                || ativacao.getRevogadaEm() != null;
    }

    private boolean grupoCanceladoOuRevogado(GrupoAtivacaoBeneficioEntity grupo) {
        return grupo.getStatus() == StatusGrupoAtivacaoBeneficio.CANCELADO
                || grupo.getStatus() == StatusGrupoAtivacaoBeneficio.REVOGADO;
    }

    private boolean grupoExpirado(GrupoAtivacaoBeneficioEntity grupo, OffsetDateTime agora) {
        return grupo.getStatus() == StatusGrupoAtivacaoBeneficio.EXPIRADO
                || !grupo.getValidadeFimEm().isAfter(agora);
    }

    private boolean beneficioExpirouAntesDoGrupo(
            AtivacaoBeneficioEntity ativacao,
            GrupoAtivacaoBeneficioEntity grupo,
            OffsetDateTime agora) {
        return ativacao.getFimEm().isBefore(grupo.getValidadeFimEm())
                && !ativacao.getFimEm().isAfter(agora);
    }
}
