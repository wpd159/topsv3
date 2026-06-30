package br.com.topsdojob.v3.importacao.plano;

import static br.com.topsdojob.v3.importacao.plano.CriticidadeEtapaImportacao.ALERTA;
import static br.com.topsdojob.v3.importacao.plano.CriticidadeEtapaImportacao.BLOQUEANTE;
import static br.com.topsdojob.v3.importacao.plano.StatusEtapaImportacao.APTO_DRY_RUN;
import static br.com.topsdojob.v3.importacao.plano.StatusEtapaImportacao.BLOQUEADO;
import static br.com.topsdojob.v3.importacao.plano.StatusEtapaImportacao.PLANEJADO;
import static br.com.topsdojob.v3.importacao.plano.TipoEtapaImportacao.BLOQUEAR_IMPORTACAO_COM_PENDENCIA_CRITICA;
import static br.com.topsdojob.v3.importacao.plano.TipoEtapaImportacao.GERAR_RELATORIO_DRY_RUN;
import static br.com.topsdojob.v3.importacao.plano.TipoEtapaImportacao.PREPARAR_ANUNCIOS;
import static br.com.topsdojob.v3.importacao.plano.TipoEtapaImportacao.PREPARAR_BANNERS;
import static br.com.topsdojob.v3.importacao.plano.TipoEtapaImportacao.PREPARAR_COMERCIAL_SUPORTE;
import static br.com.topsdojob.v3.importacao.plano.TipoEtapaImportacao.PREPARAR_CREDITOS;
import static br.com.topsdojob.v3.importacao.plano.TipoEtapaImportacao.PREPARAR_DOCUMENTOS_PRIVADOS;
import static br.com.topsdojob.v3.importacao.plano.TipoEtapaImportacao.PREPARAR_LOCALIDADES;
import static br.com.topsdojob.v3.importacao.plano.TipoEtapaImportacao.PREPARAR_METRICAS;
import static br.com.topsdojob.v3.importacao.plano.TipoEtapaImportacao.PREPARAR_MIDIAS;
import static br.com.topsdojob.v3.importacao.plano.TipoEtapaImportacao.PREPARAR_PAGAMENTOS;
import static br.com.topsdojob.v3.importacao.plano.TipoEtapaImportacao.PREPARAR_PREMIUM_BENEFICIOS;
import static br.com.topsdojob.v3.importacao.plano.TipoEtapaImportacao.PREPARAR_SEO_URLS;
import static br.com.topsdojob.v3.importacao.plano.TipoEtapaImportacao.PREPARAR_USUARIOS;
import static br.com.topsdojob.v3.importacao.plano.TipoEtapaImportacao.VALIDAR_DICIONARIO_CAMPOS;
import static br.com.topsdojob.v3.importacao.plano.TipoEtapaImportacao.VALIDAR_PACOTE_ENTRADA;
import static br.com.topsdojob.v3.importacao.plano.TipoEtapaImportacao.VALIDAR_REGRAS_SANEAMENTO;

import java.util.List;

public final class CatalogoPlanoExecucaoImportacao {

