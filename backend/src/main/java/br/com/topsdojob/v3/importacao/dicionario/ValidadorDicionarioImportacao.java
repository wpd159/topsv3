package br.com.topsdojob.v3.importacao.dicionario;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import br.com.topsdojob.v3.importacao.model.CodigoPendenciaImportacao;
import br.com.topsdojob.v3.importacao.model.PendenciaImportacaoDto;
import br.com.topsdojob.v3.importacao.model.TipoEntidadeImportacao;
import br.com.topsdojob.v3.importacao.pacote.TipoArquivoPacoteImportacao;
import br.com.topsdojob.v3.importacao.validacao.ResultadoValidacaoImportacao;

public class ValidadorDicionarioImportacao {

    public ResultadoValidacaoImportacao validar(List<DicionarioArquivoImportacaoDto> catalogo) {
        List<PendenciaImportacaoDto> pendencias = new ArrayList<>();
        Map<TipoArquivoPacoteImportacao, DicionarioArquivoImportacaoDto> porTipo =
                new EnumMap<>(TipoArquivoPacoteImportacao.class);

        for (DicionarioArquivoImportacaoDto dicionario : catalogo == null ? List.<DicionarioArquivoImportacaoDto>of() : catalogo) {
            if (dicionario.tipoArquivo() != null) {
                porTipo.putIfAbsent(dicionario.tipoArquivo(), dicionario);
            }
            validarDicionario(dicionario, pendencias);
        }

        for (TipoArquivoPacoteImportacao tipo : TipoArquivoPacoteImportacao.values()) {
            if (!porTipo.containsKey(tipo)) {
                pendencias.add(pendenciaDicionario(
                        tipo.name(),
                        CodigoPendenciaImportacao.DICIONARIO_TIPO_ARQUIVO_AUSENTE,
                        "tipo de arquivo sem dicionario estrutural"));
            }
        }

        if (pendencias.isEmpty()) {
            return ResultadoValidacaoImportacao.aprovado();
        }
        return ResultadoValidacaoImportacao.comPendencias(pendencias);
    }

    private void validarDicionario(
            DicionarioArquivoImportacaoDto dicionario,
            List<PendenciaImportacaoDto> pendencias) {
        if (dicionario == null) {
            pendencias.add(pendenciaDicionario(
                    "DICIONARIO_NULO",
                    CodigoPendenciaImportacao.DICIONARIO_TIPO_ARQUIVO_AUSENTE,
                    "dicionario nulo no catalogo"));
            return;
        }

        String idDicionario = dicionario.tipoArquivo() == null ? "TIPO_NAO_INFORMADO" : dicionario.tipoArquivo().name();
        if (dicionario.campos().isEmpty()) {
            pendencias.add(pendenciaDicionario(
                    idDicionario,
                    CodigoPendenciaImportacao.DICIONARIO_ARQUIVO_SEM_CAMPOS,
                    "arquivo sem campos declarados"));
            return;
        }

        for (CampoPacoteImportacaoDto campo : dicionario.campos()) {
            validarCampo(idDicionario, campo, pendencias);
        }
    }

