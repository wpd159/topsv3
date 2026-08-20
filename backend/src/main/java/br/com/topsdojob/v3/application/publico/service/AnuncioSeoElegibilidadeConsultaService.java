package br.com.topsdojob.v3.application.publico.service;

import static br.com.topsdojob.v3.application.publico.anunciante.midia.LimiteMidiasAnuncioService.FOTOS_BASE;
import static br.com.topsdojob.v3.application.publico.anunciante.midia.LimiteMidiasAnuncioService.FOTOS_COM_EXTRA;

import br.com.topsdojob.v3.application.publico.dto.LocalizacaoPublicaDto;
import br.com.topsdojob.v3.application.publico.dto.MidiaPublicaDto;
import br.com.topsdojob.v3.application.publico.mapper.MidiaPublicaMapper;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoFlagsDto;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoMapper;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
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
        if (anuncios == null || anuncios.isEmpty()) {
            return Map.of();
        }

        Map<UUID, AnuncioEntity> anunciosUnicos = anuncios.stream()
                .filter(Objects::nonNull)
                .filter(anuncio -> anuncio.getId() != null)
                .collect(Collectors.toMap(
                        AnuncioEntity::getId,
                        Function.identity(),
                        (primeiro, ignorado) -> primeiro,
                        LinkedHashMap::new));
        if (anunciosUnicos.isEmpty()) {
            return Map.of();
        }

        List<UUID> anuncioIds = List.copyOf(anunciosUnicos.keySet());
        List<AnuncioMidiaEntity> vinculos = anuncioMidiaRepository.findByAnuncioIdIn(anuncioIds);
        Map<UUID, List<AnuncioMidiaEntity>> vinculosPorAnuncio = vinculos.stream()
                .collect(Collectors.groupingBy(AnuncioMidiaEntity::getAnuncioId));
        List<UUID> arquivoIds = vinculos.stream()
                .map(AnuncioMidiaEntity::getArquivoMidiaId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, ArquivoMidiaEntity> arquivos = arquivoIds.isEmpty()
                ? Map.of()
                : arquivoMidiaRepository.findByIdIn(arquivoIds).stream()
                        .collect(Collectors.toMap(ArquivoMidiaEntity::getId, Function.identity()));
        Map<UUID, PremiumPublicoFlagsDto> premiumPorAnuncio = premiumMapper.flagsPorAnuncios(
                anunciosUnicos.values());

        Map<UUID, Resultado> resultados = new LinkedHashMap<>();
        for (AnuncioEntity anuncio : anunciosUnicos.values()) {
            List<AnuncioMidiaEntity> vinculosDoAnuncio = vinculosPorAnuncio.getOrDefault(
                    anuncio.getId(), List.of());
            PremiumPublicoFlagsDto premium = premiumPorAnuncio.getOrDefault(
                    anuncio.getId(), PremiumPublicoFlagsDto.vazio());
            int maxFotos = premium.fotosExtrasAtivo() ? FOTOS_COM_EXTRA : FOTOS_BASE;
            List<MidiaPublicaDto> midias = midiaMapper.publicas(
                    vinculosDoAnuncio,
                    arquivos,
                    false,
                    maxFotos,
                    premium.videoAtivo());
            boolean indexavel = indexabilidadePolicy.indexavel(
                    anuncio,
                    localizacoes == null ? null : localizacoes.get(anuncio.getId()),
                    midias);
            OffsetDateTime ultimaAtualizacaoMidia = vinculosDoAnuncio.stream()
                    .map(AnuncioMidiaEntity::getAtualizadoEm)
                    .filter(Objects::nonNull)
                    .max(OffsetDateTime::compareTo)
                    .orElse(null);
            resultados.put(anuncio.getId(), new Resultado(indexavel, ultimaAtualizacaoMidia));
        }
        return Map.copyOf(resultados);
    }

    public record Resultado(boolean indexavel, OffsetDateTime ultimaAtualizacaoMidia) {
    }
}
