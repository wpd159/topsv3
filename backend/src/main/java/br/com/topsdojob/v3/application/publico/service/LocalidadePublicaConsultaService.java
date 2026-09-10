package br.com.topsdojob.v3.application.publico.service;

import br.com.topsdojob.v3.application.publico.dto.AgregadoCidadePublicaDto;
import br.com.topsdojob.v3.application.publico.dto.BairroLocalidadePublicaDto;
import br.com.topsdojob.v3.application.publico.dto.CategoriaCidadePublicaDto;
import br.com.topsdojob.v3.application.publico.dto.CidadeLocalidadePublicaDto;
import br.com.topsdojob.v3.application.publico.dto.DescobertaLocalidadesPublicaDto;
import br.com.topsdojob.v3.application.publico.dto.EstadoLocalidadePublicaDto;
import br.com.topsdojob.v3.application.publico.dto.LocalizacaoPublicaDto;
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
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ServicoAnuncio;
import java.text.Collator;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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
    private final AnuncioSeoElegibilidadeConsultaService elegibilidadeService;
    private final LocalidadeSeoIndexabilidadePolicy indexabilidadePolicy;
    private final LocalidadesConsultaCoordenador coordenador;

    public LocalidadePublicaConsultaService(
            EstadoRepository estadoRepository,
            CidadeRepository cidadeRepository,
            BairroRepository bairroRepository,
            AnuncioRepository anuncioRepository,
            AnuncioLocalizacaoRepository localizacaoRepository,
            AnuncioSeoElegibilidadeConsultaService elegibilidadeService,
            LocalidadeSeoIndexabilidadePolicy indexabilidadePolicy,
            LocalidadesConsultaCoordenador coordenador) {
        this.estadoRepository = estadoRepository;
        this.cidadeRepository = cidadeRepository;
        this.bairroRepository = bairroRepository;
        this.anuncioRepository = anuncioRepository;
        this.localizacaoRepository = localizacaoRepository;
        this.elegibilidadeService = elegibilidadeService;
        this.indexabilidadePolicy = indexabilidadePolicy;
        this.coordenador = coordenador;
    }

    public DescobertaLocalidadesPublicaDto descobrir() {
        return consultar().descoberta();
    }

    private ResultadoConsulta consultar() {
        // Global and city endpoints consume exactly the same public, unfiltered snapshot.
        // No completed result is retained; all public/moderation/removal rules are reread.
        return coordenador.executar("descoberta-publica-global", () ->
                coordenador.transacao("avaliacao_e_agregacao", this::carregar));
    }

    private ResultadoConsulta carregar() {
        List<AnuncioEntity> anuncios = coordenador.medir("anuncios", this::anunciosPublicos);
        if (anuncios.isEmpty()) {
            return new ResultadoConsulta(new DescobertaLocalidadesPublicaDto(List.of()), Map.of());
        }

        Map<UUID, AnuncioEntity> anuncioPorId = anuncios.stream()
                .collect(Collectors.toMap(AnuncioEntity::getId, Function.identity()));
        List<AnuncioLocalizacaoEntity> localizacoes = coordenador.medir("localizacoes",
                () -> localizacaoRepository.findByAnuncioIdIn(anuncioPorId.keySet()));
        Map<UUID, EstadoEntity> estados = porId(
                coordenador.medir("estados", () -> estadoRepository.findAllById(ids(localizacoes, AnuncioLocalizacaoEntity::getEstadoId))),
                EstadoEntity::getId);
        Map<UUID, CidadeEntity> cidades = porId(
                coordenador.medir("cidades", () -> cidadeRepository.findAllById(ids(localizacoes, AnuncioLocalizacaoEntity::getCidadeId))),
                CidadeEntity::getId);
        Map<UUID, BairroEntity> bairros = porId(
                coordenador.medir("bairros", () -> bairroRepository.findAllById(ids(localizacoes, AnuncioLocalizacaoEntity::getBairroId))),
                BairroEntity::getId);
        Map<UUID, LocalizacaoPublicaDto> localizacoesPublicas = new LinkedHashMap<>();
        for (AnuncioLocalizacaoEntity localizacao : localizacoes) {
            LocalidadesConsultaOrcamento.atualOuNulo().conferir();
            localizacoesPublicas.putIfAbsent(
                    localizacao.getAnuncioId(),
                    localizacao(localizacao, estados, cidades, bairros));
        }
        Map<UUID, AnuncioSeoElegibilidadeConsultaService.Resultado> elegibilidade = coordenador.medir("elegibilidade", () -> elegibilidadeService.avaliarLocalidades(
                anuncios,
                localizacoesPublicas));

        Map<UUID, EstadoAcc> acumulado = new LinkedHashMap<>();
        for (AnuncioLocalizacaoEntity localizacao : localizacoes) {
            LocalidadesConsultaOrcamento.atualOuNulo().conferir();
            AnuncioEntity anuncio = anuncioPorId.get(localizacao.getAnuncioId());
            EstadoEntity estado = estados.get(localizacao.getEstadoId());
            CidadeEntity cidade = cidades.get(localizacao.getCidadeId());
            if (anuncio == null || estado == null || cidade == null || !estado.getId().equals(cidade.getEstadoId())) {
                continue;
            }
            boolean indexavel = elegibilidade.containsKey(anuncio.getId())
                    && elegibilidade.get(anuncio.getId()).indexavel();
            BairroEntity bairro = bairros.get(localizacao.getBairroId());
            OffsetDateTime atualizacao = ultimaAtualizacao(anuncio, localizacao);
            OffsetDateTime midiaAtualizada = elegibilidade.containsKey(anuncio.getId())
                    ? elegibilidade.get(anuncio.getId()).ultimaAtualizacaoMidia() : null;
            if (midiaAtualizada != null && (atualizacao == null || midiaAtualizada.isAfter(atualizacao))) {
                atualizacao = midiaAtualizada;
            }
            EstadoAcc estadoAcc = acumulado.computeIfAbsent(estado.getId(), ignored -> new EstadoAcc(estado));
            estadoAcc.adicionar(anuncio.getId(), indexavel, atualizacao);
            CidadeAcc cidadeAcc = estadoAcc.cidades.computeIfAbsent(cidade.getId(), ignored -> new CidadeAcc(cidade));
            cidadeAcc.adicionar(anuncio.getId(), indexavel, atualizacao);
            if (bairro != null && cidade.getId().equals(bairro.getCidadeId())) {
                BairroAcc bairroAcc = cidadeAcc.bairros.computeIfAbsent(bairro.getId(), ignored -> new BairroAcc(bairro));
                bairroAcc.adicionar(anuncio.getId(), indexavel, atualizacao);
            }
        }

        List<EstadoLocalidadePublicaDto> resultado = acumulado.values().stream()
                .map(item -> item.toDto(indexabilidadePolicy))
                .sorted(Comparator.comparing(EstadoLocalidadePublicaDto::uf))
                .toList();
        DescobertaLocalidadesPublicaDto descoberta = new DescobertaLocalidadesPublicaDto(resultado);
        Map<String, AgregadoCidadePublicaDto> agregados = coordenador.medir("agregados_cidades",
                () -> agregados(descoberta, anuncios, localizacoes, cidades, estados));
        return new ResultadoConsulta(descoberta, agregados);
    }

    @Transactional(readOnly = true)
    public DescobertaLocalidadesPublicaDto catalogoCompleto() {
        Comparator<String> nomes = comparadorNomes();
        Map<UUID, List<BairroEntity>> bairrosPorCidade = bairroRepository.findAll().stream()
                .filter(item -> item.getCidadeId() != null)
                .collect(Collectors.groupingBy(BairroEntity::getCidadeId));
        Map<UUID, List<CidadeEntity>> cidadesPorEstado = cidadeRepository.findAll().stream()
                .filter(item -> item.getEstadoId() != null)
                .collect(Collectors.groupingBy(CidadeEntity::getEstadoId));

        List<EstadoLocalidadePublicaDto> estados = estadoRepository.findAll().stream()
                .map(estado -> new EstadoLocalidadePublicaDto(
                        estado.getUf(),
                        estado.getNome(),
                        0,
                        null,
                        indexabilidadePolicy.estado(0, List.of()),
                        cidadesPorEstado.getOrDefault(estado.getId(), List.of()).stream()
                                .map(cidade -> new CidadeLocalidadePublicaDto(
                                        cidade.getNome(),
                                        cidade.getSlug(),
                                        0,
                                        null,
                                        indexabilidadePolicy.cidade(0),
                                        bairrosPorCidade.getOrDefault(cidade.getId(), List.of()).stream()
                                                .map(bairro -> new BairroLocalidadePublicaDto(
                                                        bairro.getNome(),
                                                        bairro.getSlug(),
                                                        0,
                                                        null,
                                                        indexabilidadePolicy.bairro(0)))
                                                .sorted(Comparator.comparing(
                                                        BairroLocalidadePublicaDto::nome,
                                                        nomes))
                                                .toList()))
                                .sorted(Comparator.comparing(CidadeLocalidadePublicaDto::nome, nomes))
                                .toList()))
                .sorted(Comparator.comparing(EstadoLocalidadePublicaDto::nome, nomes))
                .toList();
        return new DescobertaLocalidadesPublicaDto(estados);
    }

    public AgregadoCidadePublicaDto agregadoCidade(String uf, String cidadeSlug) {
        String ufSeguro = RotaPublicaGuard.uf(uf);
        String cidadeSegura = RotaPublicaGuard.slug(cidadeSlug, "cidade");
        AgregadoCidadePublicaDto resultado = consultar().cidades().get(chaveCidade(ufSeguro, cidadeSegura));
        if (resultado == null) throw notFound("cidade sem anuncios publicos");
        return resultado;
    }

    private Map<String, AgregadoCidadePublicaDto> agregados(DescobertaLocalidadesPublicaDto descoberta,
            List<AnuncioEntity> anuncios, List<AnuncioLocalizacaoEntity> localizacoes,
            Map<UUID, CidadeEntity> cidades, Map<UUID, EstadoEntity> estados) {
        Map<UUID, AnuncioEntity> anuncioPorId = anuncios.stream()
                .collect(Collectors.toMap(AnuncioEntity::getId, Function.identity()));
        Map<UUID, List<AnuncioLocalizacaoEntity>> porCidade = localizacoes.stream()
                .filter(item -> anuncioPorId.containsKey(item.getAnuncioId()))
                .filter(item -> item.getCidadeId() != null)
                .collect(Collectors.groupingBy(AnuncioLocalizacaoEntity::getCidadeId));
        Map<String, AgregadoCidadePublicaDto> resultado = new LinkedHashMap<>();
        Map<String, UUID> identidades = cidades.values().stream()
                .filter(cidade -> estados.containsKey(cidade.getEstadoId()))
                .collect(Collectors.toMap(cidade -> chaveCidade(estados.get(cidade.getEstadoId()).getUf(),
                        cidade.getSlug()), CidadeEntity::getId));
        for (EstadoLocalidadePublicaDto estado : descoberta.estados()) {
            for (CidadeLocalidadePublicaDto cidade : estado.cidades()) {
                LocalidadesConsultaOrcamento.atualOuNulo().conferir();
                UUID cidadeId = identidades.get(chaveCidade(estado.uf(), cidade.slug()));
                List<AnuncioLocalizacaoEntity> locais = porCidade.getOrDefault(cidadeId, List.of());
                List<AnuncioEntity> daCidade = locais.stream().map(AnuncioLocalizacaoEntity::getAnuncioId)
                        .distinct().map(anuncioPorId::get).toList();
                resultado.put(chaveCidade(estado.uf(), cidade.slug()),
                        agregado(estado, cidade, daCidade, locais, anuncioPorId));
            }
        }
        return Map.copyOf(resultado);
    }

    private AgregadoCidadePublicaDto agregado(EstadoLocalidadePublicaDto estadoDescoberto,
            CidadeLocalidadePublicaDto cidadeDescoberta, List<AnuncioEntity> anuncios,
            List<AnuncioLocalizacaoEntity> localizacoesPublicas, Map<UUID, AnuncioEntity> anuncioPorId) {

        Map<String, Long> categorias = anuncios.stream()
                .flatMap(anuncio -> {
                    LocalidadesConsultaOrcamento.atualOuNulo().conferir();
                    Set<String> codigos = new java.util.LinkedHashSet<>();
                    if (anuncio.getCategoria() != null && !anuncio.getCategoria().isBlank()) {
                        codigos.add(anuncio.getCategoria());
                    }
                    if (anuncio.getServicos().contains(ServicoAnuncio.VIDEOCHAMADA)) {
                        codigos.add("VENDA_DE_CONTEUDO");
                    }
                    return codigos.stream();
                })
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        List<CategoriaCidadePublicaDto> categoriasDto = categorias.entrySet().stream()
                .map(entry -> new CategoriaCidadePublicaDto(
                        entry.getKey(),
                        nomeCategoria(entry.getKey()),
                        entry.getValue()))
                .sorted(Comparator.comparing(CategoriaCidadePublicaDto::totalAnunciosAtivos).reversed()
                        .thenComparing(CategoriaCidadePublicaDto::codigo))
                .toList();

        List<CidadeLocalidadePublicaDto> relacionadas = estadoDescoberto.cidades().stream()
                .filter(item -> !item.slug().equals(cidadeDescoberta.slug()))
                .toList();

        return new AgregadoCidadePublicaDto(
                estadoDescoberto.uf(),
                estadoDescoberto.nome(),
                cidadeDescoberta.nome(),
                cidadeDescoberta.slug(),
                anuncios.size(),
                cidadeDescoberta.ultimaAtualizacao(),
                cidadeDescoberta.indexacao(),
                cidadeDescoberta.bairros(),
                categoriasDto,
                relacionadas);
    }

    private String chaveCidade(String uf, String slug) {
        return uf.toLowerCase(Locale.ROOT) + "/" + slug;
    }

    private record ResultadoConsulta(DescobertaLocalidadesPublicaDto descoberta,
            Map<String, AgregadoCidadePublicaDto> cidades) { }

    private List<AnuncioEntity> anunciosPublicos() {
        return anuncioRepository.findPublicosComProprietarioAtivo().stream()
                .filter(anuncio -> !anuncio.isAtendimentoExclusivamenteVirtual())
                .toList();
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

    private Comparator<String> comparadorNomes() {
        Collator collator = Collator.getInstance(Locale.forLanguageTag("pt-BR"));
        collator.setStrength(Collator.PRIMARY);
        return collator::compare;
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
        private final Set<UUID> anunciosElegiveis = new java.util.HashSet<>();
        private OffsetDateTime ultimaAtualizacao;

        void adicionar(UUID anuncioId, boolean elegivel, OffsetDateTime atualizacao) {
            anuncios.add(anuncioId);
            if (elegivel) {
                anunciosElegiveis.add(anuncioId);
            }
            if (atualizacao != null && (ultimaAtualizacao == null || atualizacao.isAfter(ultimaAtualizacao))) {
                ultimaAtualizacao = atualizacao;
            }
        }

        long total() {
            return anuncios.size();
        }

        long elegiveis() {
            return anunciosElegiveis.size();
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

        private EstadoLocalidadePublicaDto toDto(LocalidadeSeoIndexabilidadePolicy policy) {
            List<CidadeLocalidadePublicaDto> cidadesDto = cidades.values().stream()
                    .map(item -> item.toDto(policy))
                    .sorted(Comparator.comparing(CidadeLocalidadePublicaDto::nome))
                    .toList();
            return new EstadoLocalidadePublicaDto(
                    estado.getUf(),
                    estado.getNome(),
                    total(),
                    ultimaAtualizacao(),
                    policy.estado(elegiveis(), cidadesDto),
                    cidadesDto);
        }
    }

    private static final class CidadeAcc extends LocalidadeAcc {
        private final CidadeEntity cidade;
        private final Map<UUID, BairroAcc> bairros = new LinkedHashMap<>();

        private CidadeAcc(CidadeEntity cidade) {
            this.cidade = cidade;
        }

        private CidadeLocalidadePublicaDto toDto(LocalidadeSeoIndexabilidadePolicy policy) {
            return new CidadeLocalidadePublicaDto(
                    cidade.getNome(),
                    cidade.getSlug(),
                    total(),
                    ultimaAtualizacao(),
                    policy.cidade(elegiveis()),
                    bairros.values().stream()
                            .map(item -> item.toDto(policy))
                            .sorted(Comparator.comparing(BairroLocalidadePublicaDto::nome))
                            .toList());
        }
    }

    private static final class BairroAcc extends LocalidadeAcc {
        private final BairroEntity bairro;

        private BairroAcc(BairroEntity bairro) {
            this.bairro = bairro;
        }

        private BairroLocalidadePublicaDto toDto(LocalidadeSeoIndexabilidadePolicy policy) {
            return new BairroLocalidadePublicaDto(
                    bairro.getNome(),
                    bairro.getSlug(),
                    total(),
                    ultimaAtualizacao(),
                    policy.bairro(elegiveis()));
        }
    }
}
