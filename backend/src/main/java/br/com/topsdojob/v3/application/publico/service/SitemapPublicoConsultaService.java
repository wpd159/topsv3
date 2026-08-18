package br.com.topsdojob.v3.application.publico.service;

import br.com.topsdojob.v3.application.publico.dto.LocalizacaoPublicaDto;
import br.com.topsdojob.v3.application.publico.dto.MidiaPublicaDto;
import br.com.topsdojob.v3.application.publico.dto.SitemapAnuncioPublicoDto;
import br.com.topsdojob.v3.application.publico.mapper.MidiaPublicaMapper;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioLocalizacaoEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.BairroEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.CidadeEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.EstadoEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioLocalizacaoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.BairroRepository;
import br.com.topsdojob.v3.persistence.repository.CidadeRepository;
import br.com.topsdojob.v3.persistence.repository.EstadoRepository;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SitemapPublicoConsultaService {

    private final AnuncioRepository anuncioRepository;
    private final AnuncioLocalizacaoRepository localizacaoRepository;
    private final AnuncioMidiaRepository anuncioMidiaRepository;
    private final ArquivoMidiaRepository arquivoMidiaRepository;
    private final EstadoRepository estadoRepository;
    private final CidadeRepository cidadeRepository;
    private final BairroRepository bairroRepository;
    private final MidiaPublicaMapper midiaMapper;
    private final AnuncioSeoIndexabilidadePolicy indexabilidadePolicy;

    public SitemapPublicoConsultaService(
            AnuncioRepository anuncioRepository,
            AnuncioLocalizacaoRepository localizacaoRepository,
            AnuncioMidiaRepository anuncioMidiaRepository,
            ArquivoMidiaRepository arquivoMidiaRepository,
            EstadoRepository estadoRepository,
            CidadeRepository cidadeRepository,
            BairroRepository bairroRepository,
            MidiaPublicaMapper midiaMapper,
            AnuncioSeoIndexabilidadePolicy indexabilidadePolicy) {
        this.anuncioRepository = anuncioRepository;
        this.localizacaoRepository = localizacaoRepository;
        this.anuncioMidiaRepository = anuncioMidiaRepository;
        this.arquivoMidiaRepository = arquivoMidiaRepository;
        this.estadoRepository = estadoRepository;
        this.cidadeRepository = cidadeRepository;
        this.bairroRepository = bairroRepository;
        this.midiaMapper = midiaMapper;
        this.indexabilidadePolicy = indexabilidadePolicy;
    }

    @Transactional(readOnly = true)
    public List<SitemapAnuncioPublicoDto> listarAnunciosIndexaveis() {
        List<AnuncioEntity> anuncios = anuncioRepository.findPublicosComProprietarioAtivo();
        if (anuncios.isEmpty()) {
            return List.of();
        }

        List<UUID> anuncioIds = anuncios.stream().map(AnuncioEntity::getId).toList();
        Map<UUID, AnuncioLocalizacaoEntity> localizacoes = porId(
                localizacaoRepository.findByAnuncioIdIn(anuncioIds),
                AnuncioLocalizacaoEntity::getAnuncioId);
        Map<UUID, EstadoEntity> estados = porId(
                estadoRepository.findAllById(ids(localizacoes.values(), AnuncioLocalizacaoEntity::getEstadoId)),
                EstadoEntity::getId);
        Map<UUID, CidadeEntity> cidades = porId(
                cidadeRepository.findAllById(ids(localizacoes.values(), AnuncioLocalizacaoEntity::getCidadeId)),
                CidadeEntity::getId);
        Map<UUID, BairroEntity> bairros = porId(
                bairroRepository.findAllById(ids(localizacoes.values(), AnuncioLocalizacaoEntity::getBairroId)),
                BairroEntity::getId);

        List<AnuncioMidiaEntity> vinculos = anuncioMidiaRepository.findByAnuncioIdIn(anuncioIds);
        Map<UUID, List<AnuncioMidiaEntity>> vinculosPorAnuncio = vinculos.stream()
                .collect(Collectors.groupingBy(AnuncioMidiaEntity::getAnuncioId));
        Map<UUID, ArquivoMidiaEntity> arquivos = porId(
                arquivoMidiaRepository.findByIdIn(ids(vinculos, AnuncioMidiaEntity::getArquivoMidiaId)),
                ArquivoMidiaEntity::getId);

        return anuncios.stream()
                .map(anuncio -> entrada(anuncio, localizacoes, estados, cidades, bairros, vinculosPorAnuncio, arquivos))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(SitemapAnuncioPublicoDto::slug))
                .toList();
    }

    private SitemapAnuncioPublicoDto entrada(
            AnuncioEntity anuncio,
            Map<UUID, AnuncioLocalizacaoEntity> localizacoes,
            Map<UUID, EstadoEntity> estados,
            Map<UUID, CidadeEntity> cidades,
            Map<UUID, BairroEntity> bairros,
            Map<UUID, List<AnuncioMidiaEntity>> vinculosPorAnuncio,
            Map<UUID, ArquivoMidiaEntity> arquivos) {
        AnuncioLocalizacaoEntity localizacaoEntity = localizacoes.get(anuncio.getId());
        LocalizacaoPublicaDto localizacao = localizacao(localizacaoEntity, estados, cidades, bairros);
        if (localizacao == null) {
            return null;
        }

        List<AnuncioMidiaEntity> vinculos = vinculosPorAnuncio.getOrDefault(anuncio.getId(), List.of());
        List<MidiaPublicaDto> midias = midiaMapper.publicas(vinculos, arquivos, false);
        boolean indexavel = indexabilidadePolicy.indexavel(anuncio, localizacao, midias);
        if (!indexavel) {
            return null;
        }

        return new SitemapAnuncioPublicoDto(
                anuncio.getSlug(),
                localizacao.uf(),
                localizacao.cidadeSlug(),
                localizacao.bairroSlug(),
                ultimaAtualizacao(anuncio, localizacaoEntity, vinculos),
                true,
                true);
    }

    private LocalizacaoPublicaDto localizacao(
            AnuncioLocalizacaoEntity localizacao,
            Map<UUID, EstadoEntity> estados,
            Map<UUID, CidadeEntity> cidades,
            Map<UUID, BairroEntity> bairros) {
        if (localizacao == null) {
            return null;
        }
        EstadoEntity estado = estados.get(localizacao.getEstadoId());
        CidadeEntity cidade = cidades.get(localizacao.getCidadeId());
        if (estado == null || cidade == null || !estado.getId().equals(cidade.getEstadoId())) {
            return null;
        }
        BairroEntity bairro = localizacao.getBairroId() == null ? null : bairros.get(localizacao.getBairroId());
        if (bairro != null && !cidade.getId().equals(bairro.getCidadeId())) {
            return null;
        }
        return new LocalizacaoPublicaDto(
                estado.getUf(),
                estado.getNome(),
                cidade.getNome(),
                cidade.getSlug(),
                bairro == null ? null : bairro.getNome(),
                bairro == null ? null : bairro.getSlug(),
                null);
    }

    private OffsetDateTime ultimaAtualizacao(
            AnuncioEntity anuncio,
            AnuncioLocalizacaoEntity localizacao,
            List<AnuncioMidiaEntity> vinculos) {
        return java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(
                                anuncio.getAtualizadoEm(),
                                anuncio.getUltimaPublicacaoEm(),
                                anuncio.getPublicadoEm(),
                                localizacao.getAtualizadoEm()),
                        vinculos.stream().map(AnuncioMidiaEntity::getAtualizadoEm))
                .filter(Objects::nonNull)
                .max(OffsetDateTime::compareTo)
                .orElse(null);
    }

    private <T> List<UUID> ids(Collection<T> items, Function<T, UUID> idGetter) {
        return items.stream().map(idGetter).filter(Objects::nonNull).distinct().toList();
    }

    private <T> Map<UUID, T> porId(Collection<T> items, Function<T, UUID> idGetter) {
        return items.stream().collect(Collectors.toMap(idGetter, Function.identity()));
    }
}
