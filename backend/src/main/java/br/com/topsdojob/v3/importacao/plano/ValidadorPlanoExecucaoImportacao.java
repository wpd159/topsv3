package br.com.topsdojob.v3.importacao.plano;

import static br.com.topsdojob.v3.importacao.plano.TipoEtapaImportacao.BLOQUEAR_IMPORTACAO_COM_PENDENCIA_CRITICA;
import static br.com.topsdojob.v3.importacao.plano.TipoEtapaImportacao.GERAR_RELATORIO_DRY_RUN;
import static br.com.topsdojob.v3.importacao.plano.TipoEtapaImportacao.PREPARAR_ANUNCIOS;
import static br.com.topsdojob.v3.importacao.plano.TipoEtapaImportacao.PREPARAR_CREDITOS;
import static br.com.topsdojob.v3.importacao.plano.TipoEtapaImportacao.PREPARAR_LOCALIDADES;
import static br.com.topsdojob.v3.importacao.plano.TipoEtapaImportacao.PREPARAR_MIDIAS;
import static br.com.topsdojob.v3.importacao.plano.TipoEtapaImportacao.PREPARAR_PAGAMENTOS;
import static br.com.topsdojob.v3.importacao.plano.TipoEtapaImportacao.PREPARAR_SEO_URLS;
import static br.com.topsdojob.v3.importacao.plano.TipoEtapaImportacao.PREPARAR_USUARIOS;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import br.com.topsdojob.v3.importacao.model.CodigoPendenciaImportacao;
import br.com.topsdojob.v3.importacao.model.PendenciaImportacaoDto;
import br.com.topsdojob.v3.importacao.model.TipoEntidadeImportacao;

public class ValidadorPlanoExecucaoImportacao {

    public ResultadoPlanoImportacaoDto validar(PlanoExecucaoImportacaoDto plano) {
        List<PendenciaImportacaoDto> pendencias = new ArrayList<>();
        List<TipoEtapaImportacao> etapasAptasDryRun = new ArrayList<>();

        if (plano == null) {
            pendencias.add(pendenciaPlano(
                    CodigoPendenciaImportacao.PLANO_SEM_RELATORIO_DRY_RUN,
                    "plano ausente"));
            return ResultadoPlanoImportacaoDto.comPendencias(pendencias, etapasAptasDryRun);
        }

        Map<TipoEtapaImportacao, EtapaImportacaoDto> porTipo = new EnumMap<>(TipoEtapaImportacao.class);
        for (EtapaImportacaoDto etapa : plano.etapas()) {
            validarEtapa(etapa, pendencias, etapasAptasDryRun);
            if (etapa != null && etapa.tipo() != null) {
                porTipo.putIfAbsent(etapa.tipo(), etapa);
            }
        }

        validarDependenciasDeclaradas(plano.etapas(), porTipo, pendencias);
        validarDependenciasCriticas(porTipo, pendencias);
        validarGatesFinais(porTipo, pendencias);

        if (pendencias.isEmpty()) {
            return ResultadoPlanoImportacaoDto.aprovado(etapasAptasDryRun);
        }
        return ResultadoPlanoImportacaoDto.comPendencias(pendencias, etapasAptasDryRun);
    }

    private void validarEtapa(
            EtapaImportacaoDto etapa,
            List<PendenciaImportacaoDto> pendencias,
            List<TipoEtapaImportacao> etapasAptasDryRun) {
        if (etapa == null) {
            pendencias.add(pendenciaEtapa(
                    "ETAPA_NULA",
                    CodigoPendenciaImportacao.PLANO_ETAPA_SEM_TIPO,
                    "etapa nula no plano"));
            return;
        }

        String idEtapa = idEtapa(etapa);
        if (etapa.tipo() == null) {
            pendencias.add(pendenciaEtapa(
                    idEtapa,
                    CodigoPendenciaImportacao.PLANO_ETAPA_SEM_TIPO,
                    "etapa sem tipo"));
        }
        if (etapa.ordem() == null || etapa.ordem() <= 0) {
            pendencias.add(pendenciaEtapa(
                    idEtapa,
                    CodigoPendenciaImportacao.PLANO_ETAPA_SEM_ORDEM,
                    "etapa sem ordem positiva"));
        }
        if (etapa.critica() && etapa.descricaoSanitizada() == null) {
            pendencias.add(pendenciaEtapa(
                    idEtapa,
                    CodigoPendenciaImportacao.PLANO_ETAPA_CRITICA_SEM_DESCRICAO,
                    "etapa critica sem descricao"));
        }
        if (etapa.status() == StatusEtapaImportacao.APTO_DRY_RUN && etapa.tipo() != null) {
            etapasAptasDryRun.add(etapa.tipo());
        }
    }

    private void validarDependenciasDeclaradas(
            List<EtapaImportacaoDto> etapas,
            Map<TipoEtapaImportacao, EtapaImportacaoDto> porTipo,
            List<PendenciaImportacaoDto> pendencias) {
        for (EtapaImportacaoDto etapa : etapas) {
            if (etapa == null) {
                continue;
            }
            for (DependenciaEtapaImportacaoDto dependencia : etapa.dependencias()) {
                if (dependencia == null
                        || dependencia.tipoEtapa() == null
                        || !porTipo.containsKey(dependencia.tipoEtapa())) {
                    pendencias.add(pendenciaEtapa(
                            idEtapa(etapa),
                            CodigoPendenciaImportacao.PLANO_DEPENDENCIA_INEXISTENTE,
                            "dependencia declarada ausente no plano"));
                }
            }
        }
    }

