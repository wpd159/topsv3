package br.com.topsdojob.v3.application.admin.desempenho;

import br.com.topsdojob.v3.application.admin.desempenho.dto.AdminDesempenhoComparativoPremiumDto;
import br.com.topsdojob.v3.application.admin.desempenho.dto.AdminDesempenhoDiarioDto;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AdminDesempenhoPremiumComparativoService {

    static final String MENSAGEM_SEGURA = "Beneficios Premium podem ampliar exposicao visual do anuncio. "
            + "Compare periodos com e sem beneficio ativo para entender tendencia. "
            + "Resultados variam conforme praca, anuncio, fotos, texto e demanda.";

    private final AdminDesempenhoSanitizer sanitizer;

    public AdminDesempenhoPremiumComparativoService(AdminDesempenhoSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    public AdminDesempenhoComparativoPremiumDto comparar(
            List<AdminDesempenhoDiarioDto> diario,
            List<String> beneficiosExposicaoAtivos) {
        long visualizacoesOrganicas = diario.stream()
                .filter(item -> !item.premiumAtivo())
                .mapToLong(AdminDesempenhoDiarioDto::visualizacoes)
                .sum();
        long cliquesOrganicos = diario.stream()
                .filter(item -> !item.premiumAtivo())
                .mapToLong(AdminDesempenhoDiarioDto::cliquesWhatsapp)
                .sum();
        long visualizacoesComPremium = diario.stream()
                .filter(AdminDesempenhoDiarioDto::premiumAtivo)
                .mapToLong(AdminDesempenhoDiarioDto::visualizacoes)
                .sum();
        long cliquesComPremium = diario.stream()
                .filter(AdminDesempenhoDiarioDto::premiumAtivo)
                .mapToLong(AdminDesempenhoDiarioDto::cliquesWhatsapp)
                .sum();
        return new AdminDesempenhoComparativoPremiumDto(
                visualizacoesOrganicas,
                cliquesOrganicos,
                sanitizer.taxaCliqueView(cliquesOrganicos, visualizacoesOrganicas),
                visualizacoesComPremium,
                cliquesComPremium,
                sanitizer.taxaCliqueView(cliquesComPremium, visualizacoesComPremium),
                beneficiosExposicaoAtivos == null ? List.of() : List.copyOf(beneficiosExposicaoAtivos),
                MENSAGEM_SEGURA,
                false,
                false,
                true);
    }
}
