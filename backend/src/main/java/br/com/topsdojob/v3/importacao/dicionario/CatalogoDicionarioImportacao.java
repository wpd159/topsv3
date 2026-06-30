package br.com.topsdojob.v3.importacao.dicionario;

import static br.com.topsdojob.v3.importacao.dicionario.ObrigatoriedadeCampoImportacao.OBRIGATORIO;
import static br.com.topsdojob.v3.importacao.dicionario.ObrigatoriedadeCampoImportacao.OBRIGATORIO_SE_PRESENTE_ORIGEM;
import static br.com.topsdojob.v3.importacao.dicionario.ObrigatoriedadeCampoImportacao.OPCIONAL;
import static br.com.topsdojob.v3.importacao.dicionario.ObrigatoriedadeCampoImportacao.PENDENTE_EVIDENCIA;
import static br.com.topsdojob.v3.importacao.dicionario.SensibilidadeCampoImportacao.FINANCEIRO;
import static br.com.topsdojob.v3.importacao.dicionario.SensibilidadeCampoImportacao.OPERACIONAL;
import static br.com.topsdojob.v3.importacao.dicionario.SensibilidadeCampoImportacao.PESSOAL;
import static br.com.topsdojob.v3.importacao.dicionario.SensibilidadeCampoImportacao.PRIVADO_DOCUMENTAL;
import static br.com.topsdojob.v3.importacao.dicionario.SensibilidadeCampoImportacao.PUBLICO;
import static br.com.topsdojob.v3.importacao.dicionario.SensibilidadeCampoImportacao.SENSIVEL;
import static br.com.topsdojob.v3.importacao.dicionario.SensibilidadeCampoImportacao.TECNICO_HASH;
import static br.com.topsdojob.v3.importacao.dicionario.TipoCampoImportacao.BOOLEANO;
import static br.com.topsdojob.v3.importacao.dicionario.TipoCampoImportacao.CHECKSUM;
import static br.com.topsdojob.v3.importacao.dicionario.TipoCampoImportacao.CREDITO;
import static br.com.topsdojob.v3.importacao.dicionario.TipoCampoImportacao.DATA;
import static br.com.topsdojob.v3.importacao.dicionario.TipoCampoImportacao.DATA_HORA;
import static br.com.topsdojob.v3.importacao.dicionario.TipoCampoImportacao.DINHEIRO;
import static br.com.topsdojob.v3.importacao.dicionario.TipoCampoImportacao.DOCUMENTO;
import static br.com.topsdojob.v3.importacao.dicionario.TipoCampoImportacao.EMAIL;
import static br.com.topsdojob.v3.importacao.dicionario.TipoCampoImportacao.ENUM;
import static br.com.topsdojob.v3.importacao.dicionario.TipoCampoImportacao.IDENTIFICADOR_LEGADO;
import static br.com.topsdojob.v3.importacao.dicionario.TipoCampoImportacao.JSON;
import static br.com.topsdojob.v3.importacao.dicionario.TipoCampoImportacao.NUMERO_INTEIRO;
import static br.com.topsdojob.v3.importacao.dicionario.TipoCampoImportacao.REFERENCIA_MIDIA;
import static br.com.topsdojob.v3.importacao.dicionario.TipoCampoImportacao.TELEFONE;
import static br.com.topsdojob.v3.importacao.dicionario.TipoCampoImportacao.TEXTO;
import static br.com.topsdojob.v3.importacao.dicionario.TipoCampoImportacao.URL;
import static br.com.topsdojob.v3.importacao.pacote.TipoArquivoPacoteImportacao.CHECKSUMS;
import static br.com.topsdojob.v3.importacao.pacote.TipoArquivoPacoteImportacao.DUMP_BANCO_LEGADO;
import static br.com.topsdojob.v3.importacao.pacote.TipoArquivoPacoteImportacao.EXPORT_ANUNCIOS;
import static br.com.topsdojob.v3.importacao.pacote.TipoArquivoPacoteImportacao.EXPORT_BANNERS;
import static br.com.topsdojob.v3.importacao.pacote.TipoArquivoPacoteImportacao.EXPORT_BENEFICIOS;
import static br.com.topsdojob.v3.importacao.pacote.TipoArquivoPacoteImportacao.EXPORT_CREDITOS;
import static br.com.topsdojob.v3.importacao.pacote.TipoArquivoPacoteImportacao.EXPORT_LOCALIDADES;
import static br.com.topsdojob.v3.importacao.pacote.TipoArquivoPacoteImportacao.EXPORT_METRICAS;
import static br.com.topsdojob.v3.importacao.pacote.TipoArquivoPacoteImportacao.EXPORT_PAGAMENTOS;
import static br.com.topsdojob.v3.importacao.pacote.TipoArquivoPacoteImportacao.EXPORT_PREMIUM;
import static br.com.topsdojob.v3.importacao.pacote.TipoArquivoPacoteImportacao.EXPORT_SEO_CONTEUDOS;
import static br.com.topsdojob.v3.importacao.pacote.TipoArquivoPacoteImportacao.EXPORT_SLUGS;
import static br.com.topsdojob.v3.importacao.pacote.TipoArquivoPacoteImportacao.EXPORT_URLS_PUBLICAS;
import static br.com.topsdojob.v3.importacao.pacote.TipoArquivoPacoteImportacao.EXPORT_USUARIOS;
import static br.com.topsdojob.v3.importacao.pacote.TipoArquivoPacoteImportacao.MANIFESTO_MIDIA;
import static br.com.topsdojob.v3.importacao.pacote.TipoArquivoPacoteImportacao.RELATORIO_ORIGEM;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import br.com.topsdojob.v3.importacao.pacote.TipoArquivoPacoteImportacao;

