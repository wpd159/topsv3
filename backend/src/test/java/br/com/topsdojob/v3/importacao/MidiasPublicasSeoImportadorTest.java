package br.com.topsdojob.v3.importacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MidiasPublicasSeoImportadorTest {

  private static final Path IMPORTACAO = Path.of(
      "..", "scripts", "local", "importacao");
  private static final Path INVENTARIO = IMPORTACAO.resolve(
      "inventariar-midias-privadas-copia-limpa.sql");

  @Test
  void anunciosImportadosEntramPendentesSemCatalogoOuSitemap() throws Exception {
    String sql = Files.readString(IMPORTACAO.resolve("dryrun-producao-v3-saneado.sql"));

    assertThat(sql)
        .contains("ELSE 'PENDENTE_REVISAO'")
        .contains("'PENDENTE'::text AS status_moderacao")
        .contains("a.status AS status_origem")
        .contains("'publicadoAutomaticamente', false")
        .contains("'ANUNCIO_AGUARDA_MODERACAO_V3'")
        .contains("CASE WHEN a.status = 'PUBLICADO' THEN 'PUBLICAVEL' ELSE 'NAO_PUBLICAVEL' END")
        .doesNotContain("WHEN 'ATIVO' THEN 'PUBLICADO'")
        .doesNotContain("WHEN 'ATIVO' THEN 'APROVADO'");
  }

  @Test
  void fotosEVideosUsamSomenteDestinoPrivadoPendente() throws Exception {
    String sql = Files.readString(IMPORTACAO.resolve("dryrun-producao-v3-saneado.sql"));
    String media = sql.substring(
        sql.indexOf("-- Toda midia recuperavel"),
        sql.indexOf("-- Busca e SEO"));
    String persistence = media.substring(
        media.indexOf("INSERT INTO arquivo_midia"),
        media.indexOf("INSERT INTO stg_midia"));

    assertThat(sql)
        .contains("/tmp/dryrun-r2-private-media.tsv")
        .contains("dryrun_r2_private_media")
        .contains("c.r2_private_media_bucket")
        .contains("c.r2_private_media_prefix")
        .contains("manifesto R2 privado possui falha bloqueante de storage")
        .contains("manifesto R2 privado possui midia logica sem principal recuperavel"
            + " ou quarentena de origem autorizada")
        .contains("manifesto R2 privado nao cobre todas as referencias")
        .contains("objeto R2 privado foi compartilhado entre anuncios distintos");
    assertThat(persistence)
        .contains("md5('r2:arquivo-privado-destino:'")
        .contains("'PENDENTE'")
        .contains("CASE WHEN m.tipo = 'VIDEO' THEN 'RESTRITA_18' END")
        .doesNotContain("c.r2_public_media_bucket")
        .doesNotContain("'PUBLICAVEL'")
        .doesNotContain("'LIVRE'");
  }

  @Test
  void manifestoCobreFotosVideosAssetsEVariantesSemReferenciaOperacionalDaOrigem()
      throws Exception {
    String sql = Files.readString(IMPORTACAO.resolve("dryrun-producao-v3-saneado.sql"));

    assertThat(sql)
        .contains("'anuncio_fotos'::text AS source_table")
        .contains("'anuncio_videos'")
        .contains("'protected_media_assets'")
        .contains("'ORIGINAL'::text AS variante")
        .contains("'LEGADO'")
        .contains("'PREVIEW'")
        .contains("'THUMBNAIL'")
        .contains("'DERIVADO'")
        .contains("'PROCESSADO'")
        .contains("'destinoObjectKeyHash', md5(coalesce(r.object_key, ''))")
        .doesNotContain("'urlOrigem'")
        .doesNotContain("'objectKeyOrigem'");
  }

  @Test
  void midiaTotalmenteAusenteFicaEmQuarentenaSemEntidadeOperacional() throws Exception {
    String importer = Files.readString(
        IMPORTACAO.resolve("dryrun-producao-v3-saneado.sql"));
    String validator = Files.readString(
        IMPORTACAO.resolve("validar-dryrun-producao-v3-saneado.sql"));

    assertThat(importer)
        .contains("'QUARENTENA_ORIGEM_AUSENTE'")
        .contains("r.motivo = 'OBJETO_ORIGEM_AUSENTE'")
        .contains("'MIDIA_ORIGEM_AUSENTE'")
        .contains("'ANUNCIO_MIDIA'")
        .contains("ON CONFLICT (id) DO NOTHING")
        .contains("midia ausente na origem gerou midia operacional")
        .contains("anuncio com midia ausente nao permaneceu pendente de moderacao")
        .doesNotContain("'urlOrigem'")
        .doesNotContain("'objectKeyOrigem'");
    assertThat(validator)
        .contains("MIDIA_LOGICA_ORIGEM|")
        .contains("MIDIA_LOGICA_IMPORTADA|")
        .contains("MIDIA_LOGICA_QUARENTENA|")
        .contains("MIDIA_LOGICA_DIVERGENTE|")
        .contains("midias_logicas_importadas + midias_logicas_quarentena")
        .contains("quarentena de midia ausente criou entidade operacional"
            + " ou liberou anuncio")
        .contains("quarentena de midia impediu a importacao de anuncio da origem");
  }

  @Test
  void inventarioUsaSomenteObjetosRecuperaveisEAgrupaVariantesDaMesmaMidia()
      throws Exception {
    String sql = Files.readString(INVENTARIO);

    assertThat(sql)
        .contains("r2_source_public_base_url")
        .contains("r2_source_public_bucket")
        .contains("r2_source_private_bucket")
        .contains("FROM anuncio_fotos")
        .contains("FROM anuncio_videos")
        .contains("FROM protected_media_assets")
        .contains("p.original_storage_ref LIKE 'r2://%'")
        .contains("THEN p.legacy_original_url")
        .contains("ELSE p.preview_public_url")
        .contains("PARTITION BY r.source_ad_id, r.logical_media_hash")
        .contains("count(*) FILTER (WHERE primary_media) <> 1")
        .contains("ROLLBACK;")
        .doesNotContain("INSERT INTO")
        .doesNotContain("UPDATE ")
        .doesNotContain("DELETE FROM");
  }

  @Test
  void validadorExigeMidiaPrivadaPendenteENenhumIndicePublico() throws Exception {
    String sql = Files.readString(
        IMPORTACAO.resolve("validar-dryrun-producao-v3-saneado.sql"));

    assertThat(sql)
        .contains("ANUNCIOS_IMPORTADOS_PUBLICADOS_INDEVIDAMENTE")
        .contains("ANUNCIOS_IMPORTADOS_MODERADOS_INDEVIDAMENTE")
        .contains("MIDIA_PRIVADA_DESTINO_INVALIDA")
        .contains("FOTO_FORA_DE_PENDENTE_PRIVADA")
        .contains("VIDEO_FORA_DE_PENDENTE_RESTRITA")
        .contains("anuncio importado foi publicado, indexado ou adicionado ao catalogo")
        .contains("foto ou video importado fora do contrato privado e pendente");
  }

  @Test
  void falhaFechadoParaParametroAusenteOuManifestoDeOutroAmbiente() throws Exception {
    String sql = Files.readString(IMPORTACAO.resolve("dryrun-producao-v3-saneado.sql"));

    assertThat(sql)
        .contains("\\if :{?r2_public_media_bucket}")
        .contains("\\if :{?r2_public_media_prefix}")
        .contains("\\if :{?r2_private_media_bucket}")
        .contains("\\if :{?r2_private_media_prefix}")
        .contains("\\if :{?r2_document_bucket}")
        .contains("\\if :{?r2_document_prefix}")
        .contains("manifesto R2 privado nao corresponde ao prefixo e objetos do destino configurado")
        .contains("storageDestinationFingerprint");
  }
}
