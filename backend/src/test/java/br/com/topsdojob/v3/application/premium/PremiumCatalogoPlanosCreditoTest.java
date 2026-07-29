package br.com.topsdojob.v3.application.premium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.persistence.entity.financeiro.PlanoCreditoEntity;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumOpcaoRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumRepository;
import br.com.topsdojob.v3.persistence.repository.PlanoCreditoRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PremiumCatalogoPlanosCreditoTest {

    @Test
    void catalogoDeCompraRetornaSomentePlanosAtivosDoRepositorioCanonico() {
        BeneficioPremiumRepository beneficios = mock(BeneficioPremiumRepository.class);
        BeneficioPremiumOpcaoRepository opcoes = mock(BeneficioPremiumOpcaoRepository.class);
        PlanoCreditoRepository planos = mock(PlanoCreditoRepository.class);
        PlanoCreditoEntity ativo = plano("PACOTE_ATIVO", true);
        PlanoCreditoEntity inativo = plano("PACOTE_INATIVO", false);
        when(planos.findByAtivoTrueOrderByOrdemExibicaoAscCodigoAsc()).thenReturn(List.of(ativo));
        when(planos.findAllByOrderByOrdemExibicaoAscCodigoAsc()).thenReturn(List.of(ativo, inativo));
        PremiumCatalogoService service = new PremiumCatalogoService(beneficios, opcoes, planos);

        assertThat(service.pacotesAtivos())
                .extracting(item -> item.codigo())
                .containsExactly("PACOTE_ATIVO");
        assertThat(service.pacotesAdministrativos())
                .extracting(item -> item.codigo())
                .containsExactly("PACOTE_ATIVO", "PACOTE_INATIVO");
    }

    private PlanoCreditoEntity plano(String codigo, boolean ativo) {
        return PlanoCreditoEntity.criar(
                UUID.randomUUID(),
                codigo,
                codigo,
                "",
                100,
                new BigDecimal("49.90"),
                ativo,
                10,
                OffsetDateTime.parse("2026-07-28T12:00:00Z"));
    }
}
