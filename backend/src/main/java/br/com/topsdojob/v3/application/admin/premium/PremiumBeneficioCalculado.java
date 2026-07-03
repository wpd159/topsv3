package br.com.topsdojob.v3.application.admin.premium;

import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumEntity;
import br.com.topsdojob.v3.persistence.entity.premium.GrupoAtivacaoBeneficioEntity;
import java.util.List;

public record PremiumBeneficioCalculado(
        AtivacaoBeneficioEntity ativacao,
        BeneficioPremiumEntity beneficio,
        GrupoAtivacaoBeneficioEntity grupo,
        PremiumBeneficioStatusCalculado status,
        List<PremiumConsistenciaCodigo> codigos,
        boolean venceEmBreve,
        boolean inconsistente) {
}