public final class CatalogoDicionarioImportacao {

    private static final List<DicionarioArquivoImportacaoDto> CATALOGO = List.of(
            dicionario(DUMP_BANCO_LEGADO, "Descritor estrutural do dump; nao abre nem interpreta dump real.",
                    id("id_dump_legado"),
                    campo("versao_dump", TEXTO, PENDENTE_EVIDENCIA, OPERACIONAL, true, false, false, true, "depende da fonte real"),
                    campo("origem_logica", TEXTO, OBRIGATORIO, OPERACIONAL, false, true, false, false, "origem sanitizada"),
                    campo("extraido_em", DATA_HORA, OBRIGATORIO, OPERACIONAL, false, false, false, false, "data declarada"),
                    campo("checksum_sha256", CHECKSUM, OBRIGATORIO_SE_PRESENTE_ORIGEM, TECNICO_HASH, false, false, true, true, "evidencia tecnica")),
            dicionario(EXPORT_USUARIOS, "Campos minimos para usuarios legados.",
                    id("id_usuario_legado"),
                    campo("nome_publico", TEXTO, OPCIONAL, PESSOAL, false, true, false, false, "nome exibido pode exigir saneamento"),
                    campo("email", EMAIL, OPCIONAL, PESSOAL, false, true, false, false, "dado pessoal"),
                    campo("telefone_whatsapp", TELEFONE, OPCIONAL, PESSOAL, false, true, false, false, "WhatsApp e telefone sao dados pessoais"),
                    campo("documento_privado", DOCUMENTO, PENDENTE_EVIDENCIA, PRIVADO_DOCUMENTAL, false, false, true, true, "documento privado nunca e midia publica"),
                    campo("status_usuario", ENUM, OBRIGATORIO_SE_PRESENTE_ORIGEM, OPERACIONAL, false, true, false, true, "depende da fonte real")),
            dicionario(EXPORT_ANUNCIOS, "Campos minimos para anuncios.",
                    id("id_anuncio_legado"),
                    ref("id_usuario_legado"),
                    campo("slug", TEXTO, OBRIGATORIO_SE_PRESENTE_ORIGEM, PUBLICO, false, true, false, true, "preservar quando possivel"),
                    campo("titulo_publico", TEXTO, OPCIONAL, PUBLICO, false, true, false, false, "texto publico sanitizavel"),
                    campo("cidade_id_legado", IDENTIFICADOR_LEGADO, OBRIGATORIO_SE_PRESENTE_ORIGEM, OPERACIONAL, false, false, true, true, "referencia localidade"),
                    campo("preco", DINHEIRO, OPCIONAL, FINANCEIRO, false, true, false, false, "valor financeiro decimal/numeric, nunca float"),
                    campo("status_anuncio", ENUM, OBRIGATORIO_SE_PRESENTE_ORIGEM, OPERACIONAL, false, true, true, true, "status exige evidencia")),
            dicionario(EXPORT_LOCALIDADES, "Campos minimos para UF, cidade e bairro.",
                    id("id_localidade_legado"),
                    campo("uf", TEXTO, OBRIGATORIO, PUBLICO, false, true, true, false, "sigla UF"),
                    campo("cidade", TEXTO, OBRIGATORIO, PUBLICO, false, true, true, false, "nome da cidade"),
                    campo("bairro", TEXTO, OPCIONAL, PUBLICO, false, true, false, false, "bairro quando houver"),
                    campo("slug", TEXTO, OBRIGATORIO_SE_PRESENTE_ORIGEM, PUBLICO, false, true, false, true, "slug preservavel")),
            dicionario(MANIFESTO_MIDIA, "Manifesto estrutural de midia e documentos.",
                    id("id_midia_legado"),
                    ref("id_anuncio_legado"),
                    campo("caminho_legado_sanitizado", REFERENCIA_MIDIA, OBRIGATORIO, OPERACIONAL, false, true, true, false, "texto logico, nao path real"),
                    campo("tipo_midia", ENUM, OBRIGATORIO, OPERACIONAL, false, true, true, false, "classificacao estrutural"),
                    campo("checksum_sha256", CHECKSUM, OBRIGATORIO_SE_PRESENTE_ORIGEM, TECNICO_HASH, false, false, true, true, "evidencia tecnica"),
                    campo("classificacao_privacidade", ENUM, OBRIGATORIO, SENSIVEL, false, false, true, true, "documento privado fica fora de midia publica")),
            dicionario(EXPORT_METRICAS, "Metricas atuais sao existentes e preservaveis/migraveis.",
                    ref("id_anuncio_legado"),
                    campo("visualizacoes", NUMERO_INTEIRO, OBRIGATORIO_SE_PRESENTE_ORIGEM, OPERACIONAL, false, true, false, true, "preservar ou reimplementar com equivalencia"),
                    campo("cliques", NUMERO_INTEIRO, OBRIGATORIO_SE_PRESENTE_ORIGEM, OPERACIONAL, false, true, false, true, "sem limite artificial no gratuito"),
                    campo("contatos", NUMERO_INTEIRO, OBRIGATORIO_SE_PRESENTE_ORIGEM, OPERACIONAL, false, true, false, true, "sem limite artificial no gratuito"),
                    campo("whatsapp_cliques", NUMERO_INTEIRO, OBRIGATORIO_SE_PRESENTE_ORIGEM, OPERACIONAL, false, true, false, true, "sem limite artificial no gratuito"),
                    campo("coletado_em", DATA_HORA, PENDENTE_EVIDENCIA, OPERACIONAL, false, false, false, true, "depende da origem")),
            dicionario(EXPORT_PAGAMENTOS, "Pagamento deve ser classificado por evidencia, nao por nome da tabela.",
                    id("id_pagamento_legado"),
                    campo("provedor_evidencia", ENUM, OBRIGATORIO_SE_PRESENTE_ORIGEM, FINANCEIRO, false, true, true, true, "Efi/Mercado Pago legado por evidencia real"),
                    campo("tabela_origem", TEXTO, OPCIONAL, OPERACIONAL, false, true, false, false, "nome Mercado Pago pode conter Efi"),
                    campo("txid", TEXTO, OPCIONAL, FINANCEIRO, false, true, false, true, "evidencia de provedor"),
                    campo("valor", DINHEIRO, OBRIGATORIO_SE_PRESENTE_ORIGEM, FINANCEIRO, false, true, true, true, "numeric/decimal, nunca float"),
                    campo("status_pagamento", ENUM, OBRIGATORIO_SE_PRESENTE_ORIGEM, FINANCEIRO, false, true, true, true, "status financeiro"),
                    campo("aprovado_em", DATA_HORA, OPCIONAL, FINANCEIRO, false, false, false, true, "data de aprovacao quando houver"),
                    campo("id_credito_legado", IDENTIFICADOR_LEGADO, OPCIONAL, FINANCEIRO, false, false, true, true, "reconciliacao com creditos")),
            dicionario(EXPORT_CREDITOS, "Creditos usam inteiro/bigint, nunca float.",
                    id("id_credito_legado"),
                    ref("id_usuario_legado"),
                    campo("quantidade_creditos", CREDITO, OBRIGATORIO_SE_PRESENTE_ORIGEM, FINANCEIRO, false, true, true, true, "inteiro/bigint conceitual"),
                    campo("origem_credito", ENUM, OBRIGATORIO_SE_PRESENTE_ORIGEM, FINANCEIRO, false, true, true, true, "fonte exige evidencia"),
                    campo("id_pagamento_legado", IDENTIFICADOR_LEGADO, OPCIONAL, FINANCEIRO, false, false, true, true, "concilia pagamento quando existir")),
            dicionario(EXPORT_PREMIUM, "Premium atual deve ser preservado como regra existente.",
                    id("id_premium_legado"),
                    ref("id_anuncio_legado"),
                    campo("ativo", BOOLEANO, OBRIGATORIO_SE_PRESENTE_ORIGEM, OPERACIONAL, false, true, true, true, "estado premium"),
                    campo("inicio_em", DATA_HORA, OPCIONAL, OPERACIONAL, false, false, false, true, "periodo premium"),
                    campo("fim_em", DATA_HORA, OPCIONAL, OPERACIONAL, false, false, false, true, "periodo premium"),
                    campo("regra_preservacao", ENUM, PENDENTE_EVIDENCIA, OPERACIONAL, false, true, true, true, "regra existente depende da fonte")),
            dicionario(EXPORT_BENEFICIOS, "Beneficios legados preservaveis.",
                    id("id_beneficio_legado"),
                    campo("codigo_beneficio", TEXTO, OBRIGATORIO_SE_PRESENTE_ORIGEM, OPERACIONAL, false, true, false, true, "codigo legado"),
                    campo("descricao_sanitizada", TEXTO, OPCIONAL, OPERACIONAL, false, true, false, false, "sem dado real"),
                    campo("ativo", BOOLEANO, OBRIGATORIO_SE_PRESENTE_ORIGEM, OPERACIONAL, false, false, false, true, "estado do beneficio")),
            dicionario(EXPORT_BANNERS, "Banners exigem midia por manifesto.",
                    id("id_banner_legado"),
                    campo("posicao", ENUM, OBRIGATORIO_SE_PRESENTE_ORIGEM, OPERACIONAL, false, true, false, true, "posicao futura"),
                    campo("referencia_midia", REFERENCIA_MIDIA, OBRIGATORIO_SE_PRESENTE_ORIGEM, OPERACIONAL, false, true, true, true, "deve existir no manifesto"),
                    campo("link_url", URL, OPCIONAL, PUBLICO, false, true, false, false, "URL publica/canonica futura"),
                    campo("ativo", BOOLEANO, OBRIGATORIO_SE_PRESENTE_ORIGEM, OPERACIONAL, false, false, false, true, "estado")),
            dicionario(EXPORT_SEO_CONTEUDOS, "Textos SEO do painel/admin serao migrados por pacote futuro.",
                    id("id_seo_legado"),
                    campo("chave_conteudo", TEXTO, OBRIGATORIO_SE_PRESENTE_ORIGEM, OPERACIONAL, false, true, false, true, "chave SEO"),
                    campo("titulo", TEXTO, OPCIONAL, PUBLICO, false, true, false, false, "texto publico futuro"),
                    campo("descricao", TEXTO, OPCIONAL, PUBLICO, false, true, false, false, "texto publico futuro"),
                    campo("conteudo_json", JSON, OPCIONAL, OPERACIONAL, false, true, false, true, "estrutura pode depender da origem"),
                    campo("atualizado_em", DATA_HORA, OPCIONAL, OPERACIONAL, false, false, false, true, "data de referencia")),
            dicionario(EXPORT_URLS_PUBLICAS, "Mapa de URLs atuais para V3.",
                    campo("url_atual_path", URL, OBRIGATORIO, PUBLICO, true, true, true, false, "path publico atual"),
                    campo("url_destino_path", URL, OPCIONAL, PUBLICO, false, true, false, true, "destino quando houver"),
                    campo("decisao", ENUM, OBRIGATORIO, OPERACIONAL, false, true, true, true, "manter/redirecionar/noindex/remover"),
                    ref("id_legado_relacionado")),
            dicionario(EXPORT_SLUGS, "Slugs preservaveis e duplicidades.",
                    campo("slug", TEXTO, OBRIGATORIO, PUBLICO, true, true, true, false, "slug atual"),
                    campo("tipo_entidade", ENUM, OBRIGATORIO, OPERACIONAL, false, true, true, false, "tipo vinculado"),
                    ref("id_legado"),
                    campo("prioridade", NUMERO_INTEIRO, OPCIONAL, OPERACIONAL, false, true, false, true, "prioridade de decisao")),
            dicionario(CHECKSUMS, "Arquivo de checksums declarados.",
                    campo("tipo_arquivo", ENUM, OBRIGATORIO, OPERACIONAL, true, true, true, false, "tipo do pacote"),
                    campo("nome_logico", TEXTO, OBRIGATORIO, OPERACIONAL, true, true, true, false, "nome logico"),
                    campo("checksum_sha256", CHECKSUM, OBRIGATORIO, TECNICO_HASH, false, false, true, false, "hash declarado"),
                    campo("algoritmo", ENUM, OBRIGATORIO, TECNICO_HASH, false, false, true, false, "sha256 esperado"),
                    campo("tamanho_bytes", NUMERO_INTEIRO, OPCIONAL, TECNICO_HASH, false, false, false, true, "tamanho declarado")),
            dicionario(RELATORIO_ORIGEM, "Relatorio sanitizado sobre a origem do pacote.",
                    campo("origem", TEXTO, OBRIGATORIO, OPERACIONAL, true, true, true, false, "origem logica"),
                    campo("gerado_em", DATA_HORA, OBRIGATORIO, OPERACIONAL, false, false, true, false, "geracao declarada"),
                    campo("versao_extrator", TEXTO, PENDENTE_EVIDENCIA, OPERACIONAL, false, true, false, true, "depende da fonte real"),
                    campo("observacoes_sanitizadas", TEXTO, OPCIONAL, OPERACIONAL, false, true, false, false, "sem dados reais")));

