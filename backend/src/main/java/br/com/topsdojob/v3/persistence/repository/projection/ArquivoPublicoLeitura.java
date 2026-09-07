package br.com.topsdojob.v3.persistence.repository.projection;

import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import java.util.UUID;

/** Identity-only constructor for collection; full public-media fields for the fresh final read. */
public record ArquivoPublicoLeitura(UUID id, String sha256, StatusArquivoMidia statusArquivo,
        String storageProvider, String bucket, String chaveObjeto, String mimeType,
        Integer largura, Integer altura) {
    public ArquivoPublicoLeitura(UUID id, String sha256, StatusArquivoMidia statusArquivo) {
        this(id, sha256, statusArquivo, null, null, null, null, null, null);
    }

    public static ArquivoPublicoLeitura de(ArquivoMidiaEntity item) {
        return item == null ? null : new ArquivoPublicoLeitura(item.getId(), item.getSha256(),
                item.getStatusArquivo(), item.getStorageProvider(), item.getBucket(),
                item.getChaveObjeto(), item.getMimeType(), item.getLargura(), item.getAltura());
    }
}
