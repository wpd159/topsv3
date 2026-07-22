package br.com.topsdojob.v3.application.admin.readonly;

import br.com.topsdojob.v3.application.admin.readonly.dto.AdminMidiaDetalheDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminMidiaListaItemDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminPaginaDto;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminMidiaDetalhadaConsultaService {

    private final AnuncioMidiaRepository anuncioMidiaRepository;
    private final ArquivoMidiaRepository arquivoMidiaRepository;
    private final AnuncioRepository anuncioRepository;

    public AdminMidiaDetalhadaConsultaService(
            AnuncioMidiaRepository anuncioMidiaRepository,
            ArquivoMidiaRepository arquivoMidiaRepository,
            AnuncioRepository anuncioRepository) {
        this.anuncioMidiaRepository = anuncioMidiaRepository;
        this.arquivoMidiaRepository = arquivoMidiaRepository;
        this.anuncioRepository = anuncioRepository;
    }

    @Transactional(readOnly = true)
    public AdminPaginaDto<AdminMidiaListaItemDto> listar(
            int page,
            int size,
            StatusAnuncioMidia status,
            VisibilidadeMidia visibilidadeMidia,
            TipoAnuncioMidia tipo,
            UUID anuncioId) {
        var pageable = AdminReadOnlyPageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("atualizadoEm"), Sort.Order.desc("criadoEm"), Sort.Order.asc("id")));
        Page<AnuncioMidiaEntity> result = anuncioMidiaRepository.findAll(
                midiaSpec(status, visibilidadeMidia, tipo, anuncioId),
                pageable);
        return page(result);
    }

    @Transactional(readOnly = true)
    public AdminPaginaDto<AdminMidiaListaItemDto> listarPorAnuncio(AnuncioEntity anuncio, int page, int size) {
        var pageable = AdminReadOnlyPageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.asc("ordem"), Sort.Order.asc("criadoEm"), Sort.Order.asc("id")));
        Page<AnuncioMidiaEntity> result = anuncioMidiaRepository.findByAnuncioIdAndTipoNot(
                anuncio.getId(), TipoAnuncioMidia.STORY, pageable);
        return page(result);
    }

    @Transactional(readOnly = true)
    public AdminMidiaDetalheDto detalhar(UUID id) {
        AnuncioMidiaEntity midia = anuncioMidiaRepository.findById(id)
                .filter(item -> item.getTipo() != TipoAnuncioMidia.STORY)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "midia nao encontrada"));
        ArquivoMidiaEntity arquivo = midia.getArquivoMidiaId() == null
                ? null
                : arquivoMidiaRepository.findById(midia.getArquivoMidiaId()).orElse(null);
        AnuncioEntity anuncio = anuncioRepository.findById(midia.getAnuncioId()).orElse(null);
        return new AdminMidiaDetalheDto(
                midia.getId(),
                midia.getAnuncioId(),
                midia.getArquivoMidiaId(),
                anuncio == null ? null : anuncio.getSlug(),
                enumName(midia.getTipo()),
                enumName(midia.getFinalidade()),
                midia.getOrdem(),
                enumName(midia.getStatus()),
                enumName(midia.getVisibilidadeMidia()),
                arquivo == null ? null : enumName(arquivo.getStatusArquivo()),
                arquivo == null ? null : arquivo.getMimeType(),
                arquivo == null ? null : arquivo.getTamanhoBytes(),
                arquivo == null ? null : arquivo.getLargura(),
                arquivo == null ? null : arquivo.getAltura(),
                arquivo == null ? null : arquivo.getDuracaoMs(),
                midia.getCriadoEm(),
                midia.getAtualizadoEm(),
                true);
    }

    private AdminPaginaDto<AdminMidiaListaItemDto> page(Page<AnuncioMidiaEntity> result) {
        Map<UUID, ArquivoMidiaEntity> arquivos = arquivoMidiaRepository.findAllById(
                        result.getContent().stream().map(AnuncioMidiaEntity::getArquivoMidiaId).filter(java.util.Objects::nonNull).toList())
                .stream()
                .collect(Collectors.toMap(ArquivoMidiaEntity::getId, Function.identity()));
        Map<UUID, AnuncioEntity> anuncios = anuncioRepository.findAllById(
                        result.getContent().stream().map(AnuncioMidiaEntity::getAnuncioId).filter(java.util.Objects::nonNull).toList())
                .stream()
                .collect(Collectors.toMap(AnuncioEntity::getId, Function.identity()));
        return new AdminPaginaDto<>(
                result.getContent().stream()
                        .map(midia -> item(midia, arquivos.get(midia.getArquivoMidiaId()), anuncios.get(midia.getAnuncioId())))
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isLast());
    }

    private AdminMidiaListaItemDto item(AnuncioMidiaEntity midia, ArquivoMidiaEntity arquivo, AnuncioEntity anuncio) {
        return new AdminMidiaListaItemDto(
                midia.getId(),
                midia.getAnuncioId(),
                anuncio == null ? null : anuncio.getSlug(),
                enumName(midia.getTipo()),
                enumName(midia.getFinalidade()),
                midia.getOrdem(),
                enumName(midia.getStatus()),
                enumName(midia.getVisibilidadeMidia()),
                arquivo == null ? null : enumName(arquivo.getStatusArquivo()),
                arquivo == null ? null : arquivo.getMimeType(),
                arquivo == null ? null : arquivo.getTamanhoBytes(),
                arquivo == null ? null : arquivo.getLargura(),
                arquivo == null ? null : arquivo.getAltura(),
                arquivo == null ? null : arquivo.getDuracaoMs(),
                midia.getCriadoEm(),
                midia.getAtualizadoEm(),
                true);
    }

    private Specification<AnuncioMidiaEntity> midiaSpec(
            StatusAnuncioMidia status,
            VisibilidadeMidia visibilidadeMidia,
            TipoAnuncioMidia tipo,
            UUID anuncioId) {
        return (root, query, builder) -> {
            var predicate = builder.notEqual(root.get("tipo"), TipoAnuncioMidia.STORY);
            if (status != null) {
                predicate = builder.and(predicate, builder.equal(root.get("status"), status));
            }
            if (visibilidadeMidia != null) {
                predicate = builder.and(predicate, builder.equal(root.get("visibilidadeMidia"), visibilidadeMidia));
            }
            if (tipo != null) {
                predicate = builder.and(predicate, builder.equal(root.get("tipo"), tipo));
            }
            if (anuncioId != null) {
                predicate = builder.and(predicate, builder.equal(root.get("anuncioId"), anuncioId));
            }
            return predicate;
        };
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
