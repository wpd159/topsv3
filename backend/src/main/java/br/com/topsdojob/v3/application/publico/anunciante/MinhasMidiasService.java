package br.com.topsdojob.v3.application.publico.anunciante;

import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioMidiaGestaoDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioMidiaLimitesDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioMidiasResponseDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioCicloVidaDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.ReordenarMinhasMidiasRequestDto;
import br.com.topsdojob.v3.application.anuncio.midia.AnuncioMidiaUploadCoreService;
import br.com.topsdojob.v3.application.anuncio.midia.AnuncioMidiaUploadCoreService.CapacidadeProprietario;
import br.com.topsdojob.v3.application.anuncio.FotoElegivelAnuncioPolicy;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadProperties;
import br.com.topsdojob.v3.application.publico.anunciante.midia.LimiteMidiasAnuncioService;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.RevisaoAnuncioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusRevisaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MinhasMidiasService {

    private final MeusAnunciosConsultaService consultaService;
    private final AnuncioMidiaRepository anuncioMidiaRepository;
    private final ArquivoMidiaRepository arquivoMidiaRepository;
    private final RevisaoAnuncioRepository revisaoRepository;
    private final LimiteMidiasAnuncioService limiteService;
    private final MidiaUploadProperties uploadProperties;
    private final R2StorageProperties storageProperties;
    private final ObjectProvider<ObjectStorage> storageProvider;
    private final AnuncioMidiaUploadCoreService uploadCoreService;
    private final FotoElegivelAnuncioPolicy fotoElegivelPolicy;
    private final MeuAnuncioCicloVidaService cicloVidaService;

    public MinhasMidiasService(
            MeusAnunciosConsultaService consultaService,
            AnuncioMidiaRepository anuncioMidiaRepository,
            ArquivoMidiaRepository arquivoMidiaRepository,
            RevisaoAnuncioRepository revisaoRepository,
            LimiteMidiasAnuncioService limiteService,
            MidiaUploadProperties uploadProperties,
            R2StorageProperties storageProperties,
            ObjectProvider<ObjectStorage> storageProvider,
            AnuncioMidiaUploadCoreService uploadCoreService,
            FotoElegivelAnuncioPolicy fotoElegivelPolicy,
            MeuAnuncioCicloVidaService cicloVidaService) {
        this.consultaService = consultaService;
        this.anuncioMidiaRepository = anuncioMidiaRepository;
        this.arquivoMidiaRepository = arquivoMidiaRepository;
        this.revisaoRepository = revisaoRepository;
        this.limiteService = limiteService;
        this.uploadProperties = uploadProperties;
        this.storageProperties = storageProperties;
        this.storageProvider = storageProvider;
        this.uploadCoreService = uploadCoreService;
        this.fotoElegivelPolicy = fotoElegivelPolicy;
        this.cicloVidaService = cicloVidaService;
    }

    @Transactional(readOnly = true)
    public MeuAnuncioMidiasResponseDto listar(String slug, Authentication authentication) {
        AnuncioEntity anuncio = consultaService.anuncioDoUsuario(slug, authentication);
        return resposta(anuncio);
    }

    @Transactional(readOnly = true)
    public MeuAnuncioMidiaLimitesDto limites(String slug, Authentication authentication) {
        AnuncioEntity anuncio = consultaService.anuncioDoUsuario(slug, authentication);
        return limites(anuncio, vinculosAtivos(anuncio.getId()));
    }

    @Transactional
    public MeuAnuncioMidiasResponseDto enviar(
            String slug,
            MultipartFile arquivo,
            String idempotencyKey,
            Authentication authentication) {
        return enviarLoteInterno(slug, List.of(arquivo), idempotencyKey, authentication);
    }

    @Transactional
    public MeuAnuncioMidiasResponseDto enviarLote(
            String slug,
            List<MultipartFile> arquivos,
            String idempotencyKey,
            Authentication authentication) {
        return enviarLoteInterno(slug, arquivos, idempotencyKey, authentication);
    }

    private MeuAnuncioMidiasResponseDto enviarLoteInterno(
            String slug,
            List<MultipartFile> arquivos,
            String idempotencyKey,
            Authentication authentication) {
        AnuncioEntity anuncio = anuncioMutavel(slug, authentication);
        List<AnuncioMidiaEntity> atuais = vinculosAtivos(
                anuncioMidiaRepository.findByAnuncioIdForUpdate(anuncio.getId()));
        MeuAnuncioMidiaLimitesDto limites = limites(anuncio, atuais);
        uploadCoreService.enviarProprietario(
                anuncio,
                arquivos,
                idempotencyKey,
                new CapacidadeProprietario(
                        limites.fotosDisponiveis(),
                        limites.videosDisponiveis(),
                        limites.videoAtivo()));
        return resposta(anuncio);
    }

    @Transactional
    public MeuAnuncioMidiasResponseDto reordenar(
            String slug,
            ReordenarMinhasMidiasRequestDto request,
            Authentication authentication) {
        AnuncioEntity anuncio = anuncioMutavel(slug, authentication);
        List<AnuncioMidiaEntity> atuais = vinculosAtivos(
                anuncioMidiaRepository.findByAnuncioIdForUpdate(anuncio.getId()));
        List<UUID> ids = request == null || request.midiaIds() == null ? List.of() : request.midiaIds();
        if (ids.size() != atuais.size() || new HashSet<>(ids).size() != ids.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ordem deve conter todas as midias ativas sem duplicidade");
        }
        Map<UUID, AnuncioMidiaEntity> porId = atuais.stream()
                .collect(Collectors.toMap(AnuncioMidiaEntity::getId, Function.identity()));
        if (!porId.keySet().equals(new HashSet<>(ids))) {
            boolean existeForaDoAnuncio = ids.stream().anyMatch(id -> !porId.containsKey(id) && anuncioMidiaRepository.existsById(id));
            throw new ResponseStatusException(
                    existeForaDoAnuncio ? HttpStatus.FORBIDDEN : HttpStatus.NOT_FOUND,
                    existeForaDoAnuncio ? "midia pertence a outro anuncio" : "midia nao encontrada");
        }

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        int deslocamento = atuais.stream().map(AnuncioMidiaEntity::getOrdem).filter(Objects::nonNull)
                .max(Integer::compareTo).orElse(0) + atuais.size() + 100;
        for (int index = 0; index < ids.size(); index++) {
            porId.get(ids.get(index)).reordenar(deslocamento + index, agora);
        }
        anuncioMidiaRepository.flush();
        for (int index = 0; index < ids.size(); index++) {
            porId.get(ids.get(index)).reordenar(index, agora);
        }
        anuncioMidiaRepository.flush();
        return resposta(anuncio);
    }

    @Transactional
    public MeuAnuncioMidiasResponseDto remover(
            String slug,
            UUID midiaId,
            Authentication authentication,
            String requestId) {
        AnuncioEntity anuncio = consultaService.anuncioDoUsuarioParaRemocaoMidia(slug, authentication);
        List<AnuncioMidiaEntity> vinculadas = anuncioMidiaRepository.findByAnuncioIdForUpdate(anuncio.getId());
        AnuncioMidiaEntity midia = vinculadas.stream()
                .filter(item -> item != null && Objects.equals(item.getId(), midiaId))
                .findFirst()
                .orElse(null);
        if (midia == null) {
            boolean existeForaDoAnuncio = midiaId != null && anuncioMidiaRepository.existsById(midiaId);
            throw new ResponseStatusException(
                    existeForaDoAnuncio ? HttpStatus.FORBIDDEN : HttpStatus.NOT_FOUND,
                    existeForaDoAnuncio ? "midia pertence a outro anuncio" : "midia nao encontrada");
        }
        if (midia.getStatus() == StatusAnuncioMidia.REMOVIDA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "midia ja removida");
        }
        if (anuncio.getRemovidoEm() != null || anuncio.getStatus() == StatusAnuncio.REMOVIDO) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado");
        }
        validarRevisaoMutavel(anuncio);
        List<UUID> arquivoIds = vinculadas.stream().map(AnuncioMidiaEntity::getArquivoMidiaId)
                .filter(Objects::nonNull).distinct().sorted().toList();
        if (!arquivoIds.isEmpty()) {
            arquivoMidiaRepository.findByIdInForUpdate(arquivoIds);
        }
        boolean fotoDoAnuncio = midia.getTipo() == TipoAnuncioMidia.FOTO
                && (midia.getFinalidade() == FinalidadeAnuncioMidia.CAPA
                    || midia.getFinalidade() == FinalidadeAnuncioMidia.GALERIA);
        boolean encerrar = fotoDoAnuncio && anuncioMidiaRepository.findFotosValidasAtivasIds(anuncio.getId())
                .stream().noneMatch(id -> !id.equals(midiaId));
        if (!encerrar) {
            // A pending replacement cannot authorize removing the last approved photo
            // while the advertisement stays operational.
            fotoElegivelPolicy.validarRemocaoIndividual(anuncio, midiaId);
        }
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        midia.removerLogicamente(agora);
        anuncioMidiaRepository.flush();
        if (encerrar) {
            cicloVidaService.encerrarPorUltimaFoto(anuncio, midiaId, requestId, agora);
        }
        return resposta(anuncio);
    }

    private AnuncioEntity anuncioMutavel(String slug, Authentication authentication) {
        AnuncioEntity anuncio = consultaService.anuncioDoUsuarioParaAtualizacao(slug, authentication);
        validarRevisaoMutavel(anuncio);
        return anuncio;
    }

    private void validarRevisaoMutavel(AnuncioEntity anuncio) {
        if (revisaoRepository.existsByAnuncioIdAndStatusIn(
                anuncio.getId(), List.of(StatusRevisaoAnuncio.EM_ANALISE))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "anuncio possui revisao em analise");
        }
    }

    private MeuAnuncioMidiasResponseDto resposta(AnuncioEntity anuncio) {
        List<AnuncioMidiaEntity> vinculos = vinculosAtivos(anuncio.getId());
        MeuAnuncioMidiaLimitesDto limites = limites(anuncio, vinculos);
        Map<UUID, ArquivoMidiaEntity> arquivos = arquivoMidiaRepository.findByIdIn(vinculos.stream()
                        .map(AnuncioMidiaEntity::getArquivoMidiaId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(ArquivoMidiaEntity::getId, Function.identity()));
        ObjectStorage storage = storageProvider.getIfAvailable();
        int fotoIndex = 0;
        List<MeuAnuncioMidiaGestaoDto> resultado = new ArrayList<>();
        for (AnuncioMidiaEntity vinculo : vinculos) {
            ArquivoMidiaEntity arquivo = arquivos.get(vinculo.getArquivoMidiaId());
            boolean foto = vinculo.getTipo() == TipoAnuncioMidia.FOTO;
            boolean ocultaPorLimite = foto && fotoIndex++ >= limites.maxFotos();
            resultado.add(new MeuAnuncioMidiaGestaoDto(
                    vinculo.getId(),
                    vinculo.getTipo().name(),
                    vinculo.getOrdem(),
                    vinculo.getStatus().name(),
                    enumName(vinculo.getVisibilidadeMidia()),
                    previewUrl(storage, arquivo),
                    vinculo.getVisibilidadeMidia() == VisibilidadeMidia.RESTRITA_18,
                    ocultaPorLimite));
        }
        return new MeuAnuncioMidiasResponseDto(List.copyOf(resultado), limites,
                new MeuAnuncioCicloVidaDto(anuncio.getId(), anuncio.getSlug(), anuncio.getStatus().name(),
                        anuncio.getStatusModeracao().name(), anuncio.getAtualizadoEm(),
                        consultaService.acoesPermitidas(anuncio)),
                anuncioMidiaRepository.findFotosValidasAtivasIds(anuncio.getId()).size());
    }

    private String previewUrl(ObjectStorage storage, ArquivoMidiaEntity arquivo) {
        if (storage == null || arquivo == null || arquivo.getChaveObjeto() == null) return null;
        try {
            if (storageProperties.getPrivateMediaBucket().equals(arquivo.getBucket())
                    && arquivo.getChaveObjeto().startsWith(storageProperties.getPrivateMediaPrefix())) {
                return storage.temporaryGetUrl(StorageArea.PRIVATE_MEDIA, arquivo.getChaveObjeto(), Duration.ofMinutes(5)).toString();
            }
            if (storageProperties.getPublicMediaBucket().equals(arquivo.getBucket())
                    && arquivo.getChaveObjeto().startsWith(storageProperties.getPublicMediaPrefix())) {
                return storage.publicUrl(StorageArea.PUBLIC_MEDIA, arquivo.getChaveObjeto()).map(URI::toString).orElse(null);
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private MeuAnuncioMidiaLimitesDto limites(AnuncioEntity anuncio, List<AnuncioMidiaEntity> vinculos) {
        var limite = limiteService.resolver(anuncio.getId());
        boolean extra = limite.fotosExtrasAtivo();
        int maxFotos = limite.maxFotos();
        int fotos = (int) vinculos.stream().filter(item -> item.getTipo() == TipoAnuncioMidia.FOTO).count();
        int videos = (int) vinculos.stream().filter(item -> item.getTipo() == TipoAnuncioMidia.VIDEO).count();
        return new MeuAnuncioMidiaLimitesDto(
                maxFotos,
                fotos,
                Math.max(0, maxFotos - fotos),
                limite.maxVideos(),
                videos,
                Math.max(0, limite.maxVideos() - videos),
                extra,
                limite.videoAtivo(),
                uploadProperties.getMaxImageBytes(),
                uploadProperties.getMaxVideoBytes());
    }

    private List<AnuncioMidiaEntity> vinculosAtivos(UUID anuncioId) {
        return vinculosAtivos(anuncioMidiaRepository.findByAnuncioId(anuncioId));
    }

    private List<AnuncioMidiaEntity> vinculosAtivos(List<AnuncioMidiaEntity> vinculadas) {
        return vinculadas.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getStatus() != StatusAnuncioMidia.REMOVIDA)
                .filter(item -> item.getTipo() != TipoAnuncioMidia.STORY)
                .sorted(Comparator.comparing(AnuncioMidiaEntity::getOrdem, Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
