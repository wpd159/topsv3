package br.com.topsdojob.v3.application.publico.service;

import br.com.topsdojob.v3.application.publico.dto.PoliticaContatoPublicoDto;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import org.springframework.stereotype.Service;

@Service
public class PoliticaContatoPublicoService {

    private static final String MOTIVO_DISPONIVEL = "CONTATO_PUBLICO_AUTORIZADO";
    private static final String MOTIVO_INDISPONIVEL = "CONTATO_PUBLICO_INDISPONIVEL";

    public PoliticaContatoPublicoDto avaliar(AnuncioEntity anuncio) {
        if (podeExporContato(anuncio)) {
            return new PoliticaContatoPublicoDto(true, MOTIVO_DISPONIVEL, null);
        }
        return new PoliticaContatoPublicoDto(false, MOTIVO_INDISPONIVEL, null);
    }

    public boolean podeExporContato(AnuncioEntity anuncio) {
        return anuncio != null
                && anuncio.getStatus() == StatusAnuncio.PUBLICADO
                && anuncio.getStatusModeracao() == StatusModeracaoAnuncio.APROVADO
                && anuncio.getRemovidoEm() == null
                && whatsappValido(anuncio.getWhatsappNormalizado());
    }

    public String whatsappUrl(AnuncioEntity anuncio) {
        if (!podeExporContato(anuncio)) {
            return null;
        }
        String digits = anuncio.getWhatsappNormalizado().replace("+", "");
        return "https://wa.me/" + digits;
    }

    private boolean whatsappValido(String value) {
        return value != null && value.matches("^\\+[1-9][0-9]{7,14}$");
    }
}
