package br.com.topsdojob.v3.application.publico.mapper;

import br.com.topsdojob.v3.application.metrica.VisualizacoesCanonicasDto;
import br.com.topsdojob.v3.application.publico.dto.AnuncioCardPublicoDto;
import br.com.topsdojob.v3.application.publico.dto.AnuncioDetalhePublicoDto;
import br.com.topsdojob.v3.application.publico.dto.LocalizacaoPublicaDto;
import br.com.topsdojob.v3.application.publico.dto.MidiaPublicaDto;
import br.com.topsdojob.v3.application.publico.dto.SeoRotaPublicaDto;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoFlagsDto;
import br.com.topsdojob.v3.application.publico.service.IdadeAnunciantePublicaService;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.LocalAtendimentoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ServicoAnuncio;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class AnuncioPublicoMapper {

    public static final String PENDENTE_POLITICA_WHATSAPP =
            "PENDENTE_POLITICA_EXPOSICAO_WHATSAPP_PUBLICO";

    private final MidiaPublicaSeguraPolicy midiaPolicy;

    public AnuncioPublicoMapper(MidiaPublicaSeguraPolicy midiaPolicy) {
        this.midiaPolicy = midiaPolicy;
    }

    public AnuncioCardPublicoDto toCard(
            AnuncioEntity anuncio,
            LocalizacaoPublicaDto localizacao,
            List<MidiaPublicaDto> midias,
            PremiumPublicoFlagsDto premium,
            IdadeAnunciantePublicaService.Resultado idadePublica,
            boolean contatoDisponivel,
            OffsetDateTime anunciaDesde,
            VisualizacoesCanonicasDto visualizacoes) {
        PremiumPublicoFlagsDto flags = premium == null ? PremiumPublicoFlagsDto.vazio() : premium;
        return new AnuncioCardPublicoDto(
                anuncio.getId(),
                anuncio.getSlug(),
                anuncio.getTitulo(),
                resumo(anuncio.getDescricao()),
                anuncio.getPreco(),
                anuncio.getCategoria(),
                idadePublica == null ? null : idadePublica.idade(),
                localizacao,
                midiaPolicy.paraCard(midias, flags.carrosselFotosAtivo()),
                flags.destaqueAtivo(),
                flags.topoAtivo(),
                flags.carrosselFotosAtivo(),
                false,
                contatoDisponivel,
                flags.whatsappCardAtivo() && contatoDisponivel,
                anuncio.getLocaisAtendimento().contains(LocalAtendimentoAnuncio.MEU_LOCAL),
                anuncio.getServicos().contains(ServicoAnuncio.ANAL),
                flags.beneficiosPublicos(),
                anunciaDesde,
                anuncio.getPublicadoEm(),
                Objects.requireNonNull(visualizacoes, "visualizacoes canonicas obrigatorias"));
    }

    public AnuncioDetalhePublicoDto toDetalhe(
            AnuncioEntity anuncio,
            LocalizacaoPublicaDto localizacao,
            List<MidiaPublicaDto> midias,
            SeoRotaPublicaDto seo,
            PremiumPublicoFlagsDto premium,
            boolean contatoDisponivel,
            OffsetDateTime anunciaDesde,
            String username,
            Integer idade,
            boolean idadeOculta,
            VisualizacoesCanonicasDto visualizacoes) {
        PremiumPublicoFlagsDto flags = premium == null ? PremiumPublicoFlagsDto.vazio() : premium;
        return new AnuncioDetalhePublicoDto(
                anuncio.getId(),
                anuncio.getSlug(),
                anuncio.getTitulo(),
                anuncio.getDescricao(),
                anuncio.getPreco(),
                anuncio.getCategoria(),
                username,
                idade,
                idadeOculta,
                localizacao,
                List.copyOf(midias),
                flags.destaqueAtivo(),
                flags.topoAtivo(),
                flags.carrosselFotosAtivo(),
                false,
                contatoDisponivel,
                anuncio.getLocaisAtendimento().contains(LocalAtendimentoAnuncio.MEU_LOCAL),
                anuncio.getServicos().contains(ServicoAnuncio.ANAL),
                anuncio.getLocaisAtendimento().stream()
                        .map(Enum::name)
                        .sorted()
                        .toList(),
                anuncio.getServicos().stream()
                        .map(Enum::name)
                        .sorted(Comparator.naturalOrder())
                        .toList(),
                flags.beneficiosPublicos(),
                null,
                PENDENTE_POLITICA_WHATSAPP,
                anunciaDesde,
                anuncio.getPublicadoEm(),
                seo,
                Objects.requireNonNull(visualizacoes, "visualizacoes canonicas obrigatorias"));
    }

    private String resumo(String descricao) {
        if (descricao == null || descricao.length() <= 160) {
            return descricao;
        }
        return descricao.substring(0, 157).trim() + "...";
    }
}
