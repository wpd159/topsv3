package br.com.topsdojob.v3.application.anuncio.midia;

import br.com.topsdojob.v3.application.publico.anunciante.midia.FotoUploadProcessor;
import br.com.topsdojob.v3.application.publico.anunciante.midia.FotoUploadProcessor.FotoProcessada;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadValidator;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadValidator.MidiaValidada;
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
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Nucleo transacional sem autenticacao; os wrappers autorizam e bloqueiam o anuncio. */
@Service
public class AnuncioMidiaUploadCoreService {

    private static final String IDEMPOTENCIA_PROPRIETARIO = "midia-upload-v1";
    private static final String IDEMPOTENCIA_ADMIN = "midia-upload-admin-v1";

    private final AnuncioMidiaRepository anuncioMidiaRepository;
    private final ArquivoMidiaRepository arquivoMidiaRepository;
    private final MidiaUploadValidator uploadValidator;
    private final FotoUploadProcessor fotoProcessor;
    private final R2StorageProperties storageProperties;
    private final ObjectProvider<ObjectStorage> storageProvider;

    public AnuncioMidiaUploadCoreService(
            AnuncioMidiaRepository anuncioMidiaRepository,
            ArquivoMidiaRepository arquivoMidiaRepository,
            MidiaUploadValidator uploadValidator,
            FotoUploadProcessor fotoProcessor,
            R2StorageProperties storageProperties,
            ObjectProvider<ObjectStorage> storageProvider) {
        this.anuncioMidiaRepository = anuncioMidiaRepository;
        this.arquivoMidiaRepository = arquivoMidiaRepository;
        this.uploadValidator = uploadValidator;
        this.fotoProcessor = fotoProcessor;
        this.storageProperties = storageProperties;
        this.storageProvider = storageProvider;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ResultadoUpload enviarProprietario(
            AnuncioEntity anuncioBloqueado,
            List<MultipartFile> arquivos,
            String idempotencyKey,
            CapacidadeProprietario capacidade) {
        if (arquivos == null || arquivos.isEmpty() || arquivos.size() > 11) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "envie entre 1 e 11 arquivos por lote");
        }
        if (capacidade == null || capacidade.fotosDisponiveis() < 0 || capacidade.videosDisponiveis() < 0) {
            throw new IllegalArgumentException("capacidade de upload obrigatoria");
        }
        return enviar(anuncioBloqueado, new ArrayList<>(arquivos), idempotencyKey,
                ModoUpload.PROPRIETARIO, capacidade);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ResultadoUpload enviarAdministrativo(
            AnuncioEntity anuncioBloqueado,
            MultipartFile arquivo,
            String idempotencyKey) {
        if (arquivo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "arquivo obrigatorio");
        }
        return enviar(anuncioBloqueado, List.of(arquivo), idempotencyKey,
                ModoUpload.ADMINISTRATIVO, null);
    }

    private ResultadoUpload enviar(
            AnuncioEntity anuncio,
            List<MultipartFile> arquivos,
            String idempotencyKey,
            ModoUpload modo,
            CapacidadeProprietario capacidade) {
        if (anuncio == null || anuncio.getId() == null) {
            throw new IllegalArgumentException("anuncio bloqueado obrigatorio");
        }
        String chave = chaveIdempotencia(idempotencyKey);
        List<UploadLoteItem> itens = validarTodos(anuncio.getId(), arquivos, chave, modo);
        List<AnuncioMidiaEntity> vinculosBloqueados = exigirLista(
                anuncioMidiaRepository.findByAnuncioIdForUpdate(anuncio.getId()));
        List<AnuncioMidiaEntity> vinculosIdempotentes = exigirLista(
                anuncioMidiaRepository.findByIdInForUpdate(
                        itens.stream().map(UploadLoteItem::vinculoId).toList()));
        Map<UUID, AnuncioMidiaEntity> vinculoPorId = vinculosIdempotentes.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(AnuncioMidiaEntity::getId, Function.identity()));
        List<ArquivoMidiaEntity> arquivosBloqueados = exigirLista(
                arquivoMidiaRepository.findByIdInForUpdate(
                        itens.stream().map(UploadLoteItem::arquivoId).toList()));
        Map<UUID, ArquivoMidiaEntity> arquivoPorId = arquivosBloqueados.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(ArquivoMidiaEntity::getId, Function.identity()));
        ObjectStorage storage = storageObrigatorio();

        List<ItemUpload> repetidos = new ArrayList<>();
        for (UploadLoteItem item : itens) {
            ArquivoMidiaEntity existente = arquivoPorId.get(item.arquivoId());
            AnuncioMidiaEntity vinculoExistente = vinculoPorId.get(item.vinculoId());
            if (existente == null && vinculoExistente != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "chave idempotente possui registro incompleto");
            }
            if (existente != null) {
                AnuncioMidiaEntity vinculo = validarRepeticaoExistente(
                        anuncio.getId(), item.vinculoId(), vinculoExistente, existente, item.validada());
                verificarArquivoPersistido(storage, existente);
                repetidos.add(resultado(vinculo, existente, true));
            }
        }
        if (repetidos.size() == itens.size()) {
            return new ResultadoUpload(repetidos, true);
        }
        if (!repetidos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "chave idempotente possui lote incompleto");
        }
        if (modo == ModoUpload.PROPRIETARIO) {
            validarCapacidade(itens, capacidade);
        }

        List<AnuncioMidiaEntity> atuais = vinculosAtivos(vinculosBloqueados);
        int proximaOrdem = atuais.stream()
                .map(AnuncioMidiaEntity::getOrdem)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(-1) + 1;
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        List<ItemUpload> criados = new ArrayList<>();
        for (UploadLoteItem item : itens) {
            MidiaValidada validada = item.validada();
            FotoProcessada foto = validada.video() ? null : fotoProcessor.processar(validada);
            byte[] bytesFinais = foto == null ? validada.bytes() : foto.bytes();
            String mimeFinal = foto == null ? validada.mimeType() : foto.mimeType();
            String extensaoFinal = foto == null ? validada.extensao() : foto.extensao();
            Integer larguraFinal = foto == null ? validada.largura() : foto.largura();
            Integer alturaFinal = foto == null ? validada.altura() : foto.altura();
            String shaFinal = foto == null ? validada.sha256() : foto.sha256();
            String storageKey = storageProperties.getPrivateMediaPrefix()
                    + "anuncios/" + anuncio.getId() + "/" + item.arquivoId() + "/"
                    + (foto == null ? "video." + extensaoFinal
                    : "foto-v" + foto.pipelineVersao() + "." + extensaoFinal);

            ArquivoMidiaEntity arquivoEntity = ArquivoMidiaEntity.criarUploadPendente(
                    item.arquivoId(), "R2", storageProperties.getPrivateMediaBucket(), storageKey,
                    modo == ModoUpload.ADMINISTRATIVO ? null : validada.nomeOriginal(),
                    mimeFinal, bytesFinais.length, larguraFinal, alturaFinal, null, shaFinal, agora);
            if (foto != null) {
                arquivoEntity.registrarProcessamento(
                        foto.pipelineVersao(), foto.marcaDaguaVersao(), foto.processadoEm(), foto.sha256Origem());
            }
            arquivoMidiaRepository.save(arquivoEntity);
            arquivoMidiaRepository.flush();

            ObjectWriteResult writeResult = storage.putIfAbsent(
                    StorageArea.PRIVATE_MEDIA, storageKey, bytesFinais, mimeFinal);
            if (writeResult == ObjectWriteResult.CREATED) {
                limparObjetoSeRollback(storage, storageKey);
            }
            verificarObjetoPersistido(storage, storageKey, bytesFinais, mimeFinal,
                    larguraFinal, alturaFinal, foto != null);

            AnuncioMidiaEntity vinculo = AnuncioMidiaEntity.criarUploadPendente(
                    item.vinculoId(), anuncio.getId(), item.arquivoId(),
                    validada.video() ? TipoAnuncioMidia.VIDEO : TipoAnuncioMidia.FOTO,
                    proximaOrdem++, agora);
            anuncioMidiaRepository.save(vinculo);
            criados.add(resultado(vinculo, arquivoEntity, false));
        }
        return new ResultadoUpload(criados, false);
    }

    private List<UploadLoteItem> validarTodos(
            UUID anuncioId, List<MultipartFile> arquivos, String chave, ModoUpload modo) {
        List<UploadLoteItem> itens = new ArrayList<>();
        String namespace = modo == ModoUpload.ADMINISTRATIVO
                ? IDEMPOTENCIA_ADMIN : IDEMPOTENCIA_PROPRIETARIO;
        for (int index = 0; index < arquivos.size(); index++) {
            MidiaValidada validada = uploadValidator.validar(arquivos.get(index));
            if (modo == ModoUpload.ADMINISTRATIVO && validada.video()) {
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "formato de arquivo nao permitido");
            }
            String chaveItem = chave + ":" + index;
            itens.add(new UploadLoteItem(
                    validada,
                    uuidDeterministico(namespace, "arquivo", anuncioId, chaveItem),
                    uuidDeterministico(namespace, "vinculo", anuncioId, chaveItem)));
        }
        return List.copyOf(itens);
    }

    private void validarCapacidade(List<UploadLoteItem> itens, CapacidadeProprietario capacidade) {
        long fotosNovas = itens.stream().filter(item -> !item.validada().video()).count();
        long videosNovos = itens.stream().filter(item -> item.validada().video()).count();
        if (fotosNovas > capacidade.fotosDisponiveis()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "limite de fotos atingido");
        }
        if (videosNovos > capacidade.videosDisponiveis()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, capacidade.videoAtivo()
                    ? "limite de video atingido"
                    : "o beneficio Video e necessario para enviar um video");
        }
    }

    private AnuncioMidiaEntity validarRepeticaoExistente(
            UUID anuncioId,
            UUID vinculoId,
            AnuncioMidiaEntity vinculo,
            ArquivoMidiaEntity arquivo,
            MidiaValidada upload) {
        if (vinculo == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "chave idempotente possui registro incompleto");
        }
        String shaOrigem = arquivo.getSha256Origem() == null ? arquivo.getSha256() : arquivo.getSha256Origem();
        TipoAnuncioMidia tipoEsperado = upload.video() ? TipoAnuncioMidia.VIDEO : TipoAnuncioMidia.FOTO;
        if (!anuncioId.equals(vinculo.getAnuncioId())
                || !arquivo.getId().equals(vinculo.getArquivoMidiaId())
                || vinculo.getTipo() != tipoEsperado
                || !Objects.equals(shaOrigem, upload.sha256())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "chave idempotente reutilizada com outro arquivo");
        }
        List<AnuncioMidiaEntity> associacoes = anuncioMidiaRepository.findByArquivoMidiaId(arquivo.getId());
        if (associacoes == null || associacoes.stream().filter(Objects::nonNull).anyMatch(item ->
                !Objects.equals(item.getId(), vinculoId)
                        || !Objects.equals(item.getAnuncioId(), anuncioId))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "arquivo possui associacao de midia ambigua");
        }
        if (anuncioMidiaRepository.existsDocumentoUsuarioHistoricoPorArquivoId(arquivo.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "arquivo possui associacao historica incompativel");
        }
        return vinculo;
    }

    private List<AnuncioMidiaEntity> vinculosAtivos(List<AnuncioMidiaEntity> encontrados) {
        return encontrados.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getStatus() != StatusAnuncioMidia.REMOVIDA)
                .filter(item -> item.getTipo() != TipoAnuncioMidia.STORY)
                .sorted(Comparator.comparing(AnuncioMidiaEntity::getOrdem,
                        Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    private <T> List<T> exigirLista(List<T> encontrados) {
        if (encontrados == null) {
            throw new IllegalStateException("consulta de upload retornou resultado invalido");
        }
        return encontrados;
    }

    private ObjectStorage storageObrigatorio() {
        ObjectStorage storage = storageProvider.getIfAvailable();
        if (storage == null || !storageProperties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "storage de midia indisponivel");
        }
        return storage;
    }

    private void verificarObjetoPersistido(
            ObjectStorage storage, String key, byte[] esperado, String mimeType,
            Integer largura, Integer altura, boolean foto) {
        StoredObject objeto = storage.get(StorageArea.PRIVATE_MEDIA, key);
        if (objeto == null || objeto.content() == null
                || !Objects.equals(normalizarMime(objeto.contentType()), mimeType)
                || !Objects.equals(sha256(esperado), sha256(objeto.content()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "objeto existente diverge do upload processado");
        }
        if (foto) fotoProcessor.validarDerivado(objeto.content(), mimeType, largura, altura);
    }

    private void verificarArquivoPersistido(ObjectStorage storage, ArquivoMidiaEntity arquivo) {
        if (arquivo.getChaveObjeto() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "registro idempotente sem objeto persistido");
        }
        StorageArea area;
        if (Objects.equals(storageProperties.getPrivateMediaBucket(), arquivo.getBucket())
                && arquivo.getChaveObjeto().startsWith(storageProperties.getPrivateMediaPrefix())) {
            area = StorageArea.PRIVATE_MEDIA;
        } else if (Objects.equals(storageProperties.getPublicMediaBucket(), arquivo.getBucket())
                && arquivo.getChaveObjeto().startsWith(storageProperties.getPublicMediaPrefix())) {
            area = StorageArea.PUBLIC_MEDIA;
        } else {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "registro idempotente aponta para area invalida");
        }
        StoredObject objeto = storage.get(area, arquivo.getChaveObjeto());
        if (objeto == null || objeto.content() == null
                || !Objects.equals(arquivo.getSha256(), sha256(objeto.content()))
                || !Objects.equals(arquivo.getMimeType(), normalizarMime(objeto.contentType()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "registro idempotente diverge do objeto persistido");
        }
        if (arquivo.getPipelineVersao() != null) {
            fotoProcessor.validarDerivado(
                    objeto.content(), arquivo.getMimeType(), arquivo.getLargura(), arquivo.getAltura());
        }
    }

    private String normalizarMime(String value) {
        return value == null ? null : value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private String chaveIdempotencia(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("[A-Za-z0-9._:-]{1,160}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key invalida");
        }
        return normalized;
    }

    private UUID uuidDeterministico(String namespace, String tipo, UUID anuncioId, String key) {
        return UUID.nameUUIDFromBytes(
                (namespace + ":" + tipo + ":" + anuncioId + ":" + key)
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
                        // Objeto privado fica para reconciliacao operacional.
                    }
                }
            }
        });
    }

    private ItemUpload resultado(AnuncioMidiaEntity vinculo, ArquivoMidiaEntity arquivo, boolean idempotente) {
        return new ItemUpload(vinculo.getId(), vinculo.getAnuncioId(), vinculo.getTipo(),
                vinculo.getFinalidade(), vinculo.getOrdem(), vinculo.getStatus(),
                arquivo.getStatusArquivo(), idempotente);
    }

    private enum ModoUpload { PROPRIETARIO, ADMINISTRATIVO }

    private record UploadLoteItem(MidiaValidada validada, UUID arquivoId, UUID vinculoId) { }

    public record CapacidadeProprietario(
            int fotosDisponiveis, int videosDisponiveis, boolean videoAtivo) { }

    public record ResultadoUpload(List<ItemUpload> itens, boolean idempotente) {
        public ResultadoUpload {
            itens = List.copyOf(itens);
        }

        public ItemUpload itemUnico() {
            if (itens.size() != 1) throw new IllegalStateException("resultado de upload unitario invalido");
            return itens.get(0);
        }
    }

    public record ItemUpload(
            UUID midiaId,
            UUID anuncioId,
            TipoAnuncioMidia tipo,
            FinalidadeAnuncioMidia finalidade,
            Integer ordem,
            StatusAnuncioMidia status,
            StatusArquivoMidia statusArquivo,
            boolean idempotente) { }
}