    private static final List<EtapaImportacaoDto> ETAPAS = List.of(
            etapa(VALIDAR_PACOTE_ENTRADA, 1, APTO_DRY_RUN, BLOQUEANTE,
                    "Validar descritor estrutural do pacote de entrada sem abrir dump ou arquivo real.",
                    List.of(),
                    List.of("descritor ausente", "arquivo obrigatorio sem declaracao estrutural"),
                    List.of("pacote estrutural catalogado")),
            etapa(VALIDAR_DICIONARIO_CAMPOS, 2, APTO_DRY_RUN, BLOQUEANTE,
                    "Validar dicionario estrutural dos campos e classificacoes sensiveis.",
                    List.of(dep(VALIDAR_PACOTE_ENTRADA, "o dicionario depende do pacote declarado")),
                    List.of("campo obrigatorio sem tipo", "documento privado classificado como publico"),
                    List.of("campos mapeados por tipo logico")),
            etapa(VALIDAR_REGRAS_SANEAMENTO, 3, APTO_DRY_RUN, BLOQUEANTE,
                    "Validar regras de saneamento declarativas antes de qualquer transformacao futura.",
                    List.of(dep(VALIDAR_DICIONARIO_CAMPOS, "regras usam os tipos do dicionario")),
                    List.of("regra bloqueante sem descricao", "pagamento sem evidencia"),
                    List.of("regras estruturais aptas para dry-run")),
            etapa(PREPARAR_LOCALIDADES, 4, PLANEJADO, BLOQUEANTE,
                    "Planejar UF, cidade e bairro preservando hierarquia e rotas publicas.",
                    List.of(dep(VALIDAR_REGRAS_SANEAMENTO, "localidade precisa das regras de saneamento")),
                    List.of("localidade sem evidencia suficiente"),
                    List.of("hierarquia local preparada")),
            etapa(PREPARAR_USUARIOS, 5, PLANEJADO, BLOQUEANTE,
                    "Planejar usuarios, contatos e documentos privados sem expor dados sensiveis.",
                    List.of(dep(VALIDAR_REGRAS_SANEAMENTO, "usuario precisa das regras de saneamento")),
                    List.of("usuario duplicado suspeito", "documento privado publico"),
                    List.of("usuarios preparados para associacao futura")),
            etapa(PREPARAR_ANUNCIOS, 6, PLANEJADO, BLOQUEANTE,
                    "Planejar anuncios depois de usuarios e localidades, preservando status, contato e SEO.",
                    List.of(
                            dep(PREPARAR_USUARIOS, "anuncio depende da anunciante"),
                            dep(PREPARAR_LOCALIDADES, "anuncio depende da localidade publica")),
                    List.of("anuncio sem anunciante", "anuncio sem cidade"),
                    List.of("anuncios aptos para verificacao futura")),
            etapa(PREPARAR_MIDIAS, 7, PLANEJADO, BLOQUEANTE,
                    "Planejar midias somente apos anuncios e separar documento privado de galeria publica.",
                    List.of(dep(PREPARAR_ANUNCIOS, "midia publica depende do anuncio")),
                    List.of("midia sem manifesto", "documento privado tratado como midia publica"),
                    List.of("manifesto futuro de midia definido")),
            etapa(PREPARAR_DOCUMENTOS_PRIVADOS, 8, PLANEJADO, BLOQUEANTE,
                    "Planejar documentos privados como nao publicaveis, com acesso auditavel e retencao futura.",
                    List.of(
                            dep(PREPARAR_USUARIOS, "documento privado depende da anunciante"),
                            dep(PREPARAR_ANUNCIOS, "retencao pode depender de anuncio vinculado")),
                    List.of("documento privado publicavel", "validacao documental sem auditoria"),
                    List.of("politica de retencao preparada sem expurgo automatico")),
            etapa(PREPARAR_PREMIUM_BENEFICIOS, 9, PLANEJADO, BLOQUEANTE,
                    "Planejar Premium atual como preservado e recursos novos apenas como aditivos.",
                    List.of(dep(PREPARAR_ANUNCIOS, "beneficio Premium depende do anuncio")),
                    List.of("Premium inconsistente sem evidencia"),
                    List.of("beneficios atuais preservados")),
            etapa(PREPARAR_PAGAMENTOS, 10, PLANEJADO, BLOQUEANTE,
                    "Planejar pagamentos por evidencia, sem assumir provedor pelo nome de tabela.",
                    List.of(
                            dep(PREPARAR_USUARIOS, "pagamento precisa de titularidade auditavel"),
                            dep(VALIDAR_REGRAS_SANEAMENTO, "pagamento depende de classificacao por evidencia")),
                    List.of("pagamento sem provedor", "pagamento Efi sem confirmacao"),
                    List.of("pagamentos classificados por evidencia")),
            etapa(PREPARAR_CREDITOS, 11, PLANEJADO, BLOQUEANTE,
                    "Planejar creditos como inteiro/bigint conceitual e reconciliar com pagamentos aprovados.",
                    List.of(dep(PREPARAR_PAGAMENTOS, "credito depende de pagamento ou evidencia equivalente")),
                    List.of("credito sem pagamento", "pagamento aprovado sem credito"),
                    List.of("creditos preparados sem tipo decimal ou float")),
            etapa(PREPARAR_METRICAS, 12, PLANEJADO, ALERTA,
                    "Planejar metricas para preservar, migrar ou reimplementar equivalencia funcional.",
                    List.of(dep(PREPARAR_ANUNCIOS, "metricas publicas dependem do anuncio")),
                    List.of("metrica sem origem auditavel"),
                    List.of("visualizacoes e cliques preservados quando houver evidencia")),
            etapa(PREPARAR_SEO_URLS, 13, PLANEJADO, BLOQUEANTE,
                    "Planejar URLs, slugs, sitemap e redirects mantendo rotas publicas preservadas.",
                    List.of(
                            dep(PREPARAR_ANUNCIOS, "URL publica depende do anuncio"),
                            dep(PREPARAR_LOCALIDADES, "URL de listagem depende da localidade")),
                    List.of("URL publica sem decisao", "slug duplicado"),
                    List.of("rotas publicas preservadas ou redirecionadas por decisao")),
            etapa(PREPARAR_BANNERS, 14, PLANEJADO, ALERTA,
                    "Planejar banners e visual atual sem converter placeholder em midia real.",
                    List.of(dep(PREPARAR_MIDIAS, "banner depende de classificacao de midia")),
                    List.of("banner sem manifesto quando obrigatorio"),
                    List.of("dimensoes desktop e mobile preservadas")),
            etapa(PREPARAR_COMERCIAL_SUPORTE, 15, PLANEJADO, ALERTA,
                    "Planejar regras comerciais, suporte e gratuito sem limite artificial de uso.",
                    List.of(
                            dep(PREPARAR_USUARIOS, "suporte depende de usuario identificavel"),
                            dep(PREPARAR_ANUNCIOS, "acao comercial pode depender de anuncio")),
                    List.of("gratuito artificialmente limitado", "campanha sem auditoria"),
                    List.of("liquidez e base de anuncios preservadas")),
            etapa(GERAR_RELATORIO_DRY_RUN, 16, BLOQUEADO, BLOQUEANTE,
                    "Gerar relatorio futuro de dry-run estrutural com pendencias, sem importacao real.",
                    List.of(
                            dep(PREPARAR_SEO_URLS, "relatorio precisa das decisoes publicas de URL"),
                            dep(PREPARAR_COMERCIAL_SUPORTE, "relatorio inclui regras comerciais e suporte")),
                    List.of("relatorio dry-run ausente"),
                    List.of("relatorio estrutural pronto para revisao")),
            etapa(BLOQUEAR_IMPORTACAO_COM_PENDENCIA_CRITICA, 17, BLOQUEADO, BLOQUEANTE,
                    "Bloquear qualquer importacao real futura enquanto houver pendencia critica.",
                    List.of(dep(GERAR_RELATORIO_DRY_RUN, "bloqueio depende do relatorio de dry-run")),
                    List.of("pendencia bloqueante aberta", "aprovacao Pro ausente"),
                    List.of("gate de importacao real definido"),
                    true));