    private void validarDependenciasCriticas(
            Map<TipoEtapaImportacao, EtapaImportacaoDto> porTipo,
            List<PendenciaImportacaoDto> pendencias) {
        EtapaImportacaoDto anuncios = porTipo.get(PREPARAR_ANUNCIOS);
        if (anuncios != null
                && (!temDependencia(anuncios, PREPARAR_USUARIOS)
                || !temDependencia(anuncios, PREPARAR_LOCALIDADES))) {
            pendencias.add(pendenciaEtapa(
                    PREPARAR_ANUNCIOS.name(),
                    CodigoPendenciaImportacao.PLANO_ANUNCIO_SEM_DEPENDENCIA_USUARIO_LOCALIDADE,
                    "anuncio precisa depender de usuario e localidade"));
        }

        EtapaImportacaoDto midias = porTipo.get(PREPARAR_MIDIAS);
        if (midias != null && !temDependencia(midias, PREPARAR_ANUNCIOS)) {
            pendencias.add(pendenciaEtapa(
                    PREPARAR_MIDIAS.name(),
                    CodigoPendenciaImportacao.PLANO_MIDIA_SEM_DEPENDENCIA_ANUNCIO,
                    "midia precisa depender de anuncio"));
        }

        EtapaImportacaoDto creditos = porTipo.get(PREPARAR_CREDITOS);
        if (creditos != null
                && !temDependencia(creditos, PREPARAR_PAGAMENTOS)
                && !possuiAlternativaAuditavel(creditos)) {
            pendencias.add(pendenciaEtapa(
                    PREPARAR_CREDITOS.name(),
                    CodigoPendenciaImportacao.PLANO_CREDITO_SEM_DEPENDENCIA_PAGAMENTO,
                    "credito precisa depender de pagamento ou alternativa auditavel"));
        }

        EtapaImportacaoDto seoUrls = porTipo.get(PREPARAR_SEO_URLS);
        if (seoUrls != null
                && (!temDependencia(seoUrls, PREPARAR_ANUNCIOS)
                || !temDependencia(seoUrls, PREPARAR_LOCALIDADES))) {
            pendencias.add(pendenciaEtapa(
                    PREPARAR_SEO_URLS.name(),
                    CodigoPendenciaImportacao.PLANO_SEO_URL_SEM_DEPENDENCIA_ANUNCIO_LOCALIDADE,
                    "SEO e URL precisam depender de anuncio e localidade"));
        }
    }

    private void validarGatesFinais(
            Map<TipoEtapaImportacao, EtapaImportacaoDto> porTipo,
            List<PendenciaImportacaoDto> pendencias) {
        if (!porTipo.containsKey(GERAR_RELATORIO_DRY_RUN)) {
            pendencias.add(pendenciaPlano(
                    CodigoPendenciaImportacao.PLANO_SEM_RELATORIO_DRY_RUN,
                    "plano sem etapa de relatorio dry-run"));
        }
        if (!porTipo.containsKey(BLOQUEAR_IMPORTACAO_COM_PENDENCIA_CRITICA)) {
            pendencias.add(pendenciaPlano(
                    CodigoPendenciaImportacao.PLANO_SEM_BLOQUEIO_PENDENCIA_CRITICA,
                    "plano sem etapa de bloqueio por pendencia critica"));
        }
    }

    private boolean temDependencia(EtapaImportacaoDto etapa, TipoEtapaImportacao tipo) {
        return etapa.dependencias().stream()
                .anyMatch(dependencia -> dependencia != null && dependencia.tipoEtapa() == tipo);
    }

    private boolean possuiAlternativaAuditavel(EtapaImportacaoDto etapa) {
        return etapa.dependencias().stream()
                .anyMatch(dependencia -> dependencia != null && dependencia.possuiAlternativaAuditavel());
    }

    private String idEtapa(EtapaImportacaoDto etapa) {
        if (etapa.tipo() != null) {
            return etapa.tipo().name();
        }
        if (etapa.ordem() != null) {
            return "ORDEM_" + etapa.ordem();
        }
        return "ETAPA_SEM_TIPO";
    }

    private PendenciaImportacaoDto pendenciaPlano(
            CodigoPendenciaImportacao codigo,
            String detalhe) {
        return PendenciaImportacaoDto.de(
                TipoEntidadeImportacao.PLANO_IMPORTACAO,
                "plano-execucao-importacao",
                codigo,
                detalhe);
    }

    private PendenciaImportacaoDto pendenciaEtapa(
            String id,
            CodigoPendenciaImportacao codigo,
            String detalhe) {
        return PendenciaImportacaoDto.de(
                TipoEntidadeImportacao.ETAPA_PLANO_IMPORTACAO,
                id,
                codigo,
                detalhe);
    }
}