    private CatalogoDicionarioImportacao() {
    }

    public static List<DicionarioArquivoImportacaoDto> catalogoPadrao() {
        return CATALOGO;
    }

    public static Optional<DicionarioArquivoImportacaoDto> porTipo(TipoArquivoPacoteImportacao tipoArquivo) {
        return CATALOGO.stream()
                .filter(dicionario -> dicionario.tipoArquivo() == tipoArquivo)
                .findFirst();
    }

    public static Map<TipoArquivoPacoteImportacao, DicionarioArquivoImportacaoDto> mapaPorTipo() {
        return CATALOGO.stream()
                .collect(Collectors.toUnmodifiableMap(
                        DicionarioArquivoImportacaoDto::tipoArquivo,
                        dicionario -> dicionario));
    }

    private static DicionarioArquivoImportacaoDto dicionario(
            TipoArquivoPacoteImportacao tipo,
            String observacao,
            CampoPacoteImportacaoDto... campos) {
        return new DicionarioArquivoImportacaoDto(tipo, List.of(campos), observacao);
    }

    private static CampoPacoteImportacaoDto id(String nome) {
        return campo(nome, IDENTIFICADOR_LEGADO, OBRIGATORIO, OPERACIONAL, true, false, true, false, "identificador legado");
    }

    private static CampoPacoteImportacaoDto ref(String nome) {
        return campo(nome, IDENTIFICADOR_LEGADO, OBRIGATORIO_SE_PRESENTE_ORIGEM, OPERACIONAL, false, false, true, true, "referencia legado");
    }

    private static CampoPacoteImportacaoDto campo(
            String nome,
            TipoCampoImportacao tipo,
            ObrigatoriedadeCampoImportacao obrigatoriedade,
            SensibilidadeCampoImportacao sensibilidade,
            boolean identificador,
            boolean saneavel,
            boolean bloqueante,
            boolean exigeEvidencia,
            String observacao) {
        return new CampoPacoteImportacaoDto(
                nome,
                tipo,
                obrigatoriedade,
                sensibilidade,
                identificador,
                saneavel,
                bloqueante,
                exigeEvidencia,
                observacao);
    }
}
