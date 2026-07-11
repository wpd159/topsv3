package br.com.topsdojob.v3.application.publico.service;

import br.com.topsdojob.v3.application.publico.dto.AgregadoCidadePublicaDto;
import br.com.topsdojob.v3.application.publico.dto.BairroLocalidadePublicaDto;
import br.com.topsdojob.v3.application.publico.dto.CategoriaCidadePublicaDto;
import br.com.topsdojob.v3.application.publico.dto.CidadeLocalidadePublicaDto;
import br.com.topsdojob.v3.application.publico.dto.DescobertaLocalidadesPublicaDto;
import br.com.topsdojob.v3.application.publico.dto.EstadoLocalidadePublicaDto;
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
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LocalidadePublicaConsultaService {

    private final EstadoRepository estadoRepository;
    private final CidadeRepository cidadeRepository;
    private final BairroRepository bairroRepository;
    private final AnuncioRepository anuncioRepository;
    private final AnuncioLocalizacaoRepository localizacaoRepository;

    public LocalidadePublicaConsultaService(
            EstadoRepository estadoRepository,
            CidadeRepository cidadeRepository,
            BairroRepository bairroRepository,
            AnuncioRepository anuncioRepository,
            AnuncioLocalizacaoRepository localizacaoRepository) {
        this.estadoRepository = estadoRepository;
        this.cidadeRepository = cidadeRepository;
        this.bairroRepository = bairroRepository;
        this.anuncioRepository = anuncioRepository;
        this.localizacaoRepository = localizacaoRepository;
    }

    @Transactional(readOnly = true)
    public DescobertaLocalidadesPublicaDto descobrir() {
        List<AnuncioEntity> anuncios = anunciosPublicos();
        if (anuncios.isEmpty()) {
            return new DescobertaLocalidadesPublicaDto(List.of());
        }

        Map<UUID, AnuncioEntity> anuncioPorId = anuncios.stream()
                .collect(Collectors.toMap(AnuncioEntity::getId, Function.identity()));
        List<AnuncioLocalizacaoEntity> localizacoes = localizacaoRepository.findByAnuncioIdIn(anuncioPorId.keySet());
        Map<UUID, EstadoEntity> estados = porId(
                estadoRepository.findAllById(ids(localizacoes, AnuncioLocalizacaoEntity::getEstadoId)),
                EstadoEntity::getId);
        Map<UUID, CidadeEntity> cidades = porId(
                cidadeRepository.findAllById(ids(localizacoes, AnuncioLocalizacaoEntity::getCidadeId)),
                CidadeEntity::getId);
        Map<UUID, BairroEntity> bairros = porId(
                bairroRepository.findAllById(ids(localizacoes, AnuncioLocalizacaoEntity::getBairroId)),
                BairroEntity::getId);

        Map<UUID, EstadoAcc> acumulado = new LinkedHashMap<>();
        for (AnuncioLocalizacaoEntity localizacao : localizacoes) {
            AnuncioEntity anuncio = anuncioPorId.get(localizacao.getAnuncioId());
            EstadoEntity estado = estados.get(localizacao.getEstadoId());
            CidadeEntity cidade = cidades.get(localizacao.getCidadeId());
            if (anuncio == null || estado == null || cidade == null || !estado.getId().equals(cidade.getEstadoId())) {
                continue;
            }
            BairroEntity bairro = bairros.get(localizacao.getBairroId());
            OffsetDateTime atualizacao = ultimaAtualizacao(anuncio, localizacao);
            EstadoAcc estadoAcc = acumulado.computeIfAbsent(estado.getId(), ignored -> new EstadoAcc(estado));
            estadoAcc.adicionar(anuncio.getId(), atualizacao);
            CidadeAcc cidadeAcc = estadoAcc.cidades.computeIfAbsent(cidade.getId(), ignored -> new CidadeAcc(cidade));
            cidadeAcc.adicionar(anuncio.getId(), atualizacao);
            if (bairro != null && cidade.getId().equals(bairro.getCidadeId())) {
                BairroAcc bairroAcc = cidadeAcc.bairros.computeIfAbsent(bairro.getId(), ignored -> new BairroAcc(bairro));
                bairroAcc.adicionar(anuncio.getId(), atualizacao);
            }
        }

        List<EstadoLocalidadePublicaDto> resultado = acumulado.values().stream()
                .map(EstadoAcc::toDto)
                .sorted(Comparator.comparing(EstadoLocalidadePublicaDto::uf))
                .toList();
        return new DescobertaLocalidadesPublicaDto(resultado);
    }

    @Transactional(readOnly = true)
    public AgregadoCidadePublicaDto agregadoCidade(String uf, String cidadeSlug) {
        String ufSeguro = RotaPublicaGuard.uf(uf);
        String cidadeSegura = RotaPublicaGuard.slug(cidadeSlug, "cidade");
        EstadoEntity estado = estadoRepository.findByUfIgnoreCase(ufSeguro)
                .orElseThrow(() -> notFound("estado nao encontrado"));
        CidadeEntity cidade = cidadeRepository.findByEstadoIdAndSlug(estado.getId(), cidadeSegura)
                .orElseThrow(() -> notFound("cidade nao encontrada"));

        List<AnuncioLocalizacaoEntity> localizacoes = localizacaoRepository.findByCidadeId(cidade.getId());
        List<UUID> ids = localizacoes.stream().map(AnuncioLocalizacaoEntity::getAnuncioId).distinct().toList();
        List<AnuncioEntity> anuncios = ids.isEmpty()
                ? List.of()
                : anuncioRepository.findByIdInAndStatusAndStatusModeracaoAndRemovidoEmIsNull(
                        ids,
                        StatusAnuncio.PUBLICADO,
                        StatusModeracaoAnuncio.APROVADO);
        if (anuncios.isEmpty()) {
            throw notFound("cidade sem anuncios publicos");
        }

        Map<UUID, AnuncioEntity> anuncioPorId = anuncios.stream()
                .collect(Collectors.toMap(AnuncioEntity::getId, Function.identity()));
        List<AnuncioLocalizacaoEntity> localizacoesPublicas = localizacoes.stream()
                .filter(item -> anuncioPorId.containsKey(item.getAnuncioId()))
                .toList();
        Map<UUID, BairroEntity> bairrosPorId = porId(
                bairroRepository.findAllById(ids(localizacoesPublicas, AnuncioLocalizacaoEntity::getBairroId)),
                BairroEntity::getId);

        Map<UUID, BairroAcc> bairros = new HashMap<>();
        for (AnuncioLocalizacaoEntity localizacao : localizacoesPublicas) {
            BairroEntity bairro = bairrosPorId.get(localizacao.getBairroId());
            AnuncioEntity anuncio = anuncioPorId.get(localizacao.getAnuncioId());
            if (bairro == null || anuncio == null || !cidade.getId().equals(bairro.getCidadeId())) {
                continue;
            }
            bairros.computeIfAbsent(bairro.getId(), ignored -> new BairroAcc(bairro))
                    .adicionar(anuncio.getId(), ultimaAtualizacao(anuncio, localizacao));
        }

        Map<String, Long> categorias = anuncios.stream()
                .map(AnuncioEntity::getCategoria)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        List<CategoriaCidadePublicaDto> categoriasDto = categorias.entrySet().stream()
                .map(entry -> new CategoriaCidadePublicaDto(
                        entry.getKey(),
                        nomeCategoria(entry.getKey()),
                        entry.getValue()))
                .sorted(Comparator.comparing(CategoriaCidadePublicaDto::totalAnunciosAtivos).reversed()
                        .thenComparing(CategoriaCidadePublicaDto::codigo))
                .toList();

        List<CidadeLocalidadePublicaDto> relacionadas = descobrir().estados().stream()
                .filter(item -> item.uf().equalsIgnoreCase(estado.getUf()))
                .flatMap(item -> item.cidades().stream())
                .filter(item -> !item.slug().equals(cidade.getSlug()))
                .toList();

        OffsetDateTime ultimaAtualizacao = localizacoesPublicas.stream()
                .map(localizacao -> ultimaAtualizacao(anuncioPorId.get(localizacao.getAnuncioId()), localizacao))
                .filter(java.util.Objects::nonNull)
                .max(OffsetDateTime::compareTo)
                .orElse(null);

        return new AgregadoCidadePublicaDto(
                estado.getUf(),
                estado.getNome(),
                cidade.getNome(),
                cidade.getSlug(),
                anuncios.size(),
                ultimaAtualizacao,
                bairros.values().stream()
                        .map(BairroAcc::toDto)
                        .sorted(Comparator.comparing(BairroLocalidadePublicaDto::nome))
                        .toList(),
                categoriasDto,
                relacionadas);
    }

    private List<AnuncioEntity> anunciosPublicos() {
        return anuncioRepository.findByStatusAndStatusModeracaoAndRemovidoEmIsNull(
                StatusAnuncio.PUBLICADO,
                StatusModeracaoAnuncio.APROVADO);
    }

    private OffsetDateTime ultimaAtualizacao(AnuncioEntity anuncio, AnuncioLocalizacaoEntity localizacao) {
        if (anuncio == null) {
            return null;
        }
        OffsetDateTime anuncioAtualizado = anuncio.getAtualizadoEm() == null
                ? anuncio.getPublicadoEm()
                : anuncio.getAtualizadoEm();
        OffsetDateTime localAtualizado = localizacao == null ? null : localizacao.getAtualizadoEm();
        if (anuncioAtualizado == null) {
            return localAtualizado;
        }
        if (localAtualizado == null) {
            return anuncioAtualizado;
        }
        return anuncioAtualizado.isAfter(localAtualizado) ? anuncioAtualizado : localAtualizado;
    }

    private String nomeCategoria(String codigo) {
        String[] partes = codigo.toLowerCase(Locale.ROOT).split("[_-]+");
        List<String> palavras = new ArrayList<>();
        for (String parte : partes) {
            if (!parte.isBlank()) {
                palavras.add(Character.toUpperCase(parte.charAt(0)) + parte.substring(1));
            }
        }
        return String.join(" ", palavras);
    }

    private <T> Map<UUID, T> porId(Collection<T> values, Function<T, UUID> id) {
        return values.stream().collect(Collectors.toMap(id, Function.identity()));
    }

    private <T> List<UUID> ids(
            List<AnuncioLocalizacaoEntity> localizacoes,
            Function<AnuncioLocalizacaoEntity, UUID> extractor) {
        return localizacoes.stream()
                .map(extractor)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private abstract static class LocalidadeAcc {
        private final Set<UUID> anuncios = new java.util.HashSet<>();
        private OffsetDateTime ultimaAtualizacao;

        void adicionar(UUID anuncioId, OffsetDateTime atualizacao) {
            anuncios.add(anuncioId);
            if (atualizacao != null && (ultimaAtualizacao == null || atualizacao.isAfter(ultimaAtualizacao))) {
                ultimaAtualizacao = atualizacao;
            }
        }

        long total() {
            return anuncios.size();
        }

        OffsetDateTime ultimaAtualizacao() {
            return ultimaAtualizacao;
        }
    }

    private static final class EstadoAcc extends LocalidadeAcc {
        private final EstadoEntity estado;
        private final Map<UUID, CidadeAcc> cidades = new LinkedHashMap<>();

        private EstadoAcc(EstadoEntity estado) {
            this.estado = estado;
        }

        private EstadoLocalidadePublicaDto toDto() {
            return new EstadoLocalidadePublicaDto(
                    estado.getUf(),
                    estado.getNome(),
                    total(),
                    ultimaAtualizacao(),
                    cidades.values().stream()
                            .map(CidadeAcc::toDto)
                            .sorted(Comparator.comparing(CidadeLocalidadePublicaDto::nome))
                            .toList());
        }
    }

    private static final class CidadeAcc extends LocalidadeAcc {
        private final CidadeEntity cidade;
        private final Map<UUID, BairroAcc> bairros = new LinkedHashMap<>();

        private CidadeAcc(CidadeEntity cidade) {
            this.cidade = cidade;
        }

        private CidadeLocalidadePublicaDto toDto() {
            return new CidadeLocalidadePublicaDto(
                    cidade.getNome(),
                    cidade.getSlug(),
                    total(),
                    ultimaAtualizacao(),
                    bairros.values().stream()
                            .map(BairroAcc::toDto)
                            .sorted(Comparator.comparing(BairroLocalidadePublicaDto::nome))
                            .toList());
        }
    }

    private static final class BairroAcc extends LocalidadeAcc {
        private final BairroEntity bairro;

        private BairroAcc(BairroEntity bairro) {
            this.bairro = bairro;
        }

        private BairroLocalidadePublicaDto toDto() {
            return new BairroLocalidadePublicaDto(
                    bairro.getNome(),
                    bairro.getSlug(),
                    total(),
                    ultimaAtualizacao());
        }
    }
}
