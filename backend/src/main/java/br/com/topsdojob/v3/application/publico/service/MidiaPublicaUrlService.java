package br.com.topsdojob.v3.application.publico.service;

import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import org.springframework.stereotype.Service;

@Service
public class MidiaPublicaUrlService {

    public static final String PENDENTE_URL_PUBLICA_MIDIA_CDN = "PENDENTE_URL_PUBLICA_MIDIA_CDN";

    public ResultadoUrlPublica resolver(AnuncioMidiaEntity vinculo, ArquivoMidiaEntity arquivo) {
        if (vinculo == null || arquivo == null) {
            return new ResultadoUrlPublica(null, PENDENTE_URL_PUBLICA_MIDIA_CDN);
        }
        return new ResultadoUrlPublica(null, PENDENTE_URL_PUBLICA_MIDIA_CDN);
    }

    public record ResultadoUrlPublica(
            String urlPublica,
            String pendenciaMidia) {
    }
}
