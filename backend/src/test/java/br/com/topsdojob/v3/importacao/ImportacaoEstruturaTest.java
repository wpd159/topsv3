package br.com.topsdojob.v3.importacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.topsdojob.v3.importacao.mapeamento.LinhaMapaUrlDto;
import br.com.topsdojob.v3.importacao.mapeamento.MapeamentoLegadoV3Dto;
import br.com.topsdojob.v3.importacao.model.CodigoPendenciaImportacao;
import br.com.topsdojob.v3.importacao.model.DecisaoUrlImportacao;
import br.com.topsdojob.v3.importacao.model.PendenciaImportacaoDto;
import br.com.topsdojob.v3.importacao.model.SeveridadePendenciaImportacao;
import br.com.topsdojob.v3.importacao.model.StatusImportacaoItem;
import br.com.topsdojob.v3.importacao.model.TipoEntidadeImportacao;
import br.com.topsdojob.v3.importacao.relatorio.ImportacaoRelatorioBuilder;
import br.com.topsdojob.v3.importacao.relatorio.RelatorioImportacaoResumoDto;

class ImportacaoEstruturaTest {

    @Test
    void codigoPendenciaContemCodigosObrigatorios() {
        List<String> codigos = Arrays.stream(CodigoPendenciaImportacao.values())
                .map(Enum::name)
                .toList();

        assertThat(codigos).contains(
                "IMPORTADO_OK",
                "USUARIO_SEM_TELEFONE",
                "USUARIO_DUPLICADO_SUSPEITO",
                "ANUNCIO_SEM_FOTO",
                "ANUNCIO_COM_MIDIA_QUEBRADA",
                "ANUNCIO_SEM_PRECO",
                "ANUNCIO_SEM_CIDADE",
                "SLUG_DUPLICADO",
                "PREMIUM_INCONSISTENTE",
                "CREDITO_INCONSISTENTE",
                "PAGAMENTO_SEM_PROVEDOR",
                "PAGAMENTO_SEM_TXID",
                "PAGAMENTO_DUPLICADO",
                "PAGAMENTO_APROVADO_SEM_CREDITO",
                "CREDITO_SEM_PAGAMENTO",
                "PAGAMENTO_COM_CREDITO_DUPLICADO",
                "STATUS_PAGAMENTO_INCONSISTENTE",
                "EVENTO_WEBHOOK_DUPLICADO",
                "PAGAMENTO_EFI_NAO_CONFIRMADO",
                "PAGAMENTO_MERCADO_PAGO_LEGADO",
                "SEO_PENDENTE",
                "MIDIA_SEM_MANIFESTO",
                "MIDIA_CHECKSUM_DIVERGENTE",
                "URL_SEM_DECISAO");
    }

    @Test
    void builderSomaPendenciasPorSeveridade() {
        ImportacaoRelatorioBuilder builder = new ImportacaoRelatorioBuilder()
                .adicionarItem(TipoEntidadeImportacao.ANUNCIO)
                .adicionarPendencia(PendenciaImportacaoDto.de(
                        TipoEntidadeImportacao.ANUNCIO,
                        "anuncio-1",
                        CodigoPendenciaImportacao.ANUNCIO_SEM_FOTO,
                        "foto ausente no manifesto sanitizado"))
                .adicionarPendencia(new PendenciaImportacaoDto(
                        TipoEntidadeImportacao.PAGAMENTO,
                        "pagamento-1",
                        CodigoPendenciaImportacao.PAGAMENTO_DUPLICADO,
                        SeveridadePendenciaImportacao.BLOQUEANTE,
                        "duplicidade por evidencia transacional"));

        RelatorioImportacaoResumoDto resumo = builder.build();

        assertThat(resumo.totalItens()).isEqualTo(1);
        assertThat(resumo.totalPendencias()).isEqualTo(2);
        assertThat(resumo.pendenciasPorSeveridade())
                .containsEntry(SeveridadePendenciaImportacao.ERRO, 1L)
                .containsEntry(SeveridadePendenciaImportacao.BLOQUEANTE, 1L);
        assertThat(resumo.contemPendenciaBloqueante()).isTrue();
    }

    @Test
    void mapaLegadoV3NaoAceitaEntidadeSemOrigem() {
        assertThatThrownBy(() -> new MapeamentoLegadoV3Dto(
                TipoEntidadeImportacao.USUARIO,
                " ",
                "usuario-1",
                null,
                StatusImportacaoItem.COM_PENDENCIA,
                List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("origemLegado");
    }

    @Test
    void mapaUrlExigeDecisao() {
        assertThatThrownBy(() -> new LinhaMapaUrlDto(
                "/anuncios/slug-legado",
                "/anuncios/slug-v3",
                TipoEntidadeImportacao.URL,
                "url-1",
                DecisaoUrlImportacao.SEM_DECISAO,
                List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decisao");
    }
}
