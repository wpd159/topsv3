package br.com.topsdojob.v3.application.admin.moderacao;

import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecidirFotosLoteRequestDto;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecisaoFotoLoteAcao;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecisaoFotoLoteItemRequestDto;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoUsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminModeracaoFotosLotePrevalidacaoService {

    private static final int MAX_FOTOS = 100;
    private static final int OBSERVACAO_MAX = 240;

    private final AnuncioRepository anuncioRepository;
    private final AnuncioMidiaRepository anuncioMidiaRepository;
    private final ArquivoMidiaRepository arquivoMidiaRepository;
    private final DocumentoUsuarioRepository documentoUsuarioRepository;

    public AdminModeracaoFotosLotePrevalidacaoService(
            AnuncioRepository anuncioRepository,
            AnuncioMidiaRepository anuncioMidiaRepository,
            ArquivoMidiaRepository arquivoMidiaRepository,
            DocumentoUsuarioRepository documentoUsuarioRepository) {
        this.anuncioRepository = anuncioRepository;
        this.anuncioMidiaRepository = anuncioMidiaRepository;
        this.arquivoMidiaRepository = arquivoMidiaRepository;
        this.documentoUsuarioRepository = documentoUsuarioRepository;
    }

    @Transactional
    public Prevalidacao validar(
            UUID anuncioId,
            AdminDecidirFotosLoteRequestDto request,
            AdminUserPrincipal ator) {
        validarAtor(ator);
        List<AdminDecisaoFotoLoteItemRequestDto> itens =
                request == null ? null : request.fotos();
        if (itens == null || itens.isEmpty() || itens.size() > MAX_FOTOS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "o lote deve conter entre 1 e 100 fotos");
        }

        var anuncio = anuncioRepository.findByIdForModeration(anuncioId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "anuncio nao encontrado"));
        if (anuncio.getStatus() == StatusAnuncio.BLOQUEADO
                || anuncio.getStatus() == StatusAnuncio.REMOVIDO) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "estado do anuncio impede moderacao de fotos");
        }

        Set<UUID> ids = new LinkedHashSet<>();
        for (AdminDecisaoFotoLoteItemRequestDto item : itens) {
            if (item == null || item.mediaId() == null || !ids.add(item.mediaId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "mediaId obrigatorio e nao duplicado");
            }
        }

        Map<UUID, AnuncioMidiaEntity> midias = new LinkedHashMap<>();
        anuncioMidiaRepository.findByIdInForUpdate(ids)
                .forEach(item -> midias.put(item.getId(), item));
        if (midias.size() != ids.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "foto do lote nao encontrada");
        }

        Set<UUID> arquivoIds = new LinkedHashSet<>();
        for (AnuncioMidiaEntity midia : midias.values()) {
            if (!anuncioId.equals(midia.getAnuncioId())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "foto nao pertence ao anuncio informado");
            }
            if (midia.getTipo() != TipoAnuncioMidia.FOTO) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "videos e stories nao participam do lote de fotos");
            }
            if (midia.getArquivoMidiaId() == null) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "arquivo da foto nao encontrado");
            }
            arquivoIds.add(midia.getArquivoMidiaId());
        }

        Map<UUID, ArquivoMidiaEntity> arquivos = new LinkedHashMap<>();
        arquivoMidiaRepository.findByIdInForUpdate(arquivoIds)
                .forEach(item -> arquivos.put(item.getId(), item));
        if (arquivos.size() != arquivoIds.size()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "arquivo da foto nao encontrado");
        }

        List<ItemValidado> validados = itens.stream()
                .map(item -> validarItem(item, midias.get(item.mediaId()), arquivos))
                .toList();
        long fotosPublicaveisAntes = anuncioMidiaRepository.countByAnuncioIdAndTipoAndStatus(
                anuncioId,
                TipoAnuncioMidia.FOTO,
                StatusAnuncioMidia.PUBLICAVEL);
        return new Prevalidacao(anuncioId, validados, fotosPublicaveisAntes);
    }

    private ItemValidado validarItem(
            AdminDecisaoFotoLoteItemRequestDto item,
            AnuncioMidiaEntity midia,
            Map<UUID, ArquivoMidiaEntity> arquivos) {
        if (item.decisao() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "decisao da foto obrigatoria");
        }
        ArquivoMidiaEntity arquivo = arquivos.get(midia.getArquivoMidiaId());
        if (item.decisao() == AdminDecisaoFotoLoteAcao.EXCLUIR) {
            if (item.classificacao() != null || hasText(item.observacao())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "exclusao de foto nao aceita classificacao ou observacao");
            }
            if (midia.getStatus() == StatusAnuncioMidia.REMOVIDA) {
                return new ItemValidado(
                        midia.getId(),
                        item.decisao(),
                        null,
                        null,
                        true);
            }
            return new ItemValidado(midia.getId(), item.decisao(), null, null, false);
        }

        if (documentoUsuarioRepository
                .existsByArquivoMidiaIdAndRemovidoEmIsNullAndExpurgadoEmIsNull(arquivo.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "arquivo documental nao pode participar da moderacao de fotos");
        }

        VisibilidadeMidia classificacao = item.classificacao();
        if (classificacao == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "classificacao obrigatoria para aprovar foto");
        }
        String observacao = classificacao == VisibilidadeMidia.RESTRITA_18
                ? AdminModeracaoSanitizer.texto(item.observacao(), OBSERVACAO_MAX)
                : null;
        if (midia.getStatus() == StatusAnuncioMidia.PUBLICAVEL
                && midia.getVisibilidadeMidia() == classificacao
                && arquivo.getStatusArquivo() == StatusArquivoMidia.VALIDADO) {
            return new ItemValidado(
                    midia.getId(),
                    item.decisao(),
                    classificacao,
                    observacao,
                    true);
        }
        exigirPendente(midia);
        return new ItemValidado(
                midia.getId(),
                item.decisao(),
                classificacao,
                observacao,
                false);
    }

    private void exigirPendente(AnuncioMidiaEntity midia) {
        if (midia.getStatus() != StatusAnuncioMidia.PENDENTE
                && midia.getStatus() != StatusAnuncioMidia.AJUSTE_SOLICITADO) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "transicao de foto incompativel com o estado atual");
        }
    }

    private void validarAtor(AdminUserPrincipal ator) {
        boolean papelPermitido = ator != null
                && (ator.papeis().contains(PapelUsuario.ADMIN)
                || ator.papeis().contains(PapelUsuario.MODERADOR));
        boolean autoridade = ator != null
                && ator.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("MIDIA_REVISAR"::equals);
        if (ator == null || !ator.isEnabled() || !papelPermitido || !autoridade) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "moderador obrigatorio");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record Prevalidacao(
            UUID anuncioId,
            List<ItemValidado> itens,
            long fotosPublicaveisAntes) {
    }

    public record ItemValidado(
            UUID mediaId,
            AdminDecisaoFotoLoteAcao decisao,
            VisibilidadeMidia classificacao,
            String observacao,
            boolean jaProcessada) {
    }
}
