package br.com.topsdojob.v3.application.admin.readonly;

import br.com.topsdojob.v3.application.admin.readonly.dto.AdminAnuncioDetalheDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminAnuncioListaItemDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminLocalizacaoSanitizadaDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminMidiaListaItemDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminPaginaDto;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.RevisaoAnuncioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusDocumentoUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminAnuncioDetalhadoConsultaService {

    private static final int DESCRICAO_RESUMO_MAX = 180;

    private final AnuncioRepository anuncioRepository;
    private final AnuncioMidiaRepository anuncioMidiaRepository;
    private final RevisaoAnuncioRepository revisaoRepository;
    private final DocumentoUsuarioRepository documentoRepository;
    private final AdminLocalizacaoConsultaSupport localizacaoSupport;
    private final AdminMidiaDetalhadaConsultaService midiaService;

    public AdminAnuncioDetalhadoConsultaService(
            AnuncioRepository anuncioRepository,
            AnuncioMidiaRepository anuncioMidiaRepository,
            RevisaoAnuncioRepository revisaoRepository,
            DocumentoUsuarioRepository documentoRepository,
            AdminLocalizacaoConsultaSupport localizacaoSupport,
            AdminMidiaDetalhadaConsultaService midiaService) {
        this.anuncioRepository = anuncioRepository;
        this.anuncioMidiaRepository = anuncioMidiaRepository;
        this.revisaoRepository = revisaoRepository;
        this.documentoRepository = documentoRepository;
        this.localizacaoSupport = localizacaoSupport;
        this.midiaService = midiaService;
    }

    @Transactional(readOnly = true)
    public AdminPaginaDto<AdminAnuncioListaItemDto> listar(
            int page,
            int size,
            StatusAnuncio status,
            StatusModeracaoAnuncio statusModeracao,
            String uf,
            String cidade,
            String bairro,
            String termo,
            boolean comercialLimitado) {
        Set<UUID> idsLocalizacao = localizacaoSupport.filtrarAnuncioIds(uf, cidade, bairro);
        if (idsLocalizacao != null && idsLocalizacao.isEmpty()) {
            return new AdminPaginaDto<>(List.of(), Math.max(page, 0), Math.max(1, Math.min(size, AdminReadOnlyPageRequest.MAX_SIZE)), 0, 0, true);
        }
        var pageable = AdminReadOnlyPageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("atualizadoEm"), Sort.Order.desc("criadoEm"), Sort.Order.asc("id")));
        Page<AnuncioEntity> result = anuncioRepository.findAll(
                anuncioSpec(status, statusModeracao, idsLocalizacao, termo),
                pageable);
        Map<UUID, AdminLocalizacaoSanitizadaDto> localizacoes = localizacaoSupport.carregar(
                result.getContent().stream().map(AnuncioEntity::getId).toList());
        return new AdminPaginaDto<>(
                result.getContent().stream()
                        .map(anuncio -> item(anuncio, localizacoes.get(anuncio.getId()), comercialLimitado))
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isLast());
    }

    @Transactional(readOnly = true)
    public AdminAnuncioDetalheDto detalhar(UUID id, boolean comercialLimitado) {
        AnuncioEntity anuncio = anuncioRepository.findById(id)
                .filter(entity -> entity.getRemovidoEm() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado"));
        AdminLocalizacaoSanitizadaDto localizacao = localizacaoSupport.carregar(List.of(anuncio.getId()))
                .get(anuncio.getId());
        Long revisoesTotal = comercialLimitado ? null : revisaoRepository.countByAnuncioId(anuncio.getId());
        String descricaoResumo = comercialLimitado
                ? null
                : AdminTextoSanitizer.resumo(anuncio.getDescricao(), DESCRICAO_RESUMO_MAX);
        return new AdminAnuncioDetalheDto(
                anuncio.getId(),
                anuncio.getSlug(),
                AdminTextoSanitizer.resumo(anuncio.getTitulo(), 120),
                descricaoResumo,
                enumName(anuncio.getStatus()),
                enumName(anuncio.getStatusModeracao()),
                anuncio.getCategoria(),
                localizacao,
                anuncio.getCriadoEm(),
                anuncio.getAtualizadoEm(),
                anuncio.getPublicadoEm(),
                anuncio.getUltimaPublicacaoEm(),
                anuncioMidiaRepository.countByAnuncioId(anuncio.getId()),
                revisoesTotal,
                anuncio.getWhatsappNormalizado() != null,
                documentoPendente(anuncio),
                anuncio.getPreco() != null,
                comercialLimitado);
    }

    @Transactional(readOnly = true)
    public AdminPaginaDto<AdminMidiaListaItemDto> listarMidiasDoAnuncio(UUID anuncioId, int page, int size) {
        AnuncioEntity anuncio = anuncioRepository.findById(anuncioId)
                .filter(entity -> entity.getRemovidoEm() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado"));
        return midiaService.listarPorAnuncio(anuncio, page, size);
    }

    private AdminAnuncioListaItemDto item(
            AnuncioEntity anuncio,
            AdminLocalizacaoSanitizadaDto localizacao,
            boolean comercialLimitado) {
        return new AdminAnuncioListaItemDto(
                anuncio.getId(),
                anuncio.getSlug(),
                AdminTextoSanitizer.resumo(anuncio.getTitulo(), 120),
                enumName(anuncio.getStatus()),
                enumName(anuncio.getStatusModeracao()),
                localizacao,
                anuncio.getCriadoEm(),
                anuncio.getAtualizadoEm(),
                anuncio.getPublicadoEm(),
                anuncioMidiaRepository.countByAnuncioId(anuncio.getId()),
                comercialLimitado ? null : revisaoRepository.countByAnuncioId(anuncio.getId()),
                anuncio.getWhatsappNormalizado() != null,
                documentoPendente(anuncio),
                comercialLimitado);
    }

    private boolean documentoPendente(AnuncioEntity anuncio) {
        if (anuncio.getUsuarioId() == null) {
            return false;
        }
        return documentoRepository.countByUsuarioIdAndStatusInAndRemovidoEmIsNullAndExpurgadoEmIsNull(
                anuncio.getUsuarioId(),
                List.of(StatusDocumentoUsuario.PENDENTE, StatusDocumentoUsuario.EM_ANALISE)) > 0;
    }

    private Specification<AnuncioEntity> anuncioSpec(
            StatusAnuncio status,
            StatusModeracaoAnuncio statusModeracao,
            Set<UUID> idsLocalizacao,
            String termo) {
        return (root, query, builder) -> {
            var predicate = builder.isNull(root.get("removidoEm"));
            if (status != null) {
                predicate = builder.and(predicate, builder.equal(root.get("status"), status));
            }
            if (statusModeracao != null) {
                predicate = builder.and(predicate, builder.equal(root.get("statusModeracao"), statusModeracao));
            }
            if (idsLocalizacao != null) {
                predicate = builder.and(predicate, root.get("id").in(idsLocalizacao));
            }
            String termoSeguro = termoSeguro(termo);
            if (termoSeguro != null) {
                String like = "%" + termoSeguro.toLowerCase() + "%";
                predicate = builder.and(predicate, builder.or(
                        builder.like(builder.lower(root.get("slug")), like),
                        builder.like(builder.lower(root.get("titulo")), like)));
            }
            return predicate;
        };
    }

    private String termoSeguro(String termo) {
        if (termo == null || termo.isBlank()) {
            return null;
        }
        String value = termo.trim();
        if (value.length() > 80 || !value.matches("[A-Za-z0-9 ._-]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "termo invalido");
        }
        return value;
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
