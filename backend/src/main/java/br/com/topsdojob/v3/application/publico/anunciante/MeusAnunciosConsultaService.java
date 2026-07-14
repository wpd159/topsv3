package br.com.topsdojob.v3.application.publico.anunciante;

import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioCapaDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioLocalizacaoDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioMidiaDto;
import br.com.topsdojob.v3.application.publico.dto.MidiaPublicaDto;
import br.com.topsdojob.v3.application.publico.mapper.MidiaPublicaMapper;
import br.com.topsdojob.v3.application.publico.mapper.MidiaPublicaSeguraPolicy;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioLocalizacaoEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.BairroEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.CidadeEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.EstadoEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioLocalizacaoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.BairroRepository;
import br.com.topsdojob.v3.persistence.repository.CidadeRepository;
import br.com.topsdojob.v3.persistence.repository.EstadoRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoContaUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.security.publico.PublicUserPrincipal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MeusAnunciosConsultaService {

    private final UsuarioRepository usuarioRepository;
    private final AnuncioRepository anuncioRepository;
    private final AnuncioLocalizacaoRepository localizacaoRepository;
    private final AnuncioMidiaRepository anuncioMidiaRepository;
    private final ArquivoMidiaRepository arquivoMidiaRepository;
    private final EstadoRepository estadoRepository;
    private final CidadeRepository cidadeRepository;
    private final BairroRepository bairroRepository;
    private final MidiaPublicaMapper midiaMapper;
    private final MidiaPublicaSeguraPolicy midiaSeguraPolicy;

    public MeusAnunciosConsultaService(
            UsuarioRepository usuarioRepository,
            AnuncioRepository anuncioRepository,
            AnuncioLocalizacaoRepository localizacaoRepository,
            AnuncioMidiaRepository anuncioMidiaRepository,
            ArquivoMidiaRepository arquivoMidiaRepository,
            EstadoRepository estadoRepository,
            CidadeRepository cidadeRepository,
            BairroRepository bairroRepository,
            MidiaPublicaMapper midiaMapper,
            MidiaPublicaSeguraPolicy midiaSeguraPolicy) {
        this.usuarioRepository = usuarioRepository;
        this.anuncioRepository = anuncioRepository;
        this.localizacaoRepository = localizacaoRepository;
        this.anuncioMidiaRepository = anuncioMidiaRepository;
        this.arquivoMidiaRepository = arquivoMidiaRepository;
        this.estadoRepository = estadoRepository;
        this.cidadeRepository = cidadeRepository;
        this.bairroRepository = bairroRepository;
        this.midiaMapper = midiaMapper;
        this.midiaSeguraPolicy = midiaSeguraPolicy;
    }

    @Transactional(readOnly = true)
    public List<MeuAnuncioDto> listar(Authentication authentication) {
        UUID usuarioId = usuarioAutenticado(authentication).getId();
        List<AnuncioEntity> anuncios = anuncioRepository
                .findByUsuarioIdAndRemovidoEmIsNullOrderByAtualizadoEmDesc(usuarioId);
        return mapear(anuncios);
    }

    @Transactional(readOnly = true)
    public MeuAnuncioDto detalhar(String slug, Authentication authentication) {
        return mapear(List.of(anuncioDoUsuario(slug, authentication))).get(0);
    }

    public AnuncioEntity anuncioDoUsuario(String slug, Authentication authentication) {
        UUID usuarioId = usuarioAutenticado(authentication).getId();
        AnuncioEntity anuncio = anuncioRepository.findBySlugAndRemovidoEmIsNull(slugSeguro(slug))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado"));
        if (!usuarioId.equals(anuncio.getUsuarioId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "anuncio pertence a outro usuario");
        }
        return anuncio;
    }

    public UsuarioEntity usuarioAutenticado(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof PublicUserPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "sessao publica obrigatoria");
        }
        UsuarioEntity usuario = usuarioRepository.findById(principal.usuarioId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "sessao publica invalida"));
        if (usuario.getStatus() != StatusUsuario.ATIVO
                || usuario.getTipoConta() != TipoContaUsuario.ANUNCIANTE
                || usuario.getDesativadoEm() != null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "sessao publica invalida");
        }
        return usuario;
    }

    String slugSeguro(String slug) {
        if (slug == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "slug obrigatorio");
        }
        String normalizado = slug.trim().toLowerCase();
        if (!normalizado.matches("[a-z0-9][a-z0-9-]{1,120}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "slug invalido");
        }
        return normalizado;
    }

    private List<MeuAnuncioDto> mapear(List<AnuncioEntity> anuncios) {
        if (anuncios.isEmpty()) {
            return List.of();
        }

        List<UUID> anuncioIds = anuncios.stream().map(AnuncioEntity::getId).toList();
        Map<UUID, AnuncioLocalizacaoEntity> localizacoes = localizacaoRepository.findByAnuncioIdIn(anuncioIds).stream()
                .collect(Collectors.toMap(AnuncioLocalizacaoEntity::getAnuncioId, Function.identity()));
        Map<UUID, EstadoEntity> estados = entidadesPorId(
                estadoRepository.findAllById(ids(localizacoes.values(), AnuncioLocalizacaoEntity::getEstadoId)),
                EstadoEntity::getId);
        Map<UUID, CidadeEntity> cidades = entidadesPorId(
                cidadeRepository.findAllById(ids(localizacoes.values(), AnuncioLocalizacaoEntity::getCidadeId)),
                CidadeEntity::getId);
        Map<UUID, BairroEntity> bairros = entidadesPorId(
                bairroRepository.findAllById(ids(localizacoes.values(), AnuncioLocalizacaoEntity::getBairroId)),
                BairroEntity::getId);

        List<AnuncioMidiaEntity> vinculos = anuncioMidiaRepository.findByAnuncioIdIn(anuncioIds);
        Map<UUID, List<AnuncioMidiaEntity>> vinculosPorAnuncio = vinculos.stream()
                .collect(Collectors.groupingBy(AnuncioMidiaEntity::getAnuncioId));
        Map<UUID, ArquivoMidiaEntity> arquivos = arquivoMidiaRepository.findByIdIn(vinculos.stream()
                        .map(AnuncioMidiaEntity::getArquivoMidiaId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList()).stream()
                .collect(Collectors.toMap(ArquivoMidiaEntity::getId, Function.identity()));

        return anuncios.stream()
                .map(anuncio -> new MeuAnuncioDto(
                        anuncio.getId(),
                        anuncio.getSlug(),
                        anuncio.getTitulo(),
                        anuncio.getDescricao(),
                        anuncio.getCategoria(),
                        anuncio.getPreco(),
                        anuncio.getWhatsappNormalizado(),
                        enumNames(anuncio.getLocaisAtendimento()),
                        enumNames(anuncio.getServicos()),
                        enumName(anuncio.getStatus()),
                        enumName(anuncio.getStatusModeracao()),
                        localizacao(localizacoes.get(anuncio.getId()), estados, cidades, bairros),
                        capa(vinculosPorAnuncio.getOrDefault(anuncio.getId(), List.of()), arquivos),
                        midias(vinculosPorAnuncio.getOrDefault(anuncio.getId(), List.of()), arquivos),
                        anuncio.getAtualizadoEm()))
                .toList();
    }

    private MeuAnuncioLocalizacaoDto localizacao(
            AnuncioLocalizacaoEntity localizacao,
            Map<UUID, EstadoEntity> estados,
            Map<UUID, CidadeEntity> cidades,
            Map<UUID, BairroEntity> bairros) {
        if (localizacao == null) {
            return null;
        }
        EstadoEntity estado = estados.get(localizacao.getEstadoId());
        CidadeEntity cidade = cidades.get(localizacao.getCidadeId());
        BairroEntity bairro = bairros.get(localizacao.getBairroId());
        return new MeuAnuncioLocalizacaoDto(
                estado == null ? null : estado.getUf(),
                cidade == null ? null : cidade.getNome(),
                cidade == null ? null : cidade.getSlug(),
                bairro == null ? null : bairro.getNome(),
                bairro == null ? null : bairro.getSlug());
    }

    private MeuAnuncioCapaDto capa(
            List<AnuncioMidiaEntity> vinculos,
            Map<UUID, ArquivoMidiaEntity> arquivos) {
        List<MidiaPublicaDto> candidatas = midiaSeguraPolicy.paraCard(midiaMapper.publicas(vinculos, arquivos, false));
        if (candidatas.isEmpty()) {
            return null;
        }
        MidiaPublicaDto capa = candidatas.get(0);
        return new MeuAnuncioCapaDto(capa.urlPublica(), !capa.autorizada());
    }

    private List<MeuAnuncioMidiaDto> midias(
            List<AnuncioMidiaEntity> vinculos,
            Map<UUID, ArquivoMidiaEntity> arquivos) {
        Map<UUID, MidiaPublicaDto> publicasPorId = midiaMapper.publicas(vinculos, arquivos, false).stream()
                .collect(Collectors.toMap(MidiaPublicaDto::id, Function.identity()));
        return vinculos.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getStatus() != StatusAnuncioMidia.REMOVIDA)
                .filter(item -> item.getTipo() != TipoAnuncioMidia.STORY)
                .filter(item -> item.getFinalidade() != FinalidadeAnuncioMidia.STORY)
                .sorted(java.util.Comparator.comparing(
                        AnuncioMidiaEntity::getOrdem,
                        java.util.Comparator.nullsLast(Integer::compareTo)))
                .map(item -> {
                    MidiaPublicaDto publica = publicasPorId.get(item.getId());
                    boolean restrita = item.getVisibilidadeMidia() == br.com.topsdojob.v3.domain.shared.VisibilidadeMidia.RESTRITA_18;
                    return new MeuAnuncioMidiaDto(
                            item.getId(),
                            enumName(item.getTipo()),
                            enumName(item.getFinalidade()),
                            item.getOrdem(),
                            enumName(item.getStatus()),
                            enumName(item.getVisibilidadeMidia()),
                            publica == null || restrita ? null : publica.urlPublica(),
                            restrita);
                })
                .toList();
    }

    private <T> List<UUID> ids(Collection<T> entidades, Function<T, UUID> extractor) {
        return entidades.stream().map(extractor).filter(Objects::nonNull).distinct().toList();
    }

    private <T> Map<UUID, T> entidadesPorId(Iterable<T> entidades, Function<T, UUID> extractor) {
        return java.util.stream.StreamSupport.stream(entidades.spliterator(), false)
                .collect(Collectors.toMap(extractor, Function.identity()));
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private List<String> enumNames(Collection<? extends Enum<?>> values) {
        return values == null ? List.of() : values.stream().map(Enum::name).sorted().toList();
    }
}
