package br.com.topsdojob.v3.application.publico.anunciante;

import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioMidiaGestaoDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioMidiaLimitesDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioMidiasResponseDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.ReordenarMinhasMidiasRequestDto;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadProperties;
import br.com.topsdojob.v3.application.publico.anunciante.midia.FotoUploadProcessor;
import br.com.topsdojob.v3.application.publico.anunciante.midia.FotoUploadProcessor.FotoProcessada;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadValidator;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadValidator.MidiaValidada;
import br.com.topsdojob.v3.application.publico.anunciante.midia.LimiteMidiasAnuncioService;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
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
    private final FotoUploadProcessor fotoProcessor;
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
            FotoUploadProcessor fotoProcessor,
            MidiaUploadProperties uploadProperties,
            R2StorageProperties storageProperties,
            ObjectProvider<ObjectStorage> storageProvider) {
        this.consultaService = consultaService;
        this.anuncioMidiaRepository = anuncioMidiaRepository;
        this.arquivoMidiaRepository = arquivoMidiaRepository;
        this.revisaoRepository = revisaoRepository;
        this.limiteService = limiteService;
        this.uploadValidator = uploadValidator;
        this.fotoProcessor = fotoProcessor;
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
            String idempotencyKey,
            Authentication authentication) {
        AnuncioEntity anuncio = anuncioMutavel(slug, authentication);
        MidiaValidada validada = uploadValidator.validar(arquivo);
        String chaveIdempotencia = validada.video() ? null : chaveIdempotencia(idempotencyKey);
        UUID arquivoId = validada.video()
                ? UUID.randomUUID()
                : uuidDeterministico("arquivo", anuncio.getId(), chaveIdempotencia);
        UUID vinculoId = validada.video()
                ? UUID.randomUUID()
                : uuidDeterministico("vinculo", anuncio.getId(), chaveIdempotencia);
        ObjectStorage storage = storageObrigatorio();
        if (!validada.video()) {
            ArquivoMidiaEntity existente = arquivoMidiaRepository.findById(arquivoId).orElse(null);
            if (existente != null) {
                validarRepeticaoExistente(anuncio.getId(), vinculoId, existente, validada);
                verificarArquivoPersistido(storage, existente);
                return resposta(anuncio);
            }
        }
        List<AnuncioMidiaEntity> atuais = vinculosAtivos(anuncio.getId());
        MeuAnuncioMidiaLimitesDto limites = limites(anuncio, atuais);
        if (validada.video() && limites.videosDisponiveis() < 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "limite de video atingido");
        }
        if (!validada.video() && limites.fotosDisponiveis() < 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "limite de fotos atingido");
        }

        FotoProcessada foto = validada.video() ? null : fotoProcessor.processar(validada);
        byte[] bytesFinais = foto == null ? validada.bytes() : foto.bytes();
        String mimeFinal = foto == null ? validada.mimeType() : foto.mimeType();
        String extensaoFinal = foto == null ? validada.extensao() : foto.extensao();
        Integer larguraFinal = foto == null ? validada.largura() : Integer.valueOf(foto.largura());
        Integer alturaFinal = foto == null ? validada.altura() : Integer.valueOf(foto.altura());
        String shaFinal = foto == null ? validada.sha256() : foto.sha256();
        String key = storageProperties.getPrivateMediaPrefix()
                + "anuncios/" + anuncio.getId() + "/" + arquivoId + "/"
                + (foto == null ? "video." + extensaoFinal : "foto-v" + foto.pipelineVersao() + "." + extensaoFinal);

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        ArquivoMidiaEntity arquivoEntity = ArquivoMidiaEntity.criarUploadPendente(
                arquivoId,
                "R2",
                storageProperties.getPrivateMediaBucket(),
                key,
                validada.nomeOriginal(),
                mimeFinal,
                bytesFinais.length,
                larguraFinal,
                alturaFinal,
                null,
                shaFinal,
                agora);
        if (foto != null) {
            arquivoEntity.registrarProcessamento(
                    foto.pipelineVersao(), foto.marcaDaguaVersao(), foto.processadoEm(), foto.sha256Origem());
        }
        arquivoMidiaRepository.save(arquivoEntity);
        arquivoMidiaRepository.flush();
        ObjectWriteResult writeResult = storage.putIfAbsent(
                StorageArea.PRIVATE_MEDIA, key, bytesFinais, mimeFinal);
        if (writeResult == ObjectWriteResult.CREATED) limparObjetoSeRollback(storage, key);
        verificarObjetoPersistido(storage, key, bytesFinais, mimeFinal, larguraFinal, alturaFinal, foto != null);
        int proximaOrdem = atuais.stream()
                .map(AnuncioMidiaEntity::getOrdem)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(-1) + 1;
        anuncioMidiaRepository.save(AnuncioMidiaEntity.criarUploadPendente(
                vinculoId,
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

    private void validarRepeticaoExistente(
            UUID anuncioId,
            UUID vinculoId,
            ArquivoMidiaEntity arquivo,
            MidiaValidada upload) {
        AnuncioMidiaEntity vinculo = anuncioMidiaRepository.findById(vinculoId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "chave idempotente possui registro incompleto"));
        String shaOrigem = arquivo.getSha256Origem() == null ? arquivo.getSha256() : arquivo.getSha256Origem();
        if (!anuncioId.equals(vinculo.getAnuncioId())
                || !arquivo.getId().equals(vinculo.getArquivoMidiaId())
                || !Objects.equals(shaOrigem, upload.sha256())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "chave idempotente reutilizada com outro arquivo");
        }
    }

    private void verificarObjetoPersistido(
            ObjectStorage storage,
            String key,
            byte[] esperado,
            String mimeType,
            Integer largura,
            Integer altura,
            boolean foto) {
        StoredObject objeto = storage.get(StorageArea.PRIVATE_MEDIA, key);
        String mimePersistido = objeto.contentType().split(";", 2)[0].trim().toLowerCase(java.util.Locale.ROOT);
        if (!Objects.equals(mimeType, mimePersistido)
                || !Objects.equals(sha256(esperado), sha256(objeto.content()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "objeto existente diverge do upload processado");
        }
        if (foto) fotoProcessor.validarDerivado(objeto.content(), mimeType, largura, altura);
    }

    private void verificarArquivoPersistido(ObjectStorage storage, ArquivoMidiaEntity arquivo) {
        if (arquivo.getChaveObjeto() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "registro idempotente sem objeto persistido");
        }
        StorageArea area;
        if (Objects.equals(storageProperties.getPrivateMediaBucket(), arquivo.getBucket())
                && arquivo.getChaveObjeto().startsWith(storageProperties.getPrivateMediaPrefix())) {
            area = StorageArea.PRIVATE_MEDIA;
        } else if (Objects.equals(storageProperties.getPublicMediaBucket(), arquivo.getBucket())
                && arquivo.getChaveObjeto().startsWith(storageProperties.getPublicMediaPrefix())) {
            area = StorageArea.PUBLIC_MEDIA;
        } else {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "registro idempotente aponta para area invalida");
        }
        StoredObject objeto = storage.get(area, arquivo.getChaveObjeto());
        if (objeto == null
                || !Objects.equals(arquivo.getSha256(), sha256(objeto.content()))
                || !Objects.equals(
                        arquivo.getMimeType(),
                        objeto.contentType().split(";", 2)[0].trim().toLowerCase(java.util.Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "registro idempotente diverge do objeto persistido");
        }
        if (arquivo.getPipelineVersao() != null) {
            fotoProcessor.validarDerivado(
                    objeto.content(), arquivo.getMimeType(), arquivo.getLargura(), arquivo.getAltura());
        }
    }

    private String chaveIdempotencia(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("[A-Za-z0-9._:-]{1,160}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key invalida");
        }
        return normalized;
    }

    private UUID uuidDeterministico(String tipo, UUID anuncioId, String idempotencyKey) {
        return UUID.nameUUIDFromBytes(
                ("midia-upload-v1:" + tipo + ":" + anuncioId + ":" + idempotencyKey)
                        .getBytes(StandardCharsets.UTF_8));
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 indisponivel", exception);
        }
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