    private CatalogoPlanoExecucaoImportacao() {
    }

    public static PlanoExecucaoImportacaoDto planoPadrao() {
        return new PlanoExecucaoImportacaoDto(
                "PLANO_EXECUCAO_IMPORTACAO_V3",
                "2E",
                true,
                ETAPAS,
                "Plano estrutural local para dry-run futuro; nao executa importacao real.");
    }

    public static List<EtapaImportacaoDto> etapasPadrao() {
        return ETAPAS;
    }

    private static EtapaImportacaoDto etapa(
            TipoEtapaImportacao tipo,
            int ordem,
            StatusEtapaImportacao status,
            CriticidadeEtapaImportacao criticidade,
            String descricao,
            List<DependenciaEtapaImportacaoDto> dependencias,
            List<String> criteriosBloqueio,
            List<String> criteriosSucessoParcial) {
        return etapa(
                tipo,
                ordem,
                status,
                criticidade,
                descricao,
                dependencias,
                criteriosBloqueio,
                criteriosSucessoParcial,
                false);
    }

    private static EtapaImportacaoDto etapa(
            TipoEtapaImportacao tipo,
            int ordem,
            StatusEtapaImportacao status,
            CriticidadeEtapaImportacao criticidade,
            String descricao,
            List<DependenciaEtapaImportacaoDto> dependencias,
            List<String> criteriosBloqueio,
            List<String> criteriosSucessoParcial,
            boolean bloqueiaImportacaoReal) {
        return new EtapaImportacaoDto(
                tipo,
                ordem,
                status,
                criticidade,
                descricao,
                dependencias,
                criteriosBloqueio,
                criteriosSucessoParcial,
                bloqueiaImportacaoReal);
    }

    private static DependenciaEtapaImportacaoDto dep(TipoEtapaImportacao tipo, String justificativa) {
        return new DependenciaEtapaImportacaoDto(tipo, true, justificativa, null);
    }
}
