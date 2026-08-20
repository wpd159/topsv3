package br.com.topsdojob.v3.application.anuncio;

import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioAtualizacaoRequestDto;
import br.com.topsdojob.v3.domain.anuncio.CategoriaAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.LocalAtendimentoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ServicoAnuncio;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AnuncioAtualizacaoCanonicaValidator {

    private static final int TITULO_MAX = 80;
    private static final int DESCRICAO_MAX = 500;
    private static final Pattern CONTATO_NO_TITULO = Pattern.compile(
            "(?i)(\\+?\\d[\\d .()_-]{7,}\\d|whats|telefone|instagram|insta\\b|telegram|t\\.me|onlyfans|facebook|http|www\\.|@)");

    public DadosAtualizacao validar(
            MeuAnuncioAtualizacaoRequestDto request,
            String whatsappCanonico) {
        if (request == null) {
            throw badRequest("payload obrigatorio");
        }
        String titulo = textoObrigatorio(request.titulo(), "titulo", 10, TITULO_MAX);
        if (contemConteudoAtivo(titulo)) {
            throw badRequest("titulo nao pode conter HTML ou JavaScript");
        }
        if (CONTATO_NO_TITULO.matcher(titulo).find()) {
            throw badRequest("titulo nao pode conter contato, rede social ou URL");
        }
        String descricao = textoObrigatorio(request.descricao(), "descricao", 20, DESCRICAO_MAX);
        CategoriaAnuncio categoriaInformada = CategoriaAnuncio.porCodigo(request.categoria())
                .orElseThrow(() -> badRequest("categoria invalida"));
        BigDecimal preco = request.preco();
        if (preco != null) {
            if (preco.compareTo(BigDecimal.ZERO) <= 0 || preco.compareTo(new BigDecimal("999999.99")) > 0) {
                throw badRequest("preco invalido");
            }
            preco = preco.setScale(2, RoundingMode.HALF_UP);
        }
        String uf = textoObrigatorio(request.uf(), "uf", 2, 2).toUpperCase(Locale.ROOT);
        if (!uf.matches("[A-Z]{2}")) {
            throw badRequest("uf invalida");
        }
        String cidade = textoObrigatorio(request.cidade(), "cidade", 2, 80);
        String bairro = textoOpcional(request.bairro(), "bairro", 2, 80);
        String enderecoResumido = validarEnderecoResumido(request.enderecoResumido());
        Set<LocalAtendimentoAnuncio> locais = enums(
                request.locaisAtendimento(), LocalAtendimentoAnuncio.class, "locaisAtendimento");
        Set<ServicoAnuncio> servicos = new LinkedHashSet<>(
                enums(request.servicos(), ServicoAnuncio.class, "servicos"));
        String categoria = categoriaInformada.name();
        if (categoriaInformada == CategoriaAnuncio.VENDA_DE_CONTEUDO) {
            categoria = CategoriaAnuncio.ACOMPANHANTE_FEMININA.name();
            servicos.add(ServicoAnuncio.VIDEOCHAMADA);
        }
        boolean atendimentoExclusivamenteVirtual = request.atendimentoExclusivamenteVirtual();
        if (atendimentoExclusivamenteVirtual && !servicos.contains(ServicoAnuncio.VIDEOCHAMADA)) {
            throw badRequest("atendimento exclusivamente virtual exige o servico VIDEOCHAMADA");
        }
        return new DadosAtualizacao(
                titulo,
                descricao,
                categoria,
                preco,
                uf,
                cidade,
                bairro,
                enderecoResumido,
                locais,
                Set.copyOf(servicos),
                atendimentoExclusivamenteVirtual,
                whatsapp(whatsappCanonico),
                linkConteudo(request.linkConteudo()));
    }

    public String textoBusca(DadosAtualizacao request) {
        return textoBusca(request, null);
    }

    public String textoBusca(DadosAtualizacao request, String enderecoResumido) {
        return String.join(
                " ",
                request.titulo(),
                request.descricao(),
                request.cidade(),
                request.bairro() == null ? "" : request.bairro(),
                enderecoResumido == null ? "" : enderecoResumido)
                .toLowerCase(Locale.ROOT);
    }

    public String validarEnderecoResumido(String value) {
        String texto = textoOpcional(value, "enderecoResumido", 2, 120);
        if (texto != null && contemConteudoAtivo(texto)) {
            throw badRequest("enderecoResumido nao pode conter HTML ou JavaScript");
        }
        return texto;
    }

    public String slugify(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "")
                .replaceAll("-{2,}", "-");
    }

    private String textoObrigatorio(String value, String campo, int minimo, int maximo) {
        String texto = sanitize(value);
        if (texto == null || texto.length() < minimo || texto.length() > maximo) {
            throw badRequest(campo + " deve ter entre " + minimo + " e " + maximo + " caracteres");
        }
        return texto;
    }

    private String textoOpcional(String value, String campo, int minimo, int maximo) {
        String texto = sanitize(value);
        if (texto != null && (texto.length() < minimo || texto.length() > maximo)) {
            throw badRequest(campo + " deve ter entre " + minimo + " e " + maximo + " caracteres");
        }
        return texto;
    }

    private String whatsapp(String value) {
        String texto = sanitize(value);
        if (texto == null) {
            return null;
        }
        String normalizado = texto.replaceAll("[^0-9+]", "");
        if (!normalizado.startsWith("+") && normalizado.matches("[0-9]+")) {
            normalizado = "+" + normalizado;
        }
        if (!normalizado.matches("\\+[1-9][0-9]{7,14}")) {
            throw badRequest("whatsapp invalido");
        }
        return normalizado;
    }

    private String linkConteudo(String value) {
        String texto = textoOpcional(value, "linkConteudo", 8, 2048);
        if (texto == null) {
            return null;
        }
        try {
            URI uri = URI.create(texto);
            if (uri.getHost() == null
                    || (!"https".equalsIgnoreCase(uri.getScheme())
                    && !"http".equalsIgnoreCase(uri.getScheme()))) {
                throw badRequest("linkConteudo invalido");
            }
            return texto;
        } catch (IllegalArgumentException exception) {
            throw badRequest("linkConteudo invalido");
        }
    }

    private <E extends Enum<E>> Set<E> enums(List<String> values, Class<E> enumType, String campo) {
        if (values == null) {
            return Set.of();
        }
        Set<E> resultado = new LinkedHashSet<>();
        for (String value : values) {
            try {
                resultado.add(Enum.valueOf(enumType, value == null ? "" : value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                throw badRequest(campo + " contem valor invalido");
            }
        }
        return Set.copyOf(resultado);
    }

    private String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String sanitized = value.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        return sanitized.isEmpty() ? null : sanitized;
    }

    private boolean contemConteudoAtivo(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return value.indexOf('<') >= 0
                || value.indexOf('>') >= 0
                || normalized.matches(".*\\bjavascript\\s*:.*");
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record DadosAtualizacao(
            String titulo,
            String descricao,
            String categoria,
            BigDecimal preco,
            String uf,
            String cidade,
            String bairro,
            String enderecoResumido,
            Set<LocalAtendimentoAnuncio> locaisAtendimento,
            Set<ServicoAnuncio> servicos,
            boolean atendimentoExclusivamenteVirtual,
            String whatsapp,
            String linkConteudo) {
    }
}
