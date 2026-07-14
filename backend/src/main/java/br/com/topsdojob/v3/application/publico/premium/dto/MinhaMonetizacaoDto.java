package br.com.topsdojob.v3.application.publico.premium.dto;

import br.com.topsdojob.v3.application.premium.dto.PlanoCreditoDto;
import br.com.topsdojob.v3.application.premium.dto.PremiumCatalogoDto;
import java.util.List;

public record MinhaMonetizacaoDto(
        int saldoCreditos,
        List<MinhaCreditoMovimentoDto> historico,
        List<PremiumCatalogoDto> catalogo,
        List<PlanoCreditoDto> pacotesCredito,
        List<MinhaAtivacaoPremiumDto> beneficiosAtivos) {
}
