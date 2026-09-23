package br.com.topsdojob.v3.application.anuncio;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Validation identified by a fixed field and rule, without rejected values. */
public final class AnuncioAtualizacaoValidationException extends ResponseStatusException {

    public enum Campo {
        PAYLOAD("payload"),
        TITULO("titulo"),
        DESCRICAO("descricao"),
        CATEGORIA("categoria"),
        PRECO("preco"),
        UF("uf"),
        CIDADE("cidade"),
        BAIRRO("bairro"),
        ENDERECO_RESUMIDO("enderecoResumido"),
        LOCAIS_ATENDIMENTO("locaisAtendimento"),
        SERVICOS("servicos"),
        ATENDIMENTO_EXCLUSIVAMENTE_VIRTUAL("atendimentoExclusivamenteVirtual"),
        TELEFONE("telefone"),
        LINK_CONTEUDO("linkConteudo");

        private final String apiField;

        Campo(String apiField) {
            this.apiField = apiField;
        }

        public String apiField() {
            return apiField;
        }
    }

    public enum Regra {
        PAYLOAD_OBRIGATORIO,
        TAMANHO_INVALIDO,
        CONTEUDO_NAO_PERMITIDO,
        CONTATO_NAO_PERMITIDO,
        CATEGORIA_INVALIDA,
        PRECO_INVALIDO,
        UF_INVALIDA,
        LOCALIDADE_NAO_ENCONTRADA,
        VALOR_INVALIDO,
        EXCLUSIVIDADE_VIRTUAL_INVALIDA,
        TELEFONE_DA_CONTA_INVALIDO,
        URL_INVALIDA
    }

    private final Campo campo;
    private final Regra regra;
    private final String mensagemSegura;

    public AnuncioAtualizacaoValidationException(Campo campo, Regra regra, String motivoLegado) {
        super(HttpStatus.BAD_REQUEST, motivoLegado);
        this.mensagemSegura = mensagemSegura(campo, regra);
        this.campo = campo;
        this.regra = regra;
    }

    public String field() {
        return campo.apiField();
    }

    public String ruleCode() {
        return regra.name();
    }

    public String safeMessage() {
        return mensagemSegura;
    }

    private static String mensagemSegura(Campo campo, Regra regra) {
        return switch (campo.name() + ":" + regra.name()) {
            case "PAYLOAD:PAYLOAD_OBRIGATORIO" -> "Informe os dados do anúncio.";
            case "TITULO:TAMANHO_INVALIDO" -> "O nome do anúncio deve ter entre 10 e 80 caracteres.";
            case "TITULO:CONTEUDO_NAO_PERMITIDO" -> "O nome do anúncio não pode conter HTML ou JavaScript.";
            case "TITULO:CONTATO_NAO_PERMITIDO" -> "O nome do anúncio não pode conter contato, rede social ou URL.";
            case "DESCRICAO:TAMANHO_INVALIDO" -> "A descrição deve ter entre 20 e 500 caracteres.";
            case "CATEGORIA:CATEGORIA_INVALIDA" -> "Escolha uma categoria válida.";
            case "PRECO:PRECO_INVALIDO" -> "Informe um preço válido.";
            case "UF:TAMANHO_INVALIDO", "UF:UF_INVALIDA", "UF:LOCALIDADE_NAO_ENCONTRADA" ->
                    "Selecione um estado válido.";
            case "CIDADE:TAMANHO_INVALIDO", "CIDADE:LOCALIDADE_NAO_ENCONTRADA" ->
                    "Selecione uma cidade válida.";
            case "BAIRRO:TAMANHO_INVALIDO", "BAIRRO:LOCALIDADE_NAO_ENCONTRADA" ->
                    "Selecione um bairro válido ou deixe o campo vazio.";
            case "ENDERECO_RESUMIDO:TAMANHO_INVALIDO" ->
                    "O complemento deve ter entre 2 e 120 caracteres.";
            case "ENDERECO_RESUMIDO:CONTEUDO_NAO_PERMITIDO" ->
                    "O complemento não pode conter HTML ou JavaScript.";
            case "LOCAIS_ATENDIMENTO:VALOR_INVALIDO" -> "Revise os locais de atendimento.";
            case "SERVICOS:VALOR_INVALIDO" -> "Revise os serviços selecionados.";
            case "ATENDIMENTO_EXCLUSIVAMENTE_VIRTUAL:EXCLUSIVIDADE_VIRTUAL_INVALIDA" ->
                    "Atendimento exclusivamente virtual exige o serviço VIDEOCHAMADA.";
            case "TELEFONE:TELEFONE_DA_CONTA_INVALIDO" -> "Atualize o telefone em Minha Conta.";
            case "LINK_CONTEUDO:TAMANHO_INVALIDO", "LINK_CONTEUDO:URL_INVALIDA" ->
                    "Informe um link de conteúdo válido.";
            default -> throw new IllegalArgumentException("par de campo e regra desconhecido");
        };
    }
}
