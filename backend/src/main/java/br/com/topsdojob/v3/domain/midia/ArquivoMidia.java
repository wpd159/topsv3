package br.com.topsdojob.v3.domain.midia;

import br.com.topsdojob.v3.domain.shared.ClassificacaoConteudo;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ArquivoMidia(
    UUID id,
    String storageProvider,
    String bucket,
    String chaveObjeto,
    String nomeOriginal,
    String mimeType,
    Long tamanhoBytes,
    Integer largura,
    Integer altura,
    Integer duracaoMs,
    String sha256,
    String etag,
    MidiaTipos.StatusArquivo statusArquivo,
    ClassificacaoConteudo classificacaoConteudo,
    OffsetDateTime criadoEm) {
  public static final String TABELA = "arquivo_midia";
}
