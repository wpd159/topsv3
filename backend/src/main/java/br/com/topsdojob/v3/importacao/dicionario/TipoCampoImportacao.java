package br.com.topsdojob.v3.importacao.dicionario;

import java.util.EnumSet;
import java.util.Set;

public enum TipoCampoImportacao {
    TEXTO,
    NUMERO_INTEIRO,
    NUMERO_DECIMAL,
    BOOLEANO,
    DATA,
    DATA_HORA,
    UUID,
    JSON,
    ENUM,
    CHECKSUM,
    IDENTIFICADOR_LEGADO,
    REFERENCIA_MIDIA,
    URL,
    EMAIL,
    TELEFONE,
    DOCUMENTO,
    DINHEIRO,
    CREDITO;

    private static final Set<TipoCampoImportacao> SENSIVEIS_POR_NATUREZA = EnumSet.of(
            EMAIL,
            TELEFONE,
            DOCUMENTO,
            DINHEIRO,
            CREDITO);

    public boolean exigeClassificacaoSensivel() {
        return SENSIVEIS_POR_NATUREZA.contains(this);
    }
}
