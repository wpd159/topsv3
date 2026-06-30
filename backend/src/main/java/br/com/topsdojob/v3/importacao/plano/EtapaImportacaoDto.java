package br.com.topsdojob.v3.importacao.plano;

import java.util.List;

public record EtapaImportacaoDto(
        TipoEtapaImportacao tipo,
        Integer ordem,
        StatusEtapaImportacao status,
        CriticidadeEtapaImportacao criticidade,
        String descricaoSanitizada,
        List<DependenciaEtapaImportacaoDto> dependencias,
        List<String> criteriosBloqueio,
        List<String> criteriosSucessoParcial,
        boolean bloqueiaImportacaoReal) {

    public EtapaImportacaoDto {
        descricaoSanitizada = normalizarOpcional(descricaoSanitizada);
        dependencias = List.copyOf(dependencias == null ? List.of() : dependencias);
        criteriosBloqueio = copiarTextos(criteriosBloqueio);
        criteriosSucessoParcial = copiarTextos(criteriosSucessoParcial);
    }

    public boolean critica() {
        return criticidade == CriticidadeEtapaImportacao.BLOQUEANTE;
    }

    private static List<String> copiarTextos(List<String> valores) {
        return (valores == null ? List.<String>of() : valores).stream()
                .map(EtapaImportacaoDto::normalizarOpcional)
                .filter(valor -> valor != null)
                .toList();
    }

    private static String normalizarOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }
}
