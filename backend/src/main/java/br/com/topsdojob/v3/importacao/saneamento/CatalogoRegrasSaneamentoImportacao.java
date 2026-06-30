package br.com.topsdojob.v3.importacao.saneamento;

import static br.com.topsdojob.v3.importacao.saneamento.EscopoRegraSaneamentoImportacao.ANUNCIO;
import static br.com.topsdojob.v3.importacao.saneamento.EscopoRegraSaneamentoImportacao.BANNER;
import static br.com.topsdojob.v3.importacao.saneamento.EscopoRegraSaneamentoImportacao.COMERCIAL;
import static br.com.topsdojob.v3.importacao.saneamento.EscopoRegraSaneamentoImportacao.CREDITO;
import static br.com.topsdojob.v3.importacao.saneamento.EscopoRegraSaneamentoImportacao.DOCUMENTO;
import static br.com.topsdojob.v3.importacao.saneamento.EscopoRegraSaneamentoImportacao.LOCALIDADE;
import static br.com.topsdojob.v3.importacao.saneamento.EscopoRegraSaneamentoImportacao.METRICA;
import static br.com.topsdojob.v3.importacao.saneamento.EscopoRegraSaneamentoImportacao.MIDIA;
import static br.com.topsdojob.v3.importacao.saneamento.EscopoRegraSaneamentoImportacao.PACOTE_IMPORTACAO;
import static br.com.topsdojob.v3.importacao.saneamento.EscopoRegraSaneamentoImportacao.PAGAMENTO;
import static br.com.topsdojob.v3.importacao.saneamento.EscopoRegraSaneamentoImportacao.PREMIUM;
import static br.com.topsdojob.v3.importacao.saneamento.EscopoRegraSaneamentoImportacao.SEO;
import static br.com.topsdojob.v3.importacao.saneamento.EscopoRegraSaneamentoImportacao.URL;
import static br.com.topsdojob.v3.importacao.saneamento.EscopoRegraSaneamentoImportacao.USUARIO;
import static br.com.topsdojob.v3.importacao.saneamento.EscopoRegraSaneamentoImportacao.WEBHOOK;
import static br.com.topsdojob.v3.importacao.saneamento.SeveridadeRegraSaneamentoImportacao.ALERTA;
import static br.com.topsdojob.v3.importacao.saneamento.SeveridadeRegraSaneamentoImportacao.BLOQUEANTE;
import static br.com.topsdojob.v3.importacao.saneamento.SeveridadeRegraSaneamentoImportacao.INFO;
import static br.com.topsdojob.v3.importacao.saneamento.TipoRegraSaneamentoImportacao.BLOQUEAR_DADO_REAL_EM_EXEMPLO;
import static br.com.topsdojob.v3.importacao.saneamento.TipoRegraSaneamentoImportacao.CLASSIFICAR_MIDIA;
import static br.com.topsdojob.v3.importacao.saneamento.TipoRegraSaneamentoImportacao.DECIDIR_URL_PUBLICA;
import static br.com.topsdojob.v3.importacao.saneamento.TipoRegraSaneamentoImportacao.DEDUPLICAR_REGISTRO;
import static br.com.topsdojob.v3.importacao.saneamento.TipoRegraSaneamentoImportacao.GERAR_REDIRECT_SEGURO;
import static br.com.topsdojob.v3.importacao.saneamento.TipoRegraSaneamentoImportacao.MAPEAR_LOCALIDADE;
import static br.com.topsdojob.v3.importacao.saneamento.TipoRegraSaneamentoImportacao.MAPEAR_PROVEDOR_PAGAMENTO_POR_EVIDENCIA;
import static br.com.topsdojob.v3.importacao.saneamento.TipoRegraSaneamentoImportacao.MAPEAR_STATUS;
import static br.com.topsdojob.v3.importacao.saneamento.TipoRegraSaneamentoImportacao.MARCAR_NAO_IMPORTAVEL;
import static br.com.topsdojob.v3.importacao.saneamento.TipoRegraSaneamentoImportacao.NORMALIZAR_CREDITO_INTEIRO;
import static br.com.topsdojob.v3.importacao.saneamento.TipoRegraSaneamentoImportacao.NORMALIZAR_DINHEIRO;
import static br.com.topsdojob.v3.importacao.saneamento.TipoRegraSaneamentoImportacao.NORMALIZAR_EMAIL;
import static br.com.topsdojob.v3.importacao.saneamento.TipoRegraSaneamentoImportacao.NORMALIZAR_TELEFONE_E164;
import static br.com.topsdojob.v3.importacao.saneamento.TipoRegraSaneamentoImportacao.NORMALIZAR_TEXTO;
import static br.com.topsdojob.v3.importacao.saneamento.TipoRegraSaneamentoImportacao.PRESERVAR_BENEFICIO_EXISTENTE;
import static br.com.topsdojob.v3.importacao.saneamento.TipoRegraSaneamentoImportacao.PRESERVAR_METRICAS_EXISTENTES;
import static br.com.topsdojob.v3.importacao.saneamento.TipoRegraSaneamentoImportacao.PRESERVAR_PREMIUM_EXISTENTE;
import static br.com.topsdojob.v3.importacao.saneamento.TipoRegraSaneamentoImportacao.PRESERVAR_SLUG_PUBLICO;
import static br.com.topsdojob.v3.importacao.saneamento.TipoRegraSaneamentoImportacao.SANITIZAR_AUDITORIA;
import static br.com.topsdojob.v3.importacao.saneamento.TipoRegraSaneamentoImportacao.SANITIZAR_TEXTO_PUBLICO;
import static br.com.topsdojob.v3.importacao.saneamento.TipoRegraSaneamentoImportacao.SEPARAR_DOCUMENTO_PRIVADO;
import static br.com.topsdojob.v3.importacao.saneamento.TipoRegraSaneamentoImportacao.SINALIZAR_PENDENCIA_EVIDENCIA;
import static br.com.topsdojob.v3.importacao.saneamento.TipoRegraSaneamentoImportacao.VALIDAR_CHECKSUM_MIDIA;
import static br.com.topsdojob.v3.importacao.saneamento.TipoRegraSaneamentoImportacao.VALIDAR_HIERARQUIA_LOCALIDADE;
import static br.com.topsdojob.v3.importacao.saneamento.TipoRegraSaneamentoImportacao.VALIDAR_SLUG;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class CatalogoRegrasSaneamentoImportacao {

    private static final List<RegraSaneamentoImportacaoDto> REGRAS = List.of(
            regra("USUARIO_NORMALIZAR_TELEFONE", NORMALIZAR_TELEFONE_E164, USUARIO, ALERTA,
                    "Normalizar telefone e WhatsApp para formato E164 quando houver evidencia da origem.",
                    true, false, false, false, false, "TEXTO_E164"),
            regra("USUARIO_NORMALIZAR_EMAIL", NORMALIZAR_EMAIL, USUARIO, ALERTA,
                    "Normalizar e-mail sem expor dado pessoal em relatorio publico.",
                    false, false, false, false, false, "EMAIL"),
            regra("USUARIO_DEDUPLICAR_SUSPEITO", DEDUPLICAR_REGISTRO, USUARIO, BLOQUEANTE,
                    "Detectar duplicidade suspeita e exigir revisao humana antes de mesclar.",
                    true, false, false, false, false, "IDENTIFICADOR"),
            regra("USUARIO_DOCUMENTO_NAO_PUBLICO", SEPARAR_DOCUMENTO_PRIVADO, USUARIO, BLOQUEANTE,
                    "Documento privado nunca vira dado publico ou midia publica.",
                    true, false, false, true, false, "DOCUMENTO_PRIVADO"),

            regra("ANUNCIO_PRESERVAR_SLUG_PUBLICO", PRESERVAR_SLUG_PUBLICO, ANUNCIO, BLOQUEANTE,
                    "Preservar slug publico sempre que possivel para reduzir perda de SEO.",
                    true, true, true, false, false, "SLUG"),
            regra("ANUNCIO_VALIDAR_SLUG", VALIDAR_SLUG, ANUNCIO, BLOQUEANTE,
                    "Validar slug e sinalizar duplicidade antes de qualquer promocao.",
                    true, false, true, false, false, "SLUG"),
            regra("ANUNCIO_MAPEAR_STATUS", MAPEAR_STATUS, ANUNCIO, BLOQUEANTE,
                    "Mapear status legado por evidencia antes de publicar ou pausar anuncio.",
                    true, false, false, false, false, "ENUM"),
            regra("ANUNCIO_NORMALIZAR_PRECO", NORMALIZAR_DINHEIRO, ANUNCIO, ALERTA,
                    "Normalizar preco como decimal/numeric conceitual, nunca float.",
                    true, false, false, false, false, "DINHEIRO_DECIMAL"),
            regra("ANUNCIO_SEM_FOTO", SINALIZAR_PENDENCIA_EVIDENCIA, ANUNCIO, BLOQUEANTE,
                    "Sinalizar anuncio sem foto ou sem manifesto de midia.",
                    true, false, false, false, false, "PENDENCIA", List.of("ANUNCIO_SEM_FOTO")),
            regra("ANUNCIO_SEM_CIDADE", SINALIZAR_PENDENCIA_EVIDENCIA, ANUNCIO, BLOQUEANTE,
                    "Sinalizar anuncio sem cidade ou localidade sem evidencia.",
                    true, false, false, false, false, "PENDENCIA", List.of("ANUNCIO_SEM_CIDADE")),
            regra("ANUNCIO_PRESERVAR_WHATSAPP_ATIVO", SINALIZAR_PENDENCIA_EVIDENCIA, ANUNCIO, ALERTA,
                    "Preservar contato pelo WhatsApp em anuncio aprovado ou ativo conforme regra futura.",
                    true, true, false, false, false, "TELEFONE_E164"),

            regra("LOCALIDADE_NORMALIZAR", MAPEAR_LOCALIDADE, LOCALIDADE, ALERTA,
                    "Normalizar UF, cidade e bairro em estrutura hierarquica.",
                    true, false, true, false, false, "LOCALIDADE"),
            regra("LOCALIDADE_VALIDAR_HIERARQUIA", VALIDAR_HIERARQUIA_LOCALIDADE, LOCALIDADE, BLOQUEANTE,
                    "Validar hierarquia UF para cidade para bairro antes de publicar rotas.",
                    true, false, true, false, false, "LOCALIDADE"),
            regra("LOCALIDADE_SEM_EVIDENCIA", SINALIZAR_PENDENCIA_EVIDENCIA, LOCALIDADE, BLOQUEANTE,
                    "Sinalizar localidade sem evidencia suficiente.",
                    true, false, false, false, false, "PENDENCIA"),

            regra("MIDIA_CLASSIFICAR", CLASSIFICAR_MIDIA, MIDIA, ALERTA,
                    "Classificar foto, video e story somente por manifesto futuro.",
                    true, false, false, false, false, "MIDIA"),
            regra("MIDIA_VALIDAR_CHECKSUM", VALIDAR_CHECKSUM_MIDIA, MIDIA, BLOQUEANTE,
                    "Validar checksum quando houver evidencia tecnica declarada.",
                    true, false, false, false, false, "CHECKSUM", List.of("MIDIA_CHECKSUM_DIVERGENTE")),
            regra("MIDIA_SEM_MANIFESTO", SINALIZAR_PENDENCIA_EVIDENCIA, MIDIA, BLOQUEANTE,
                    "Sinalizar midia sem manifesto antes de qualquer uso publico.",
                    true, false, false, false, false, "PENDENCIA", List.of("MIDIA_SEM_MANIFESTO")),
            regra("MIDIA_SEPARAR_DOCUMENTO_PRIVADO", SEPARAR_DOCUMENTO_PRIVADO, MIDIA, BLOQUEANTE,
                    "Manter documento privado fora de galeria e midia publica.",
                    true, false, false, true, false, "DOCUMENTO_PRIVADO"),

            regra("DOCUMENTO_RETENCAO_ANUNCIO", SEPARAR_DOCUMENTO_PRIVADO, DOCUMENTO, BLOQUEANTE,
                    "Aplicar politica ENQUANTO_HOUVER_ANUNCIO, retencao_ate nullable e acesso auditavel.",
                    true, true, false, true, false, "DOCUMENTO_PRIVADO"),
            regra("DOCUMENTO_SEM_EXPURGO_AUTOMATICO", MARCAR_NAO_IMPORTAVEL, DOCUMENTO, ALERTA,
                    "Nao criar expurgo automatico nesta fase e nunca publicar documento privado.",
                    true, false, false, true, false, "DOCUMENTO_PRIVADO"),

            regra("PAGAMENTO_PROVEDOR_EVIDENCIA", MAPEAR_PROVEDOR_PAGAMENTO_POR_EVIDENCIA, PAGAMENTO, BLOQUEANTE,
                    "Classificar provedor por evidencia; tabela Mercado Pago pode conter Efi.",
                    true, false, false, false, false, "PROVEDOR_POR_EVIDENCIA"),
            regra("PAGAMENTO_NAO_CONVERTER_EFI_POR_TABELA", MAPEAR_PROVEDOR_PAGAMENTO_POR_EVIDENCIA, PAGAMENTO, BLOQUEANTE,
                    "Nao converter Efi em Mercado Pago pelo nome da tabela e nao converter Mercado Pago em Efi sem evidencia.",
                    true, false, false, false, false, "PROVEDOR_POR_EVIDENCIA"),
            regra("PAGAMENTO_SEM_PROVEDOR", SINALIZAR_PENDENCIA_EVIDENCIA, PAGAMENTO, BLOQUEANTE,
                    "Sinalizar pagamento sem provedor quando faltar evidencia.",
                    true, false, false, false, false, "PENDENCIA", List.of("PAGAMENTO_SEM_PROVEDOR")),
            regra("PAGAMENTO_EFI_NAO_CONFIRMADO", SINALIZAR_PENDENCIA_EVIDENCIA, PAGAMENTO, BLOQUEANTE,
                    "Sinalizar Efi nao confirmado quando a evidencia transacional for insuficiente.",
                    true, false, false, false, false, "PENDENCIA", List.of("PAGAMENTO_EFI_NAO_CONFIRMADO")),
            regra("PAGAMENTO_STATUS_INCONSISTENTE", SINALIZAR_PENDENCIA_EVIDENCIA, PAGAMENTO, BLOQUEANTE,
                    "Sinalizar status de pagamento inconsistente por evidencia de reconciliacao.",
                    true, false, false, false, false, "PENDENCIA", List.of("STATUS_PAGAMENTO_INCONSISTENTE")),

            regra("CREDITO_INTEIRO", NORMALIZAR_CREDITO_INTEIRO, CREDITO, BLOQUEANTE,
                    "Usar inteiro ou bigint conceitual para credito, nunca float.",
                    true, false, false, false, false, "INTEIRO_BIGINT"),
            regra("CREDITO_SEM_PAGAMENTO", SINALIZAR_PENDENCIA_EVIDENCIA, CREDITO, BLOQUEANTE,
                    "Sinalizar credito sem pagamento por evidencia de conciliacao.",
                    true, false, false, false, false, "INTEIRO_BIGINT", List.of("CREDITO_SEM_PAGAMENTO")),
            regra("CREDITO_PAGAMENTO_APROVADO_SEM_CREDITO", SINALIZAR_PENDENCIA_EVIDENCIA, CREDITO, BLOQUEANTE,
                    "Sinalizar pagamento aprovado sem credito por evidencia de conciliacao.",
                    true, false, false, false, false, "INTEIRO_BIGINT", List.of("PAGAMENTO_APROVADO_SEM_CREDITO")),
            regra("CREDITO_DUPLICADO", SINALIZAR_PENDENCIA_EVIDENCIA, CREDITO, BLOQUEANTE,
                    "Sinalizar credito duplicado antes de qualquer promocao.",
                    true, false, false, false, false, "INTEIRO_BIGINT", List.of("PAGAMENTO_COM_CREDITO_DUPLICADO")),

            regra("PREMIUM_PRESERVAR_ATUAL", PRESERVAR_PREMIUM_EXISTENTE, PREMIUM, BLOQUEANTE,
                    "Preservar Premium atual como regra existente e tratar novos recursos como aditivos.",
                    true, true, false, false, false, "PREMIUM_EXISTENTE"),
            regra("PREMIUM_PRESERVAR_BENEFICIOS", PRESERVAR_BENEFICIO_EXISTENTE, PREMIUM, ALERTA,
                    "Preservar beneficios atuais e sinalizar inconsistencias de expiracao ou pacote.",
                    true, true, false, false, false, "BENEFICIO_EXISTENTE"),
            regra("PREMIUM_GRATUITO_SEM_LIMITE_ARTIFICIAL", MARCAR_NAO_IMPORTAVEL, PREMIUM, BLOQUEANTE,
                    "Nao criar limite diario artificial de cliques, contatos ou WhatsApp no gratuito.",
                    true, true, false, false, false, "REGRA_COMERCIAL"),

            regra("METRICA_PRESERVAR_EXISTENTES", PRESERVAR_METRICAS_EXISTENTES, METRICA, BLOQUEANTE,
                    "Tratar metricas de producao como existentes e preservar, migrar ou reimplementar com equivalencia funcional.",
                    true, true, false, false, false, "METRICA_EXISTENTE"),
            regra("METRICA_WHATSAPP_CLIQUES", PRESERVAR_METRICAS_EXISTENTES, METRICA, ALERTA,
                    "Manter visualizacoes e cliques WhatsApp quando houver evidencia.",
                    true, true, false, false, false, "NUMERO_INTEIRO"),

            regra("SEO_SANITIZAR_TEXTO_PUBLICO", SANITIZAR_TEXTO_PUBLICO, SEO, ALERTA,
                    "Sanitizar textos SEO do painel ou admin antes de conteudo publico final.",
                    true, false, true, false, false, "TEXTO_PUBLICO"),
            regra("URL_PRESERVAR_ROTAS_PUBLICAS", DECIDIR_URL_PUBLICA, URL, BLOQUEANTE,
                    "Preservar /anuncios/[slug], /acompanhantes/[uf]/[cidade], /acompanhantes/[uf]/[cidade]/[bairro], /sitemap.xml e /robots.txt.",
                    true, true, true, false, false, "URL_PUBLICA"),
            regra("URL_PROIBIR_ROTAS_ALTERNATIVAS", MARCAR_NAO_IMPORTAVEL, URL, BLOQUEANTE,
                    "Proibir rotas alternativas de anuncio e decidir manter, redirecionar, noindex ou remover por evidencia.",
                    true, false, true, false, false, "URL_PUBLICA"),
            regra("URL_REDIRECT_SEGURO", GERAR_REDIRECT_SEGURO, URL, BLOQUEANTE,
                    "Evitar derrubar pagina indexada util sem auditoria e evidencia.",
                    true, false, true, false, false, "URL_PUBLICA"),

            regra("BANNER_DIMENSOES_PRESERVADAS", CLASSIFICAR_MIDIA, BANNER, ALERTA,
                    "Preservar dimensoes desktop 1452x500 e mobile 1080x900.",
                    true, true, true, false, false, "MIDIA_BANNER"),
            regra("BANNER_MANIFESTO_FUTURO", VALIDAR_CHECKSUM_MIDIA, BANNER, ALERTA,
                    "Validar midia pelo manifesto futuro e permitir rascunho sem midia obrigatoria.",
                    true, false, false, false, false, "MIDIA_BANNER"),

            regra("COMERCIAL_LIQUIDEZ_BASE", SANITIZAR_AUDITORIA, COMERCIAL, BLOQUEANTE,
                    "Preservar foco em liquidez e base de anuncios.",
                    true, true, false, false, false, "REGRA_COMERCIAL"),
            regra("COMERCIAL_CAMPANHA_AUDITAVEL", SANITIZAR_AUDITORIA, COMERCIAL, ALERTA,
                    "Campanhas e cortesias precisam de validade e auditoria futuras.",
                    true, false, false, false, false, "REGRA_COMERCIAL"),
            regra("COMERCIAL_GRATUITO_SEM_LIMITACAO", MARCAR_NAO_IMPORTAVEL, COMERCIAL, BLOQUEANTE,
                    "Nao transformar gratuito em produto artificialmente limitado.",
                    true, true, false, false, false, "REGRA_COMERCIAL"),

            regra("WEBHOOK_SANITIZAR_AUDITORIA", SANITIZAR_AUDITORIA, WEBHOOK, ALERTA,
                    "Sanitizar auditoria de webhook e preservar evidencia sem chamar provedor real.",
                    true, false, false, false, false, "AUDITORIA"),
            regra("PACOTE_BLOQUEAR_DADO_REAL_EXEMPLO", BLOQUEAR_DADO_REAL_EM_EXEMPLO, PACOTE_IMPORTACAO, BLOQUEANTE,
                    "Bloquear dado real em exemplo e manter pacote apenas estrutural.",
                    true, false, false, false, false, "PACOTE"));

    private CatalogoRegrasSaneamentoImportacao() {
    }

    public static List<RegraSaneamentoImportacaoDto> catalogoPadrao() {
        return REGRAS;
    }

    public static Map<EscopoRegraSaneamentoImportacao, List<RegraSaneamentoImportacaoDto>> porEscopo() {
        return REGRAS.stream()
                .collect(Collectors.groupingBy(
                        RegraSaneamentoImportacaoDto::escopo,
                        Collectors.toUnmodifiableList()));
    }

    private static RegraSaneamentoImportacaoDto regra(
            String codigo,
            TipoRegraSaneamentoImportacao tipo,
            EscopoRegraSaneamentoImportacao escopo,
            SeveridadeRegraSaneamentoImportacao severidade,
            String descricao,
            boolean exigeEvidencia,
            boolean preservaExistente,
            boolean publicaDado,
            boolean documentoPrivado,
            boolean gratuitoComLimiteArtificial,
            String tipoValorEstrutural) {
        return regra(
                codigo,
                tipo,
                escopo,
                severidade,
                descricao,
                exigeEvidencia,
                preservaExistente,
                publicaDado,
                documentoPrivado,
                gratuitoComLimiteArtificial,
                tipoValorEstrutural,
                List.of());
    }

    private static RegraSaneamentoImportacaoDto regra(
            String codigo,
            TipoRegraSaneamentoImportacao tipo,
            EscopoRegraSaneamentoImportacao escopo,
            SeveridadeRegraSaneamentoImportacao severidade,
            String descricao,
            boolean exigeEvidencia,
            boolean preservaExistente,
            boolean publicaDado,
            boolean documentoPrivado,
            boolean gratuitoComLimiteArtificial,
            String tipoValorEstrutural,
            List<String> pendenciasSinalizadas) {
        return new RegraSaneamentoImportacaoDto(
                codigo,
                tipo,
                escopo,
                severidade,
                descricao,
                exigeEvidencia,
                preservaExistente,
                publicaDado,
                documentoPrivado,
                gratuitoComLimiteArtificial,
                tipoValorEstrutural,
                pendenciasSinalizadas);
    }
}
