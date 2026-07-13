package br.com.topsdojob.v3.application.publico.mapper;

import br.com.topsdojob.v3.application.publico.dto.MidiaPublicaDto;
import br.com.topsdojob.v3.application.publico.service.MidiaPublicaUrlService;
import br.com.topsdojob.v3.application.publico.service.MidiaPublicaUrlService.ResultadoUrlPublica;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
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
        return publicas(vinculos, arquivosPorId, idadeConfirmada, Integer.MAX_VALUE);
    }

    public List<MidiaPublicaDto> publicas(
            List<AnuncioMidiaEntity> vinculos,
            Map<UUID, ArquivoMidiaEntity> arquivosPorId,
            boolean idadeConfirmada,
            int maxFotos) {
        java.util.concurrent.atomic.AtomicInteger fotos = new java.util.concurrent.atomic.AtomicInteger();
        return vinculos.stream()
                .filter(this::isVinculoPublico)
                .sorted(Comparator.comparing(
                        AnuncioMidiaEntity::getOrdem,
                        Comparator.nullsLast(Integer::compareTo)))
                .filter(vinculo -> vinculo.getTipo() != TipoAnuncioMidia.FOTO
                        || fotos.getAndIncrement() < Math.max(0, maxFotos))
                .map(vinculo -> toDto(vinculo, arquivosPorId.get(vinculo.getArquivoMidiaId()), idadeConfirmada))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(
                        MidiaPublicaDto::ordem,
                        Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    private MidiaPublicaDto toDto(AnuncioMidiaEntity vinculo, ArquivoMidiaEntity arquivo, boolean idadeConfirmada) {
        if (!isArquivoPublico(arquivo)) {
            return null;
        }
        boolean autorizada = vinculo.getVisibilidadeMidia() == VisibilidadeMidia.LIVRE || idadeConfirmada;
        ResultadoUrlPublica urlPublica = autorizada
                ? urlService.resolver(vinculo, arquivo)
                : new ResultadoUrlPublica(null, "MIDIA_RESTRITA_IDADE");
        return new MidiaPublicaDto(
                vinculo.getId(),
                enumName(vinculo.getTipo()),
                enumName(vinculo.getFinalidade()),
                vinculo.getOrdem(),
                enumName(vinculo.getVisibilidadeMidia()),
                autorizada,
                urlPublica.urlPublica(),
                urlPublica.pendenciaMidia(),
                arquivo.getLargura(),
                arquivo.getAltura(),
                arquivo.getMimeType());
    }

    private boolean isVinculoPublico(AnuncioMidiaEntity vinculo) {
        return vinculo != null
                && vinculo.getStatus() == StatusAnuncioMidia.PUBLICAVEL
                && vinculo.getTipo() != TipoAnuncioMidia.STORY
                && vinculo.getFinalidade() != FinalidadeAnuncioMidia.STORY
                && vinculo.getVisibilidadeMidia() != null;
    }

    private boolean isArquivoPublico(ArquivoMidiaEntity arquivo) {
        return arquivo != null
                && arquivo.getStatusArquivo() == StatusArquivoMidia.VALIDADO;
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
