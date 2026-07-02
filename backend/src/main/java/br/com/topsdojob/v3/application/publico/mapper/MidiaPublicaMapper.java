package br.com.topsdojob.v3.application.publico.mapper;

import br.com.topsdojob.v3.application.publico.dto.MidiaPublicaDto;
import br.com.topsdojob.v3.application.publico.service.MidiaPublicaUrlService;
import br.com.topsdojob.v3.application.publico.service.MidiaPublicaUrlService.ResultadoUrlPublica;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ClassificacaoConteudo;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MidiaPublicaMapper {

    private final MidiaPublicaUrlService urlService;

    public MidiaPublicaMapper(MidiaPublicaUrlService urlService) {
        this.urlService = urlService;
    }

    public List<MidiaPublicaDto> publicas(
            List<AnuncioMidiaEntity> vinculos,
            Map<UUID, ArquivoMidiaEntity> arquivosPorId) {
        return publicas(vinculos, arquivosPorId, false);
    }

    public List<MidiaPublicaDto> publicas(
            List<AnuncioMidiaEntity> vinculos,
            Map<UUID, ArquivoMidiaEntity> arquivosPorId,
            boolean idadeConfirmada) {
        return vinculos.stream()
                .filter(vinculo -> isVinculoPublico(vinculo, idadeConfirmada))
                .map(vinculo -> toDto(vinculo, arquivosPorId.get(vinculo.getArquivoMidiaId()), idadeConfirmada))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(
                        MidiaPublicaDto::ordem,
                        Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    private MidiaPublicaDto toDto(AnuncioMidiaEntity vinculo, ArquivoMidiaEntity arquivo, boolean idadeConfirmada) {
        if (!isArquivoPublico(arquivo, idadeConfirmada)) {
            return null;
        }
        ResultadoUrlPublica urlPublica = urlService.resolver(vinculo, arquivo);
        return new MidiaPublicaDto(
                enumName(vinculo.getTipo()),
                enumName(vinculo.getFinalidade()),
                vinculo.getOrdem(),
                urlPublica.urlPublica(),
                urlPublica.pendenciaMidia(),
                arquivo.getLargura(),
                arquivo.getAltura(),
                arquivo.getMimeType());
    }

    private boolean isVinculoPublico(AnuncioMidiaEntity vinculo, boolean idadeConfirmada) {
        return vinculo != null
                && vinculo.getStatus() == StatusAnuncioMidia.PUBLICAVEL
                && vinculo.getTipo() != TipoAnuncioMidia.STORY
                && vinculo.getFinalidade() != FinalidadeAnuncioMidia.STORY
                && isClassificacaoPublicavel(vinculo.getClassificacaoConteudo(), idadeConfirmada);
    }

    private boolean isArquivoPublico(ArquivoMidiaEntity arquivo, boolean idadeConfirmada) {
        return arquivo != null
                && arquivo.getStatusArquivo() == StatusArquivoMidia.VALIDADO
                && isClassificacaoPublicavel(arquivo.getClassificacaoConteudo(), idadeConfirmada);
    }

    private boolean isClassificacaoPublicavel(ClassificacaoConteudo classificacao, boolean idadeConfirmada) {
        return classificacao == ClassificacaoConteudo.LIVRE
                || (idadeConfirmada && classificacao == ClassificacaoConteudo.BLOQUEADO);
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
