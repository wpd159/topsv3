package br.com.topsdojob.v3.application.publico.service;

import static br.com.topsdojob.v3.application.publico.anunciante.midia.LimiteMidiasAnuncioService.FOTOS_BASE;
import static br.com.topsdojob.v3.application.publico.anunciante.midia.LimiteMidiasAnuncioService.FOTOS_COM_EXTRA;

import br.com.topsdojob.v3.application.publico.dto.LocalizacaoPublicaDto;
import br.com.topsdojob.v3.application.publico.dto.MidiaPublicaDto;
import br.com.topsdojob.v3.application.publico.mapper.MidiaPublicaMapper;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoFlagsDto;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoMapper;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.repository.projection.MidiaVinculoLeitura;
import br.com.topsdojob.v3.persistence.repository.projection.ArquivoPublicoLeitura;
import br.com.topsdojob.v3.application.publico.mapper.SelecaoMidiasPublicas;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnuncioSeoElegibilidadeConsultaService {

    private final AnuncioMidiaRepository anuncioMidiaRepository;
    private final ArquivoMidiaRepository arquivoMidiaRepository;
    private final MidiaPublicaMapper midiaMapper;
    private final PremiumPublicoMapper premiumMapper;
    private final AnuncioSeoIndexabilidadePolicy indexabilidadePolicy;

    public AnuncioSeoElegibilidadeConsultaService(
            AnuncioMidiaRepository anuncioMidiaRepository,
            ArquivoMidiaRepository arquivoMidiaRepository,
            MidiaPublicaMapper midiaMapper,
            PremiumPublicoMapper premiumMapper,
            AnuncioSeoIndexabilidadePolicy indexabilidadePolicy) {
        this.anuncioMidiaRepository = anuncioMidiaRepository;
        this.arquivoMidiaRepository = arquivoMidiaRepository;
        this.midiaMapper = midiaMapper;
        this.premiumMapper = premiumMapper;
        this.indexabilidadePolicy = indexabilidadePolicy;
    }

    @Transactional(readOnly = true)
    public Map<UUID, Resultado> avaliar(
            Collection<AnuncioEntity> anuncios,
            Map<UUID, LocalizacaoPublicaDto> localizacoes) {
        return avaliar(anuncios, localizacoes, false);
    }

    /** Anonymous locality eligibility does not depend on restricted previews. */
    @Transactional(readOnly = true)
    public Map<UUID, Resultado> avaliarLocalidades(
            Collection<AnuncioEntity> anuncios,
            Map<UUID, LocalizacaoPublicaDto> localizacoes) {
        return avaliar(anuncios, localizacoes, true);
    }

    private Map<UUID, Resultado> avaliar(Collection<AnuncioEntity> anuncios,
            Map<UUID, LocalizacaoPublicaDto> localizacoes, boolean somenteFotosLivres) {
        if (anuncios == null || anuncios.isEmpty()) return Map.of();
        Map<UUID, AnuncioEntity> unicos = anuncios.stream().filter(Objects::nonNull)
                .filter(anuncio -> anuncio.getId() != null)
                .collect(Collectors.toMap(AnuncioEntity::getId, Function.identity(),
                        (primeiro, ignorado) -> primeiro, LinkedHashMap::new));
        if (unicos.isEmpty()) return Map.of();
        var ids = List.copyOf(unicos.keySet());
        var vinculos = vinculos(ids);
        var premium = medirLocalidades("beneficios", () -> premiumMapper.flagsMidiaPorAnuncioIds(ids));
        var selecionados = selecionar(ids, vinculos, premium);
        // Select positions over ALL links before filtering the files to read. Restricted,
        // missing and later-invalid files must not promote a photo beyond the limit.
        var arquivoIds = somenteFotosLivres
                ? selecionados.values().stream().flatMap(Collection::stream)
                        .filter(vinculo -> vinculo.tipo() == TipoAnuncioMidia.FOTO
                                && vinculo.visibilidadeMidia() == VisibilidadeMidia.LIVRE)
                        .map(MidiaVinculoLeitura::arquivoMidiaId).filter(Objects::nonNull).distinct().toList()
                : arquivosSelecionados(selecionados, false);
        Map<UUID, ArquivoPublicoLeitura> arquivos = arquivoIds.isEmpty() ? Map.of()
                : medirLocalidades("arquivos", () -> arquivoMidiaRepository.findLeiturasPublicas(arquivoIds))
                        .stream().collect(Collectors.toMap(ArquivoPublicoLeitura::id, Function.identity()));
        Map<UUID, Resultado> resultados = new LinkedHashMap<>();
        for (var anuncio : unicos.values()) {
            conferir();
            var flags = premium.getOrDefault(anuncio.getId(), PremiumPublicoFlagsDto.vazio());
            int limiteFotos = flags.fotosExtrasAtivo() ? FOTOS_COM_EXTRA : FOTOS_BASE;
            List<MidiaPublicaDto> midias = somenteFotosLivres
                    ? midiaMapper.fotosElegiveisLocalidades(
                            vinculos.getOrDefault(anuncio.getId(), List.of()), arquivos,
                            limiteFotos, flags.videoAtivo())
                    : midiaMapper.publicasLeituras(selecionados.get(anuncio.getId()), arquivos,
                            false, limiteFotos, flags.videoAtivo());
            boolean indexavel = indexabilidadePolicy.indexavel(anuncio,
                    localizacoes == null ? null : localizacoes.get(anuncio.getId()), midias);
            OffsetDateTime atualizado = vinculos.getOrDefault(anuncio.getId(), List.of()).stream()
                    .map(MidiaVinculoLeitura::atualizadoEm).filter(Objects::nonNull)
                    .max(OffsetDateTime::compareTo).orElse(null);
            resultados.put(anuncio.getId(), new Resultado(indexavel, atualizado));
        }
        return Map.copyOf(resultados);
    }

    @Transactional(readOnly = true)
    public void coletarPreviews(Collection<AnuncioEntity> anuncios) {
        if (anuncios == null) return;
        coletarPreviewsPorIds(anuncios.stream().filter(Objects::nonNull)
                .map(AnuncioEntity::getId).filter(Objects::nonNull).toList());
    }

    @Transactional(readOnly = true)
    public void coletarPreviewsPorIds(Collection<UUID> anuncios) {
        if (anuncios == null || anuncios.isEmpty()) return;
        var ids = anuncios.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return;
        var vinculos = vinculos(ids);
        var premium = medirLocalidades("beneficios", () -> premiumMapper.flagsMidiaPorAnuncioIds(ids));
        var selecionados = selecionar(ids, vinculos, premium);
        // Free and invalid photos have already consumed their positions.
        var arquivoIds = arquivosSelecionados(selecionados, true);
        if (!arquivoIds.isEmpty()) {
            var identidades = medirLocalidades("arquivos",
                    () -> arquivoMidiaRepository.findIdentidadesPreview(arquivoIds));
            for (var arquivo : identidades) {
                conferir();
                midiaMapper.coletarPreview(arquivo);
            }
        }
        conferir();
    }

    private Map<UUID, List<MidiaVinculoLeitura>> vinculos(Collection<UUID> ids) {
        return medirLocalidades("midias", () -> anuncioMidiaRepository.findLeiturasPublicas(ids))
                .stream().collect(Collectors.groupingBy(MidiaVinculoLeitura::anuncioId));
    }

    private Map<UUID, List<MidiaVinculoLeitura>> selecionar(Collection<UUID> ids,
            Map<UUID, List<MidiaVinculoLeitura>> vinculos, Map<UUID, PremiumPublicoFlagsDto> premium) {
        Map<UUID, List<MidiaVinculoLeitura>> resultado = new LinkedHashMap<>();
        for (UUID id : ids) {
            conferir();
            var flags = premium.getOrDefault(id, PremiumPublicoFlagsDto.vazio());
            resultado.put(id, SelecaoMidiasPublicas.selecionar(vinculos.getOrDefault(id, List.of()),
                    flags.fotosExtrasAtivo() ? FOTOS_COM_EXTRA : FOTOS_BASE, flags.videoAtivo()));
        }
        return resultado;
    }

    private List<UUID> arquivosSelecionados(Map<UUID, List<MidiaVinculoLeitura>> selecionados,
            boolean somentePreviews) {
        return selecionados.values().stream().flatMap(Collection::stream)
                .filter(vinculo -> !somentePreviews || (vinculo.tipo() == TipoAnuncioMidia.FOTO
                        && vinculo.visibilidadeMidia() == VisibilidadeMidia.RESTRITA_18))
                .map(MidiaVinculoLeitura::arquivoMidiaId).filter(Objects::nonNull).distinct().toList();
    }

    private void conferir() {
        var orcamento = LocalidadesConsultaOrcamento.atualOuNulo();
        if (orcamento != null) orcamento.conferir();
    }

    public record Resultado(boolean indexavel, OffsetDateTime ultimaAtualizacaoMidia) {
    }

    private <T> T medirLocalidades(String fase, Supplier<T> consulta) {
        LocalidadesConsultaOrcamento orcamento = LocalidadesConsultaOrcamento.atualOuNulo();
        return orcamento == null ? consulta.get() : orcamento.medir(fase, consulta);
    }
}
