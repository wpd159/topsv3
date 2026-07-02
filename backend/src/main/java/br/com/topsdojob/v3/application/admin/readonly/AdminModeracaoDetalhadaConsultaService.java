package br.com.topsdojob.v3.application.admin.readonly;

import br.com.topsdojob.v3.application.admin.readonly.dto.AdminPaginaDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminRevisaoDetalheDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminRevisaoListaItemDto;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.moderacao.RevisaoAnuncioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.RevisaoAnuncioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusRevisaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoRevisaoAnuncio;
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
public class AdminModeracaoDetalhadaConsultaService {

    private final RevisaoAnuncioRepository revisaoRepository;
    private final AnuncioRepository anuncioRepository;

    public AdminModeracaoDetalhadaConsultaService(
            RevisaoAnuncioRepository revisaoRepository,
            AnuncioRepository anuncioRepository) {
        this.revisaoRepository = revisaoRepository;
        this.anuncioRepository = anuncioRepository;
    }

    @Transactional(readOnly = true)
    public AdminPaginaDto<AdminRevisaoListaItemDto> listar(
            int page,
            int size,
            StatusRevisaoAnuncio status,
            TipoRevisaoAnuncio tipo,
            UUID anuncioId) {
        var pageable = AdminReadOnlyPageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("criadoEm"), Sort.Order.asc("id")));
        Page<RevisaoAnuncioEntity> result = revisaoRepository.findAll(revisaoSpec(status, tipo, anuncioId), pageable);
        Map<UUID, AnuncioEntity> anuncios = anuncioRepository.findAllById(
                        result.getContent().stream().map(RevisaoAnuncioEntity::getAnuncioId).filter(java.util.Objects::nonNull).toList())
                .stream()
                .collect(Collectors.toMap(AnuncioEntity::getId, Function.identity()));
        return new AdminPaginaDto<>(
                result.getContent().stream().map(revisao -> item(revisao, anuncios.get(revisao.getAnuncioId()))).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isLast());
    }

    @Transactional(readOnly = true)
    public AdminRevisaoDetalheDto detalhar(UUID id) {
        RevisaoAnuncioEntity revisao = revisaoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "revisao nao encontrada"));
        AnuncioEntity anuncio = revisao.getAnuncioId() == null
                ? null
                : anuncioRepository.findById(revisao.getAnuncioId()).orElse(null);
        return new AdminRevisaoDetalheDto(
                revisao.getId(),
                revisao.getAnuncioId(),
                anuncio == null ? null : anuncio.getSlug(),
                enumName(revisao.getTipo()),
                enumName(revisao.getStatus()),
                revisao.getPayloadSolicitado() != null && !revisao.getPayloadSolicitado().isBlank(),
                revisao.getCriadoEm(),
                revisao.getFinalizadoEm(),
                true);
    }

    private AdminRevisaoListaItemDto item(RevisaoAnuncioEntity revisao, AnuncioEntity anuncio) {
        return new AdminRevisaoListaItemDto(
                revisao.getId(),
                revisao.getAnuncioId(),
                anuncio == null ? null : anuncio.getSlug(),
                enumName(revisao.getTipo()),
                enumName(revisao.getStatus()),
                revisao.getPayloadSolicitado() != null && !revisao.getPayloadSolicitado().isBlank(),
                revisao.getCriadoEm(),
                revisao.getFinalizadoEm());
    }

    private Specification<RevisaoAnuncioEntity> revisaoSpec(
            StatusRevisaoAnuncio status,
            TipoRevisaoAnuncio tipo,
            UUID anuncioId) {
        return (root, query, builder) -> {
            var predicate = builder.conjunction();
            if (status != null) {
                predicate = builder.and(predicate, builder.equal(root.get("status"), status));
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
