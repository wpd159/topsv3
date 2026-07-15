package br.com.topsdojob.v3.application.premium;

import java.util.Set;

public final class PremiumBeneficioCodigo {

    public static final String OCULTAR_IDADE = "OCULTAR_IDADE";
    public static final String FOTOS_EXTRA_5 = "FOTOS_EXTRA_5";
    public static final String ANUNCIO_TOPO = "ANUNCIO_TOPO";
    public static final String WHATSAPP_CARD = "WHATSAPP_CARD";
    public static final String CARROSSEL_FOTOS = "CARROSSEL_FOTOS";
    public static final String VIDEO_1 = "VIDEO_1";

    public static final Set<String> TODOS = Set.of(
            OCULTAR_IDADE,
            FOTOS_EXTRA_5,
            ANUNCIO_TOPO,
            WHATSAPP_CARD,
            CARROSSEL_FOTOS,
            VIDEO_1);

    public static final Set<String> MIDIA_EXTRA = Set.of(
            FOTOS_EXTRA_5,
            CARROSSEL_FOTOS,
            VIDEO_1);

    private PremiumBeneficioCodigo() {
    }
}