    private void validarCampo(
            String idDicionario,
            CampoPacoteImportacaoDto campo,
            List<PendenciaImportacaoDto> pendencias) {
        if (campo == null) {
            pendencias.add(pendenciaCampo(
                    idDicionario,
                    CodigoPendenciaImportacao.DICIONARIO_CAMPO_SEM_TIPO,
                    "campo nulo no dicionario"));
            return;
        }

        String idCampo = idDicionario + ":" + (campo.nomeLogico() == null ? "CAMPO_SEM_NOME" : campo.nomeLogico());
        if (campo.obrigatorio() && campo.nomeLogico() == null) {
            pendencias.add(pendenciaCampo(
                    idCampo,
                    CodigoPendenciaImportacao.DICIONARIO_CAMPO_OBRIGATORIO_SEM_NOME,
                    "campo obrigatorio sem nome logico"));
        }
        if (campo.tipo() == null) {
            pendencias.add(pendenciaCampo(
                    idCampo,
                    CodigoPendenciaImportacao.DICIONARIO_CAMPO_SEM_TIPO,
                    "campo sem tipo estrutural"));
            return;
        }
        if (campo.obrigatoriedade() == null) {
            pendencias.add(pendenciaCampo(
                    idCampo,
                    CodigoPendenciaImportacao.DICIONARIO_CAMPO_SEM_OBRIGATORIEDADE,
                    "campo sem obrigatoriedade"));
        }
        if (campo.tipo().exigeClassificacaoSensivel() && campo.sensibilidade() == null) {
            pendencias.add(pendenciaCampo(
                    idCampo,
                    CodigoPendenciaImportacao.DICIONARIO_CAMPO_SENSIVEL_SEM_CLASSIFICACAO,
                    "campo sensivel sem classificacao"));
        }
        if (campo.sensibilidade() == SensibilidadeCampoImportacao.FINANCEIRO && !tipoFinanceiroCompativel(campo)) {
            pendencias.add(pendenciaCampo(
                    idCampo,
                    CodigoPendenciaImportacao.DICIONARIO_CAMPO_FINANCEIRO_TIPO_INCOMPATIVEL,
                    "campo financeiro com tipo incompativel"));
        }
        if (campoCredito(campo) && !tipoCreditoCompativel(campo.tipo())) {
            pendencias.add(pendenciaCampo(
                    idCampo,
                    CodigoPendenciaImportacao.DICIONARIO_CAMPO_CREDITO_TIPO_INCOMPATIVEL,
                    "campo de credito deve usar inteiro/bigint conceitual, nunca decimal ou float"));
        }
        if (campo.tipo() == TipoCampoImportacao.DOCUMENTO
                && campo.sensibilidade() == SensibilidadeCampoImportacao.PUBLICO) {
            pendencias.add(pendenciaCampo(
                    idCampo,
                    CodigoPendenciaImportacao.DICIONARIO_DOCUMENTO_PUBLICO,
                    "documento privado nao pode ser publico"));
        }
    }

    private boolean tipoFinanceiroCompativel(CampoPacoteImportacaoDto campo) {
        return campo.tipo() == TipoCampoImportacao.DINHEIRO
                || campo.tipo() == TipoCampoImportacao.NUMERO_DECIMAL
                || campo.tipo() == TipoCampoImportacao.CREDITO
                || campo.tipo() == TipoCampoImportacao.IDENTIFICADOR_LEGADO
                || campo.tipo() == TipoCampoImportacao.ENUM
                || campo.tipo() == TipoCampoImportacao.TEXTO
                || campo.tipo() == TipoCampoImportacao.DATA_HORA;
    }

    private boolean campoCredito(CampoPacoteImportacaoDto campo) {
        return campo.tipo() == TipoCampoImportacao.CREDITO
                || (campo.nomeLogico() != null && campo.nomeLogico().toLowerCase().contains("credito"));
    }

    private boolean tipoCreditoCompativel(TipoCampoImportacao tipo) {
        return tipo == TipoCampoImportacao.CREDITO
                || tipo == TipoCampoImportacao.NUMERO_INTEIRO
                || tipo == TipoCampoImportacao.IDENTIFICADOR_LEGADO
                || tipo == TipoCampoImportacao.ENUM;
    }

    private PendenciaImportacaoDto pendenciaDicionario(
            String id,
            CodigoPendenciaImportacao codigo,
            String detalhe) {
        return PendenciaImportacaoDto.de(
                TipoEntidadeImportacao.DICIONARIO_IMPORTACAO,
                id,
                codigo,
                detalhe);
    }

    private PendenciaImportacaoDto pendenciaCampo(
            String id,
            CodigoPendenciaImportacao codigo,
            String detalhe) {
        return PendenciaImportacaoDto.de(
                TipoEntidadeImportacao.CAMPO_DICIONARIO_IMPORTACAO,
                id,
                codigo,
                detalhe);
    }
}
