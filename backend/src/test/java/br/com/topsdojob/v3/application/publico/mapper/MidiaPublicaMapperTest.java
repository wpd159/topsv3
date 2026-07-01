package br.com.topsdojob.v3.application.publico.mapper;

import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.entity;
import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.set;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.application.publico.dto.MidiaPublicaDto;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ClassificacaoConteudo;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MidiaPublicaMapperTest {

    private final MidiaPublicaMapper mapper = new MidiaPublicaMapper();

    @Test
    void mapperNaoExpoeDocumentoPrivadoStorageKeyOuHash() {
        UUID arquivoId = UUID.randomUUID();
        AnuncioMidiaEntity vinculo = entity(AnuncioMidiaEntity.class);
        set(vinculo, "arquivoMidiaId", arquivoId);
        set(vinculo, "tipo", TipoAnuncioMidia.FOTO);
        set(vinculo, "finalidade", FinalidadeAnuncioMidia.GALERIA);
        set(vinculo, "ordem", 1);
        set(vinculo, "status", StatusAnuncioMidia.PUBLICAVEL);
        set(vinculo, "classificacaoConteudo", ClassificacaoConteudo.LIVRE);

        ArquivoMidiaEntity arquivo = entity(ArquivoMidiaEntity.class);
        set(arquivo, "id", arquivoId);
        set(arquivo, "bucket", "bucket-privado");
        set(arquivo, "chaveObjeto", "documentos/privado/frente.png");
        set(arquivo, "sha256", "a".repeat(64));
        set(arquivo, "mimeType", "image/png");
        set(arquivo, "largura", 640);
        set(arquivo, "altura", 480);
        set(arquivo, "statusArquivo", StatusArquivoMidia.VALIDADO);
        set(arquivo, "classificacaoConteudo", ClassificacaoConteudo.LIVRE);

        List<MidiaPublicaDto> midias = mapper.publicas(List.of(vinculo), Map.of(arquivoId, arquivo));

        assertThat(midias).hasSize(1);
        assertThat(midias.get(0).urlPublica()).isNull();
        assertThat(midias.get(0).toString())
                .doesNotContain("bucket-privado")
                .doesNotContain("documentos/privado")
                .doesNotContain("aaaaaaaa");
    }

    @Test
    void mapperBloqueiaMidiaNaoAprovadaParaPublicacao() {
        UUID arquivoId = UUID.randomUUID();
        AnuncioMidiaEntity vinculo = entity(AnuncioMidiaEntity.class);
        set(vinculo, "arquivoMidiaId", arquivoId);
        set(vinculo, "status", StatusAnuncioMidia.PENDENTE);
        set(vinculo, "classificacaoConteudo", ClassificacaoConteudo.LIVRE);

        ArquivoMidiaEntity arquivo = entity(ArquivoMidiaEntity.class);
        set(arquivo, "id", arquivoId);
        set(arquivo, "statusArquivo", StatusArquivoMidia.VALIDADO);
        set(arquivo, "classificacaoConteudo", ClassificacaoConteudo.LIVRE);

        assertThat(mapper.publicas(List.of(vinculo), Map.of(arquivoId, arquivo))).isEmpty();
    }

    @Test
    void mapperBloqueiaMidiaComClassificacaoBloqueada() {
        UUID arquivoId = UUID.randomUUID();
        AnuncioMidiaEntity vinculo = entity(AnuncioMidiaEntity.class);
        set(vinculo, "arquivoMidiaId", arquivoId);
        set(vinculo, "tipo", TipoAnuncioMidia.FOTO);
        set(vinculo, "finalidade", FinalidadeAnuncioMidia.GALERIA);
        set(vinculo, "status", StatusAnuncioMidia.PUBLICAVEL);
        set(vinculo, "classificacaoConteudo", ClassificacaoConteudo.BLOQUEADO);

        ArquivoMidiaEntity arquivo = entity(ArquivoMidiaEntity.class);
        set(arquivo, "id", arquivoId);
        set(arquivo, "statusArquivo", StatusArquivoMidia.VALIDADO);
        set(arquivo, "classificacaoConteudo", ClassificacaoConteudo.LIVRE);

        assertThat(mapper.publicas(List.of(vinculo), Map.of(arquivoId, arquivo))).isEmpty();
    }

    @Test
    void mapperLiberaMidiaBloqueadaComIdadeConfirmada() {
        UUID arquivoId = UUID.randomUUID();
        AnuncioMidiaEntity vinculo = entity(AnuncioMidiaEntity.class);
        set(vinculo, "arquivoMidiaId", arquivoId);
        set(vinculo, "tipo", TipoAnuncioMidia.FOTO);
        set(vinculo, "finalidade", FinalidadeAnuncioMidia.GALERIA);
        set(vinculo, "status", StatusAnuncioMidia.PUBLICAVEL);
        set(vinculo, "classificacaoConteudo", ClassificacaoConteudo.BLOQUEADO);

        ArquivoMidiaEntity arquivo = entity(ArquivoMidiaEntity.class);
        set(arquivo, "id", arquivoId);
        set(arquivo, "statusArquivo", StatusArquivoMidia.VALIDADO);
        set(arquivo, "classificacaoConteudo", ClassificacaoConteudo.BLOQUEADO);

        assertThat(mapper.publicas(List.of(vinculo), Map.of(arquivoId, arquivo), true)).hasSize(1);
    }

    @Test
    void mapperNaoExpoeStorySemConfirmacaoDeIdadeMesmoLivre() {
        UUID arquivoId = UUID.randomUUID();
        AnuncioMidiaEntity vinculo = entity(AnuncioMidiaEntity.class);
        set(vinculo, "arquivoMidiaId", arquivoId);
        set(vinculo, "tipo", TipoAnuncioMidia.STORY);
        set(vinculo, "finalidade", FinalidadeAnuncioMidia.STORY);
        set(vinculo, "status", StatusAnuncioMidia.PUBLICAVEL);
        set(vinculo, "classificacaoConteudo", ClassificacaoConteudo.LIVRE);

        ArquivoMidiaEntity arquivo = entity(ArquivoMidiaEntity.class);
        set(arquivo, "id", arquivoId);
        set(arquivo, "statusArquivo", StatusArquivoMidia.VALIDADO);
        set(arquivo, "classificacaoConteudo", ClassificacaoConteudo.LIVRE);

        assertThat(mapper.publicas(List.of(vinculo), Map.of(arquivoId, arquivo))).isEmpty();
    }
}
