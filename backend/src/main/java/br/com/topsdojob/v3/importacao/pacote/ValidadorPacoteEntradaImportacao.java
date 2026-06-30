package br.com.topsdojob.v3.importacao.pacote;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import br.com.topsdojob.v3.importacao.model.CodigoPendenciaImportacao;
import br.com.topsdojob.v3.importacao.model.PendenciaImportacaoDto;
import br.com.topsdojob.v3.importacao.model.TipoEntidadeImportacao;

public class ValidadorPacoteEntradaImportacao {

    public ResultadoValidacaoPacoteImportacao validar(PacoteEntradaImportacaoDto pacote) {
        List<PendenciaImportacaoDto> pendencias = new ArrayList<>();
        if (pacote == null) {
            pendencias.add(pendenciaPacote(CodigoPendenciaImportacao.PACOTE_DESCRITOR_AUSENTE, "descritor ausente"));
            return ResultadoValidacaoPacoteImportacao.comPendencias(pendencias);
        }

        validarCabecalho(pacote, pendencias);
        validarArquivos(pacote.arquivos(), pendencias);

        if (pendencias.isEmpty()) {
            return ResultadoValidacaoPacoteImportacao.aprovado();
        }
        return ResultadoValidacaoPacoteImportacao.comPendencias(pendencias);
    }

    private void validarCabecalho(
            PacoteEntradaImportacaoDto pacote,
            List<PendenciaImportacaoDto> pendencias) {
        if (pacote.identificadorLogico() == null) {
            pendencias.add(pendenciaPacote(
                    CodigoPendenciaImportacao.PACOTE_SEM_IDENTIFICADOR,
                    "identificador logico ausente"));
        }
        if (pacote.versao() == null) {
            pendencias.add(pendenciaPacote(
                    CodigoPendenciaImportacao.PACOTE_SEM_VERSAO,
                    "versao do pacote ausente"));
        }
        if (pacote.extraidoEm() == null) {
            pendencias.add(pendenciaPacote(
                    CodigoPendenciaImportacao.PACOTE_SEM_EXTRACAO_DECLARADA,
                    "data/hora declarada de extracao ausente"));
        }
        if (pacote.origem() == null) {
            pendencias.add(pendenciaPacote(
                    CodigoPendenciaImportacao.PACOTE_SEM_ORIGEM,
                    "origem do pacote ausente"));
        }
    }

    private void validarArquivos(
            List<ArquivoPacoteImportacaoDto> arquivos,
            List<PendenciaImportacaoDto> pendencias) {
        if (arquivos.isEmpty()) {
            pendencias.add(pendenciaPacote(
                    CodigoPendenciaImportacao.PACOTE_SEM_ARQUIVOS,
                    "nenhum arquivo declarado"));
            return;
        }

        Map<TipoArquivoPacoteImportacao, ArquivoPacoteImportacaoDto> porTipo =
                new EnumMap<>(TipoArquivoPacoteImportacao.class);
        for (ArquivoPacoteImportacaoDto arquivo : arquivos) {
            ArquivoPacoteImportacaoDto anterior = porTipo.putIfAbsent(arquivo.tipo(), arquivo);
            if (anterior != null) {
                pendencias.add(pendenciaArquivo(
                        arquivo.tipo(),
                        CodigoPendenciaImportacao.PACOTE_ARQUIVO_DUPLICADO,
                        "tipo de arquivo duplicado no descritor"));
            }
            validarChecksum(arquivo, pendencias);
        }

        Set<TipoArquivoPacoteImportacao> obrigatorios = TipoArquivoPacoteImportacao.obrigatorios();
        for (TipoArquivoPacoteImportacao tipo : obrigatorios) {
            ArquivoPacoteImportacaoDto arquivo = porTipo.get(tipo);
            if (arquivo == null || arquivo.declaradoAusente()) {
                pendencias.add(pendenciaArquivo(
                        tipo,
                        CodigoPendenciaImportacao.PACOTE_ARQUIVO_OBRIGATORIO_AUSENTE,
                        "arquivo obrigatorio ausente"));
            }
        }
    }

    private void validarChecksum(
            ArquivoPacoteImportacaoDto arquivo,
            List<PendenciaImportacaoDto> pendencias) {
        if (arquivo.possuiChecksum()) {
            return;
        }
        CodigoPendenciaImportacao codigo = arquivo.tipo().exigeChecksum()
                ? CodigoPendenciaImportacao.PACOTE_CHECKSUM_OBRIGATORIO_AUSENTE
                : CodigoPendenciaImportacao.PACOTE_CHECKSUM_AUSENTE;
        pendencias.add(pendenciaArquivo(arquivo.tipo(), codigo, "checksum ausente no descritor"));
    }

    private PendenciaImportacaoDto pendenciaPacote(
            CodigoPendenciaImportacao codigo,
            String detalheSanitizado) {
        return PendenciaImportacaoDto.de(
                TipoEntidadeImportacao.PACOTE_IMPORTACAO,
                "pacote-entrada",
                codigo,
                detalheSanitizado);
    }

    private PendenciaImportacaoDto pendenciaArquivo(
            TipoArquivoPacoteImportacao tipo,
            CodigoPendenciaImportacao codigo,
            String detalheSanitizado) {
        return PendenciaImportacaoDto.de(
                TipoEntidadeImportacao.ARQUIVO_PACOTE_IMPORTACAO,
                tipo.name(),
                codigo,
                detalheSanitizado);
    }
}
