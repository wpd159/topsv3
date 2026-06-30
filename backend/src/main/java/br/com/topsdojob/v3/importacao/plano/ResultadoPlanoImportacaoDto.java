package br.com.topsdojob.v3.importacao.plano;

import java.util.List;

import br.com.topsdojob.v3.importacao.model.PendenciaImportacaoDto;

public record ResultadoPlanoImportacaoDto(
        boolean valido,
        List<PendenciaImportacaoDto> pendencias,
        List<TipoEtapaImportacao> etapasAptasDryRun) {

    public ResultadoPlanoImportacaoDto {
        pendencias = List.copyOf(pendencias == null ? List.of() : pendencias);
        etapasAptasDryRun = List.copyOf(etapasAptasDryRun == null ? List.of() : etapasAptasDryRun);
    }

    public static ResultadoPlanoImportacaoDto aprovado(List<TipoEtapaImportacao> etapasAptasDryRun) {
        return new ResultadoPlanoImportacaoDto(true, List.of(), etapasAptasDryRun);
    }

    public static ResultadoPlanoImportacaoDto comPendencias(
            List<PendenciaImportacaoDto> pendencias,
            List<TipoEtapaImportacao> etapasAptasDryRun) {
        return new ResultadoPlanoImportacaoDto(false, pendencias, etapasAptasDryRun);
    }
}
