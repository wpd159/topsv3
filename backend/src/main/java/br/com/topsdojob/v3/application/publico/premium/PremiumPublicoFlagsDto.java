package br.com.topsdojob.v3.application.publico.premium;

import java.util.List;

public record PremiumPublicoFlagsDto(
        boolean destaqueAtivo,
        boolean topoAtivo,
        boolean premiumAtivo,
        boolean possuiStories,
        boolean possuiMidiaExtra,
        List<String> beneficiosPublicos) {

    public static PremiumPublicoFlagsDto vazio() {
        return new PremiumPublicoFlagsDto(false, false, false, false, false, List.of());
    }
}
