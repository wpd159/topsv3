package br.com.topsdojob.v3.application.publico.mapper;

import br.com.topsdojob.v3.application.publico.dto.MidiaPublicaDto;
import br.com.topsdojob.v3.application.publico.service.MidiaPublicaUrlService;
import br.com.topsdojob.v3.application.publico.service.MidiaPublicaUrlService.ResultadoUrlPublica;
import br.com.topsdojob.v3.application.publico.service.MidiaRestritaDerivacaoService;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import br.com.topsdojob.v3.persistence.repository.projection.MidiaVinculoLeitura;
import br.com.topsdojob.v3.persistence.repository.projection.ArquivoPublicoLeitura;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MidiaPublicaMapper {

    private static final String MIDIA_RESTRITA_IDADE = "MIDIA_RESTRITA_IDADE";
    private static final Comparator<MidiaPublicaDto> ORDEM_DTO_GALERIA = Comparator
            .comparingInt((MidiaPublicaDto midia) -> prioridadeTipo(midia.tipo()))
            .thenComparing(MidiaPublicaDto::ordem, Comparator.nullsLast(Integer::compareTo))
            .thenComparing(MidiaPublicaDto::id, Comparator.nullsLast(UUID::compareTo));

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
        return publicas(vinculos, arquivosPorId, idadeConfirmada, maxFotos, true);
    }

    public List<MidiaPublicaDto> publicas(
            List<AnuncioMidiaEntity> vinculos,
            Map<UUID, ArquivoMidiaEntity> arquivosPorId,
            boolean idadeConfirmada,
            int maxFotos,
            boolean videoPermitido) {
        // Keep existing entity-based URL contracts for other consumers, while using
        // exactly the same position selector and DTO mapping as the projected read.
        var originais = new java.util.IdentityHashMap<MidiaVinculoLeitura, AnuncioMidiaEntity>();
        var leituras = vinculos.stream().map(item -> {
            var leitura = MidiaVinculoLeitura.de(item);
            originais.put(leitura, item);
            return leitura;
        }).toList();
        return SelecaoMidiasPublicas.selecionar(leituras, maxFotos, videoPermitido).stream()
                .map(vinculo -> {
                    var arquivo = vinculo.arquivoMidiaId() == null ? null : arquivosPorId.get(vinculo.arquivoMidiaId());
                    return toDto(vinculo, ArquivoPublicoLeitura.de(arquivo), idadeConfirmada,
                            () -> urlService.resolverPreviewRestrita(arquivo),
                            () -> urlService.resolver(originais.get(vinculo), arquivo));
                })
                .filter(java.util.Objects::nonNull).sorted(ORDEM_DTO_GALERIA).toList();
    }

    public List<MidiaPublicaDto> publicasLeituras(List<MidiaVinculoLeitura> vinculos,
            Map<UUID, ArquivoPublicoLeitura> arquivos, boolean idadeConfirmada,
            int maxFotos, boolean videoPermitido) {
        return SelecaoMidiasPublicas.selecionar(vinculos, maxFotos, videoPermitido).stream()
                .map(vinculo -> {
                    var arquivo = vinculo.arquivoMidiaId() == null ? null : arquivos.get(vinculo.arquivoMidiaId());
                    return toDto(vinculo, arquivo, idadeConfirmada,
                            () -> urlService.resolverPreviewRestritaLeitura(arquivo),
                            () -> urlService.resolverLeitura(vinculo, arquivo));
                })
                .filter(java.util.Objects::nonNull)
                .sorted(ORDEM_DTO_GALERIA).toList();
    }

    /** Resolve previews only after the original card policy chooses what is displayed. */
    public List<MidiaPublicaDto> publicasParaCard(
            List<AnuncioMidiaEntity> vinculos, Map<UUID, ArquivoMidiaEntity> arquivosPorId,
            int maxFotos, boolean videoPermitido, boolean carrosselAtivo,
            MidiaPublicaSeguraPolicy policy) {
        var originais = new java.util.IdentityHashMap<MidiaVinculoLeitura, AnuncioMidiaEntity>();
        var leituras = vinculos.stream().map(item -> {
            var leitura = MidiaVinculoLeitura.de(item);
            originais.put(leitura, item);
            return leitura;
        }).toList();
        var previews = new java.util.IdentityHashMap<MidiaPublicaDto,
                java.util.function.Supplier<MidiaPublicaDto>>();
        List<MidiaPublicaDto> candidatas = SelecaoMidiasPublicas
                .selecionar(leituras, maxFotos, videoPermitido).stream()
                .map(vinculo -> {
                    var arquivo = vinculo.arquivoMidiaId() == null ? null : arquivosPorId.get(vinculo.arquivoMidiaId());
                    var leitura = ArquivoPublicoLeitura.de(arquivo);
                    java.util.function.Supplier<ResultadoUrlPublica> original =
                            () -> urlService.resolver(originais.get(vinculo), arquivo);
                    // Card ordering depends on authorization/type/order/id, never on
                    // preview availability. Invalid files still consumed their slot.
                    var candidata = toDto(vinculo, leitura, false, () -> null, original);
                    if (candidata != null && vinculo.tipo() == TipoAnuncioMidia.FOTO
                            && vinculo.visibilidadeMidia() == VisibilidadeMidia.RESTRITA_18) {
                        previews.put(candidata, () -> toDto(vinculo, leitura, false,
                                () -> urlService.resolverPreviewRestrita(arquivo), original));
                    }
                    return candidata;
                }).filter(java.util.Objects::nonNull).toList();
        return policy.paraCard(candidatas, carrosselAtivo, videoPermitido).stream()
                .map(midia -> previews.containsKey(midia) ? previews.get(midia).get() : midia)
                .toList();
    }

    /**
     * Only inputs that can count as photos in anonymous locality eligibility. Position
     * selection remains shared with the gallery and precedes file/visibility filtering.
     * The original policy still decides eligibility from the resulting public URLs.
     */
    public List<MidiaPublicaDto> fotosElegiveisLocalidades(List<MidiaVinculoLeitura> vinculos,
            Map<UUID, ArquivoPublicoLeitura> arquivos, int maxFotos, boolean videoPermitido) {
        return SelecaoMidiasPublicas.selecionar(vinculos, maxFotos, videoPermitido).stream()
                .filter(vinculo -> vinculo.tipo() == TipoAnuncioMidia.FOTO
                        && vinculo.visibilidadeMidia() == VisibilidadeMidia.LIVRE)
                .map(vinculo -> {
                    var arquivo = vinculo.arquivoMidiaId() == null
                            ? null : arquivos.get(vinculo.arquivoMidiaId());
                    // Free photos never invoke the preview resolver in the shared mapping.
                    return toDto(vinculo, arquivo, false, () -> null,
                            () -> urlService.resolverLeitura(vinculo, arquivo));
                })
                .filter(java.util.Objects::nonNull).sorted(ORDEM_DTO_GALERIA).toList();
    }

    private static int prioridadeTipo(String tipo) {
        if (TipoAnuncioMidia.VIDEO.name().equals(tipo)) {
            return 0;
        }
        if (TipoAnuncioMidia.FOTO.name().equals(tipo)) {
            return 1;
        }
        return 2;
    }

    private MidiaPublicaDto toDto(MidiaVinculoLeitura vinculo, ArquivoPublicoLeitura arquivo,
            boolean idadeConfirmada, java.util.function.Supplier<ResultadoUrlPublica> resolverPreview,
            java.util.function.Supplier<ResultadoUrlPublica> resolverOriginal) {
        if (!isArquivoPublico(arquivo)) {
            return null;
        }
        boolean autorizada = vinculo.visibilidadeMidia() == VisibilidadeMidia.LIVRE || idadeConfirmada;
        ResultadoUrlPublica preview = vinculo.tipo() == TipoAnuncioMidia.FOTO
                && vinculo.visibilidadeMidia() == VisibilidadeMidia.RESTRITA_18
                ? resolverPreview.get()
                : new ResultadoUrlPublica(null, null);
        if (preview == null) {
            preview = new ResultadoUrlPublica(
                    null,
                    MidiaRestritaDerivacaoService.PENDENTE_DERIVACAO_RESTRITA);
        }
        ResultadoUrlPublica urlPublica = autorizada
                ? resolverOriginal.get()
                : new ResultadoUrlPublica(null, MIDIA_RESTRITA_IDADE);
        String pendencia = urlPublica.pendenciaMidia();
        if (!autorizada && preview.urlPublica() == null) {
            pendencia = preview.pendenciaMidia();
        }
        return new MidiaPublicaDto(
                vinculo.id(),
                enumName(vinculo.tipo()),
                enumName(vinculo.finalidade()),
                vinculo.ordem(),
                enumName(vinculo.visibilidadeMidia()),
                autorizada,
                urlPublica.urlPublica(),
                preview.urlPublica(),
                pendencia,
                arquivo.largura(),
                arquivo.altura(),
                arquivo.mimeType());
    }

    private boolean isArquivoPublico(ArquivoPublicoLeitura arquivo) {
        return arquivo != null
                && arquivo.statusArquivo() == StatusArquivoMidia.VALIDADO;
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
