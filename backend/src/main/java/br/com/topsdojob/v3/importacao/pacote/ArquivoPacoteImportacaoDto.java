package br.com.topsdojob.v3.importacao.pacote;

import java.util.Objects;
import java.util.regex.Pattern;

public record ArquivoPacoteImportacaoDto(
        TipoArquivoPacoteImportacao tipo,
        String nomeLogico,
        StatusArquivoPacoteImportacao status,
        String caminhoDeclaradoSanitizado,
        String formatoDeclarado,
        Long tamanhoBytesDeclarado,
        String checksumSha256,
        String versaoContrato) {

    private static final Pattern SHA_256 = Pattern.compile("^[a-fA-F0-9]{64}$");

    public ArquivoPacoteImportacaoDto {
        Objects.requireNonNull(tipo, "tipo deve ser informado");
        if (status == null) {
            status = StatusArquivoPacoteImportacao.DECLARADO;
        }
        nomeLogico = normalizarOpcional(nomeLogico);
        if (nomeLogico == null) {
            nomeLogico = tipo.nomeLogicoPadrao();
        }
        caminhoDeclaradoSanitizado = normalizarOpcional(caminhoDeclaradoSanitizado);
        formatoDeclarado = normalizarOpcional(formatoDeclarado);
        checksumSha256 = normalizarOpcional(checksumSha256);
        versaoContrato = normalizarOpcional(versaoContrato);
        if (tamanhoBytesDeclarado != null && tamanhoBytesDeclarado < 0) {
            throw new IllegalArgumentException("tamanhoBytesDeclarado nao pode ser negativo");
        }
        if (checksumSha256 != null && !SHA_256.matcher(checksumSha256).matches()) {
            throw new IllegalArgumentException("checksumSha256 deve ter 64 caracteres hexadecimais");
        }
    }

    public boolean possuiChecksum() {
        return checksumSha256 != null;
    }

    public boolean declaradoAusente() {
        return status == StatusArquivoPacoteImportacao.AUSENTE;
    }

    private static String normalizarOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }
}
