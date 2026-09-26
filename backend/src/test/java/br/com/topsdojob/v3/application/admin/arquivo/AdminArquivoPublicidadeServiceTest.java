package br.com.topsdojob.v3.application.admin.arquivo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoPublicidadeDtos.Detalhe;
import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoPublicidadeDtos.Versao;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.sql.ResultSet;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AdminArquivoPublicidadeServiceTest {
  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void detalheOperacionalOcultaContratanteMasExportacaoPreservaEAudita() {
    UUID id = UUID.randomUUID();
    UUID ator = UUID.randomUUID();
    ObjectMapper mapper = new ObjectMapper();
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    AdminArquivoPublicidadeAccessAuditService audit = mock(AdminArquivoPublicidadeAccessAuditService.class);
    Detalhe base = new Detalhe(id, UUID.randomUUID(), UUID.randomUUID(),
        null, null, null, null, "ORIGEM_INDETERMINADA", "DESCONHECIDA", "PREVENTIVA",
        OffsetDateTime.parse("2026-09-25T10:00:00Z"), null, null, null, List.of());
    var conteudo = mapper.createObjectNode().put("titulo", "Sintetico")
        .put("whatsapp_normalizado", "+5511999999999")
        .put("link_conteudo", "https://example.invalid/segredo");
    conteudo.set("localizacao", mapper.createObjectNode().put("endereco_resumido", "Rua QA"));
    var comercial = mapper.createObjectNode().put("movimentoCreditoId", UUID.randomUUID().toString());
    Versao versao = new Versao(UUID.randomUUID(), 1,
        OffsetDateTime.parse("2026-09-25T10:00:00Z"),
        OffsetDateTime.parse("2026-09-25T10:00:00Z"), null, "PUBLICACAO",
        conteudo,
        mapper.createObjectNode().put("cpf", "00000000000"),
        comercial, mapper.createObjectNode(), mapper.createObjectNode(),
        "0".repeat(64), List.of());
    when(jdbc.query(anyString(), any(RowMapper.class), eq(id))).thenAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      if (sql.contains("from arquivo_publicidade_veiculacao where id")) {
        return List.of(base);
      }
      if (sql.contains("from arquivo_publicidade_versao where veiculacao_id")) {
        return List.of(versao);
      }
      return List.of();
    });
    @SuppressWarnings("unchecked")
    ObjectProvider<ObjectStorage> storage = mock(ObjectProvider.class);
    var service = new AdminArquivoPublicidadeService(
        jdbc, mapper, storage, new R2StorageProperties(), audit);

    Detalhe operacional = service.detalhar(id, ator, "req-1", FinalidadeAcessoArquivoPublicidade.AUDITORIA_INTERNA, false);
    assertTrue(operacional.versoes().get(0).contratante().isNull());
    assertEquals("Sintetico", operacional.versoes().get(0).conteudo().path("titulo").asText());
    assertTrue(operacional.versoes().get(0).conteudo().path("whatsapp_normalizado").isMissingNode());
    assertTrue(operacional.versoes().get(0).conteudo().path("link_conteudo").isMissingNode());
    assertTrue(operacional.versoes().get(0).conteudo().path("localizacao").path("endereco_resumido").isMissingNode());
    assertTrue(operacional.versoes().get(0).comercial().path("movimentoCreditoId").isMissingNode());
    verify(audit).registrar(ator, id, "ARQUIVO_PUBLICIDADE_DETALHE_CONSULTADO", "req-1", FinalidadeAcessoArquivoPublicidade.AUDITORIA_INTERNA);

    Detalhe exportado = service.detalhar(id, ator, "req-2", FinalidadeAcessoArquivoPublicidade.AUDITORIA_INTERNA, true);
    assertEquals("00000000000", exportado.versoes().get(0).contratante().path("cpf").asText());
    assertEquals("+5511999999999", exportado.versoes().get(0).conteudo().path("whatsapp_normalizado").asText());
    verify(audit).registrar(ator, id, "ARQUIVO_PUBLICIDADE_EXPORTACAO_PREPARADA", "req-2", FinalidadeAcessoArquivoPublicidade.AUDITORIA_INTERNA);
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void midiaExigeIdentidadeArquivadaExataEStorageDisponivel() throws Exception {
    UUID veiculacao = UUID.randomUUID();
    UUID versao = UUID.randomUUID();
    UUID anuncioMidia = UUID.randomUUID();
    UUID midia = UUID.randomUUID();
    UUID ator = UUID.randomUUID();
    byte[] bytes = {1, 2, 3};
    String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    String chave = "hml/qa/arquivo-publicidade/" + versao + "/" + anuncioMidia + "/original";
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    ResultSet rs = mock(ResultSet.class);
    when(rs.getObject("origem_versao_id", UUID.class)).thenReturn(versao);
    when(rs.getObject("anuncio_midia_id", UUID.class)).thenReturn(anuncioMidia);
    when(rs.getString("variante")).thenReturn("ORIGINAL");
    when(rs.getString("storage_provider")).thenReturn("R2");
    when(rs.getString("bucket")).thenReturn("bucket-qa");
    when(rs.getString("chave_privada")).thenReturn(chave);
    when(rs.getString("sha256")).thenReturn(hash);
    when(rs.getString("mime_type")).thenReturn("image/png");
    when(rs.getLong("tamanho_bytes")).thenReturn((long) bytes.length);
    when(jdbc.query(anyString(), any(RowMapper.class), eq(veiculacao), eq(midia),
        eq(veiculacao), eq(midia)))
        .thenAnswer(call -> List.of(((RowMapper<?>) call.getArgument(1)).mapRow(rs, 0)));
    ObjectProvider<ObjectStorage> provider = mock(ObjectProvider.class);
    ObjectStorage storage = mock(ObjectStorage.class);
    R2StorageProperties properties = new R2StorageProperties();
    properties.setPrivateMediaBucket("bucket-qa");
    properties.setPrivateMediaPrefix("hml/qa/");
    var audit = mock(AdminArquivoPublicidadeAccessAuditService.class);
    var service = new AdminArquivoPublicidadeService(
        jdbc, new ObjectMapper(), provider, properties, audit);
    var finalidade = FinalidadeAcessoArquivoPublicidade.AUDITORIA_INTERNA;

    when(rs.getString("bucket")).thenReturn("bucket-errado");
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
        assertThrows(ResponseStatusException.class,
            () -> service.midia(veiculacao, midia, ator, "req-1", finalidade)).getStatusCode());
    verify(provider, never()).getIfAvailable();
    when(rs.getString("bucket")).thenReturn("bucket-qa");
    when(rs.getString("chave_privada")).thenReturn("hml/qa/outra-area/foto");
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
        assertThrows(ResponseStatusException.class,
            () -> service.midia(veiculacao, midia, ator, "req-2", finalidade)).getStatusCode());
    when(rs.getString("chave_privada")).thenReturn(chave);
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
        assertThrows(ResponseStatusException.class,
            () -> service.midia(veiculacao, midia, ator, "req-3", finalidade)).getStatusCode());
    when(provider.getIfAvailable()).thenReturn(storage);
    when(storage.get(StorageArea.PRIVATE_MEDIA, chave)).thenReturn(new StoredObject(bytes, "image/png"));
    assertEquals(bytes.length, service.midia(veiculacao, midia, ator, "req-4", finalidade).bytes().length);
    verify(audit).registrarMidia(ator, veiculacao, midia,
        "ARQUIVO_PUBLICIDADE_MIDIA_PREPARADA", "req-4", finalidade);
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void referenciaAutorizaVersaoAtualEValidaChaveDaCopiaOriginal() throws Exception {
    UUID veiculacao = UUID.randomUUID();
    UUID versaoOriginal = UUID.randomUUID();
    UUID vinculo = UUID.randomUUID();
    UUID referencia = UUID.randomUUID();
    UUID ator = UUID.randomUUID();
    byte[] bytes = {9, 8, 7};
    String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    String chave = "hml/qa/arquivo-publicidade/" + versaoOriginal + "/" + vinculo + "/original";
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    ResultSet rs = mock(ResultSet.class);
    when(rs.getObject("origem_versao_id", UUID.class)).thenReturn(versaoOriginal);
    when(rs.getObject("anuncio_midia_id", UUID.class)).thenReturn(vinculo);
    when(rs.getString("variante")).thenReturn("ORIGINAL");
    when(rs.getString("storage_provider")).thenReturn("R2");
    when(rs.getString("bucket")).thenReturn("bucket-qa");
    when(rs.getString("chave_privada")).thenReturn(chave);
    when(rs.getString("sha256")).thenReturn(hash);
    when(rs.getString("mime_type")).thenReturn("image/png");
    when(rs.getLong("tamanho_bytes")).thenReturn((long) bytes.length);
    when(jdbc.query(anyString(), any(RowMapper.class), eq(veiculacao), eq(referencia),
        eq(veiculacao), eq(referencia))).thenAnswer(call -> {
          String sql = call.getArgument(0);
          assertTrue(sql.contains("v.id = r.versao_id"));
          assertTrue(sql.contains("m.anuncio_midia_id = r.anuncio_midia_id"));
          assertTrue(sql.contains("m.variante = r.variante"));
          assertTrue(sql.contains("destino_j.anuncio_id = origem_j.anuncio_id"));
          return List.of(((RowMapper<?>) call.getArgument(1)).mapRow(rs, 0));
        });
    ObjectProvider<ObjectStorage> provider = mock(ObjectProvider.class);
    ObjectStorage storage = mock(ObjectStorage.class);
    when(provider.getIfAvailable()).thenReturn(storage);
    when(storage.get(StorageArea.PRIVATE_MEDIA, chave)).thenReturn(new StoredObject(bytes, "image/png"));
    R2StorageProperties properties = new R2StorageProperties();
    properties.setPrivateMediaBucket("bucket-qa");
    properties.setPrivateMediaPrefix("hml/qa/");
    var audit = mock(AdminArquivoPublicidadeAccessAuditService.class);
    var service = new AdminArquivoPublicidadeService(
        jdbc, new ObjectMapper(), provider, properties, audit);

    assertEquals(bytes.length, service.midia(veiculacao, referencia, ator, "req-ref",
        FinalidadeAcessoArquivoPublicidade.AUDITORIA_INTERNA).bytes().length);
    verify(audit).registrarMidia(ator, veiculacao, referencia,
        "ARQUIVO_PUBLICIDADE_MIDIA_PREPARADA", "req-ref",
        FinalidadeAcessoArquivoPublicidade.AUDITORIA_INTERNA);
  }
}
