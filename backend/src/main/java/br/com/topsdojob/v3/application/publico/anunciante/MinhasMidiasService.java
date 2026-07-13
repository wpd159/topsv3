package br.com.topsdojob.v3.application.publico.anunciante;

import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioMidiaGestaoDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioMidiaLimitesDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioMidiasResponseDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.ReordenarMinhasMidiasRequestDto;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadProperties;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadValidator;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadValidator.MidiaValidada;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MinhasMidiasService {

    private final MeusAnunciosConsultaService consultaService;
    private final AnuncioMidiaRepository anuncioMidiaRepository;
    private final ArquivoMidiaRepository arquivoMidiaRepository;
    private final RevisaoAnuncioRepository revisaoRepository;
    private final LimiteMidiasAnuncioService limiteService;
    private final MidiaUploadValidator uploadValidator;
    private final MidiaUploadProperties uploadProperties;
    private final R2StorageProperties storageProperties;
    private final ObjectProvider<ObjectStorage> storageProvider;

    public MinhasMidiasService(
            MeusAnunciosConsultaService consultaService,
            AnuncioMidiaRepository anuncioMidiaRepository,
            ArquivoMidiaRepository arquivoMidiaRepository,
            RevisaoAnuncioRepository revisaoRepository,
            LimiteMidiasAnuncioService limiteService,
            MidiaUploadValidator uploadValidator,
            MidiaUploadProperties uploadProperties,
            R2StorageProperties storageProperties,
            ObjectProvider<ObjectStorage> storageProvider) {
        this.consultaService = consultaService;
        this.anuncioMidiaRepository = anuncioMidiaRepository;
        this.arquivoMidiaRepository = arquivoMidiaRepository;
        this.revisaoRepository = revisaoRepository;
        this.limiteService = limiteService;
        this.uploadValidator = uploadValidator;
        this.uploadProperties = uploadProperties;
        this.storageProperties = storageProperties;
        this.storageProvider = storageProvider;
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
            Authentication authentication) {
        AnuncioEntity anuncio = anuncioMutavel(slug, authentication);
        MidiaValidada validada = uploadValidator.validar(arquivo);
        List<AnuncioMidiaEntity> atuais = vinculosAtivos(anuncio.getId());
        MeuAnuncioMidiaLimitesDto limites = limites(anuncio, atuais);
        if (validada.video() && limites.videosDisponiveis() < 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "limite de video atingido");
        }
        if (!validada.video() && limites.fotosDisponiveis() < 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "limite de fotos atingido");
        }

        ObjectStorage storage = storageObrigatorio();
        UUID arquivoId = UUID.randomUUID();
        String key = storageProperties.getPrivateMediaPrefix()
                + "anuncios/" + anuncio.getId() + "/" + arquivoId + "." + validada.extensao();
        storage.put(StorageArea.PRIVATE_MEDIA, key, validada.bytes(), validada.mimeType());
        limparObjetoSeRollback(storage, key);

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        ArquivoMidiaEntity arquivoEntity = ArquivoMidiaEntity.criarUploadPendente(
                arquivoId,
                "R2",
                storageProperties.getPrivateMediaBucket(),
                key,
                validada.nomeOriginal(),
                validada.mimeType(),
                validada.bytes().length,
                validada.largura(),
                validada.altura(),
                null,
                validada.sha256(),
                agora);
        arquivoMidiaRepository.save(arquivoEntity);
        int proximaOrdem = atuais.stream()
                .map(AnuncioMidiaEntity::getOrdem)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(-1) + 1;
        anuncioMidiaRepository.save(AnuncioMidiaEntity.criarUploadPendente(
                UUID.randomUUID(),
                anuncio.getId(),
                arquivoId,
                validada.video() ? TipoAnuncioMidia.VIDEO : TipoAnuncioMidia.FOTO,
                proximaOrdem,
                agora));
        return resposta(anuncio);
    }

    @Transactional
    public MeuAnuncioMidiasResponseDto reordenar(
            String slug,
            ReordenarMinhasMidiasRequestDto request,
            Authentication authentication) {
        AnuncioEntity anuncio = anuncioMutavel(slug, authentication);
        List<AnuncioMidiaEntity> atuais = vinculosAtivos(anuncio.getId());
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
            Authentication authentication) {
        AnuncioEntity anuncio = anuncioMutavel(slug, authentication);
        AnuncioMidiaEntity midia = anuncioMidiaRepository.findById(midiaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "midia nao encontrada"));
        if (!anuncio.getId().equals(midia.getAnuncioId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "midia pertence a outro anuncio");
        }
        if (midia.getStatus() == StatusAnuncioMidia.REMOVIDA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "midia ja removida");
        }
        midia.removerLogicamente(OffsetDateTime.now(ZoneOffset.UTC));
        anuncioMidiaRepository.flush();
        return resposta(anuncio);
    }

    private AnuncioEntity anuncioMutavel(String slug, Authentication authentication) {
        AnuncioEntity anuncio = consultaService.anuncioDoUsuario(slug, authentication);
        if (revisaoRepository.existsByAnuncioIdAndStatusIn(
                anuncio.getId(), List.of(StatusRevisaoAnuncio.EM_ANALISE))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "anuncio possui revisao em analise");
        }
        return anuncio;
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
        return new MeuAnuncioMidiasResponseDto(List.copyOf(resultado), limites);
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
                uploadProperties.getMaxImageBytes(),
                uploadProperties.getMaxVideoBytes());
    }

    private List<AnuncioMidiaEntity> vinculosAtivos(UUID anuncioId) {
        return anuncioMidiaRepository.findByAnuncioId(anuncioId).stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getStatus() != StatusAnuncioMidia.REMOVIDA)
                .filter(item -> item.getTipo() != TipoAnuncioMidia.STORY)
                .sorted(Comparator.comparing(AnuncioMidiaEntity::getOrdem, Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    private ObjectStorage storageObrigatorio() {
        ObjectStorage storage = storageProvider.getIfAvailable();
        if (storage == null || !storageProperties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "storage de midia indisponivel");
        }
        return storage;
    }

    private void limparObjetoSeRollback(ObjectStorage storage, String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    try {
                        storage.delete(StorageArea.PRIVATE_MEDIA, key);
                    } catch (RuntimeException ignored) {
                        // O objeto orfao permanece privado; a limpeza operacional pode reconciliar depois.
                    }
                }
            }
        });
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
