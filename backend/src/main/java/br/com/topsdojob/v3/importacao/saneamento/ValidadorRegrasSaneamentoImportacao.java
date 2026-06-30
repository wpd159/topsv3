package br.com.topsdojob.v3.importacao.saneamento;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import br.com.topsdojob.v3.importacao.model.CodigoPendenciaImportacao;
import br.com.topsdojob.v3.importacao.model.PendenciaImportacaoDto;
import br.com.topsdojob.v3.importacao.model.TipoEntidadeImportacao;
import br.com.topsdojob.v3.importacao.validacao.ResultadoValidacaoImportacao;

public class ValidadorRegrasSaneamentoImportacao {

    public ResultadoValidacaoImportacao validar(List<RegraSaneamentoImportacaoDto> catalogo) {
        List<PendenciaImportacaoDto> pendencias = new ArrayList<>();
        List<RegraSaneamentoImportacaoDto> regras = List.copyOf(catalogo == null ? List.of() : catalogo);

        for (RegraSaneamentoImportacaoDto regra : regras) {
            validarRegra(regra, pendencias);
        }
        validarEscoposPrincipais(regras, pendencias);

        if (pendencias.isEmpty()) {
            return ResultadoValidacaoImportacao.aprovado();
        }
        return ResultadoValidacaoImportacao.comPendencias(pendencias);
    }

    public ResultadoRegraSaneamentoImportacao validarRegra(RegraSaneamentoImportacaoDto regra) {
        List<PendenciaImportacaoDto> pendencias = new ArrayList<>();
        validarRegra(regra, pendencias);
        if (pendencias.isEmpty()) {
            return ResultadoRegraSaneamentoImportacao.valido(regra == null ? null : regra.codigo());
        }
        return ResultadoRegraSaneamentoImportacao.invalido(regra == null ? null : regra.codigo(), pendencias);
    }

    private void validarRegra(
            RegraSaneamentoImportacaoDto regra,
            List<PendenciaImportacaoDto> pendencias) {
        if (regra == null) {
            pendencias.add(pendencia(
                    "REGRA_NULA",
                    CodigoPendenciaImportacao.SANEAMENTO_REGRA_SEM_CODIGO,
                    "regra nula no catalogo"));
            return;
        }

        String idRegra = regra.codigo() == null ? "REGRA_SEM_CODIGO" : regra.codigo();
        if (regra.codigo() == null) {
            pendencias.add(pendencia(
                    idRegra,
                    CodigoPendenciaImportacao.SANEAMENTO_REGRA_SEM_CODIGO,
                    "regra sem codigo"));
        }
        if (regra.escopo() == null) {
            pendencias.add(pendencia(
                    idRegra,
                    CodigoPendenciaImportacao.SANEAMENTO_REGRA_SEM_ESCOPO,
                    "regra sem escopo"));
        }
        if (regra.bloqueante() && regra.descricaoSanitizada() == null) {
            pendencias.add(pendencia(
                    idRegra,
                    CodigoPendenciaImportacao.SANEAMENTO_BLOQUEANTE_SEM_DESCRICAO,
                    "regra bloqueante sem descricao"));
        }
        if (regra.escopo() == EscopoRegraSaneamentoImportacao.PAGAMENTO && !regra.mencionaEvidencia()) {
            pendencias.add(pendencia(
                    idRegra,
                    CodigoPendenciaImportacao.SANEAMENTO_PAGAMENTO_SEM_EVIDENCIA,
                    "regra de pagamento sem mencao a evidencia"));
        }
        if (regra.documentoPrivado() && regra.publicaDado()) {
            pendencias.add(pendencia(
                    idRegra,
                    CodigoPendenciaImportacao.SANEAMENTO_DOCUMENTO_PRIVADO_PUBLICO,
                    "documento privado marcado como publico"));
        }
        if (regra.tipoValorCreditoIncompativel()) {
            pendencias.add(pendencia(
                    idRegra,
                    CodigoPendenciaImportacao.SANEAMENTO_CREDITO_TIPO_INCOMPATIVEL,
                    "regra de credito com tipo incompativel"));
        }
    }

    private void validarEscoposPrincipais(
            List<RegraSaneamentoImportacaoDto> regras,
            List<PendenciaImportacaoDto> pendencias) {
        Set<EscopoRegraSaneamentoImportacao> escoposPresentes = regras.stream()
                .map(RegraSaneamentoImportacaoDto::escopo)
                .collect(Collectors.toUnmodifiableSet());
        for (EscopoRegraSaneamentoImportacao escopo : EnumSet.allOf(EscopoRegraSaneamentoImportacao.class)) {
            if (!escoposPresentes.contains(escopo)) {
                pendencias.add(pendencia(
                        escopo.name(),
                        CodigoPendenciaImportacao.SANEAMENTO_ESCOPO_PRINCIPAL_SEM_REGRA,
                        "escopo principal sem regra de saneamento"));
            }
        }
    }

    private PendenciaImportacaoDto pendencia(
            String id,
            CodigoPendenciaImportacao codigo,
            String detalhe) {
        return PendenciaImportacaoDto.de(
                TipoEntidadeImportacao.REGRA_SANEAMENTO_IMPORTACAO,
                id,
                codigo,
                detalhe);
    }
}
