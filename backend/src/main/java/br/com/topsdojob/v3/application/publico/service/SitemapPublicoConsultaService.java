package br.com.topsdojob.v3.application.publico.service;

import br.com.topsdojob.v3.application.publico.dto.LocalizacaoPublicaDto;
import br.com.topsdojob.v3.application.publico.dto.SitemapAnuncioPublicoDto;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioLocalizacaoEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.BairroEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.CidadeEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.EstadoEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioLocalizacaoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.BairroRepository;
import br.com.topsdojob.v3.persistence.repository.CidadeRepository;
import br.com.topsdojob.v3.persistence.repository.EstadoRepository;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Comparator;
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
public class SitemapPublicoConsultaService {

    private final AnuncioRepository anuncioRepository;
    private final AnuncioLocalizacaoRepository localizacaoRepository;
    private final EstadoRepository estadoRepository;
    private final CidadeRepository cidadeRepository;
    private final BairroRepository bairroRepository;
    private final AnuncioSeoElegibilidadeConsultaService elegibilidadeService;

    public SitemapPublicoConsultaService(
            AnuncioRepository anuncioRepository,
            AnuncioLocalizacaoRepository localizacaoRepository,
            EstadoRepository estadoRepository,
            CidadeRepository cidadeRepository,
            BairroRepository bairroRepository,
            AnuncioSeoElegibilidadeConsultaService elegibilidadeService) {
        this.anuncioRepository = anuncioRepository;
        this.localizacaoRepository = localizacaoRepository;
        this.estadoRepository = estadoRepository;
        this.cidadeRepository = cidadeRepository;
        this.bairroRepository = bairroRepository;
        this.elegibilidadeService = elegibilidadeService;
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
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

        Map<UUID, LocalizacaoPublicaDto> localizacoesPublicas = new LinkedHashMap<>();
        for (AnuncioEntity anuncio : anuncios) {
            localizacoesPublicas.put(
                    anuncio.getId(),
                    localizacao(localizacoes.get(anuncio.getId()), estados, cidades, bairros));
        }
        Map<UUID, AnuncioSeoElegibilidadeConsultaService.Resultado> elegibilidade = elegibilidadeService.avaliarLocalidades(
                anuncios,
                localizacoesPublicas);

        return anuncios.stream()
                .map(anuncio -> entrada(
                        anuncio,
                        localizacoes,
                        estados,
                        cidades,
                        bairros,
                        elegibilidade))
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
            Map<UUID, AnuncioSeoElegibilidadeConsultaService.Resultado> elegibilidade) {
        AnuncioLocalizacaoEntity localizacaoEntity = localizacoes.get(anuncio.getId());
        LocalizacaoPublicaDto localizacao = localizacao(localizacaoEntity, estados, cidades, bairros);
        if (localizacao == null) {
            return null;
        }

        AnuncioSeoElegibilidadeConsultaService.Resultado resultado = elegibilidade.get(anuncio.getId());
        if (resultado == null || !resultado.indexavel()) {
            return null;
        }

        return new SitemapAnuncioPublicoDto(
                anuncio.getSlug(),
                localizacao.uf(),
                localizacao.cidadeSlug(),
                localizacao.bairroSlug(),
                ultimaAtualizacao(anuncio, localizacaoEntity, resultado.ultimaAtualizacaoMidia()),
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
            OffsetDateTime ultimaAtualizacaoMidia) {
        return java.util.stream.Stream.of(
                        anuncio.getAtualizadoEm(),
                        anuncio.getUltimaPublicacaoEm(),
                        anuncio.getPublicadoEm(),
                        localizacao.getAtualizadoEm(),
                        ultimaAtualizacaoMidia)
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
