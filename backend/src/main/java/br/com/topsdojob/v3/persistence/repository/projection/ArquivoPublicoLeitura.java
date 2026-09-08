package br.com.topsdojob.v3.persistence.repository.projection;

import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusDerivadoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoDerivadoMidia;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Public read value. SEO uses only free-media fields; presentation preserves persisted state. */
public record ArquivoPublicoLeitura(UUID id, String sha256, StatusArquivoMidia statusArquivo,
        String storageProvider, String bucket, String chaveObjeto, String mimeType,
        Integer largura, Integer altura, TipoDerivadoMidia previewRestritoTipo,
        String previewRestritoChave, String previewRestritoPipelineVersao,
        StatusDerivadoMidia previewRestritoStatus, OffsetDateTime previewRestritoConfirmadoEm) {
    /** Narrow SEO projection deliberately does not fetch dispensable preview state. */
    public ArquivoPublicoLeitura(UUID id, String sha256, StatusArquivoMidia statusArquivo,
            String storageProvider, String bucket, String chaveObjeto, String mimeType,
            Integer largura, Integer altura) {
        this(id, sha256, statusArquivo, storageProvider, bucket, chaveObjeto, mimeType,
                largura, altura, null, null, null, null, null);
    }

    public static ArquivoPublicoLeitura de(ArquivoMidiaEntity item) {
        return item == null ? null : new ArquivoPublicoLeitura(item.getId(), item.getSha256(),
                item.getStatusArquivo(), item.getStorageProvider(), item.getBucket(),
                item.getChaveObjeto(), item.getMimeType(), item.getLargura(), item.getAltura(),
                item.getPreviewRestritoTipo(), item.getPreviewRestritoChave(),
                item.getPreviewRestritoPipelineVersao(), item.getPreviewRestritoStatus(),
                item.getPreviewRestritoConfirmadoEm());
    }

    public boolean previewRestritoDisponivel() {
        return previewRestritoStatus == StatusDerivadoMidia.DISPONIVEL
                && previewRestritoTipo == TipoDerivadoMidia.PREVIEW_RESTRITO
                && previewRestritoChave != null && !previewRestritoChave.isBlank()
                && previewRestritoPipelineVersao != null && !previewRestritoPipelineVersao.isBlank()
                && previewRestritoConfirmadoEm != null;
    }
}
