package br.com.topsdojob.v3.application.admin.arquivo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoPublicidadeDtos.Versao;
import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoStoryDtos.Detalhe;
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

class AdminArquivoStoryServiceTest {
  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void detalheOcultaContratanteEExportacaoPreservaComFinalidadeAuditada() {
    UUID id = UUID.randomUUID();
    UUID ator = UUID.randomUUID();
    ObjectMapper mapper = new ObjectMapper();
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    AdminArquivoStoryAccessAuditService audit = mock(AdminArquivoStoryAccessAuditService.class);
    var finalidade = FinalidadeAcessoArquivoPublicidade.ATENDIMENTO_FISCALIZACAO;
    Detalhe base = new Detalhe(id, UUID.randomUUID(), null, UUID.randomUUID(),
        UUID.randomUUID(), null, null, null, "MIDIA_UPLOAD", "REMUNERADA", "SIM",
        "ABRANGIDA", OffsetDateTime.parse("2026-09-25T10:00:00Z"),
        OffsetDateTime.parse("2026-09-26T10:00:00Z"),
        OffsetDateTime.parse("2027-09-26T10:00:00Z"), "LIMITE_STORY", List.of());
    var conteudo = mapper.createObjectNode().put("titulo", "Story sintetico")
        .put("whatsapp_normalizado", "+5511988888888")
        .put("link_conteudo", "https://example.invalid/segredo");
    var comercial = mapper.createObjectNode().put("movimentoCreditoId", UUID.randomUUID().toString());
    Versao versao = new Versao(UUID.randomUUID(), 1,
        OffsetDateTime.parse("2026-09-25T10:00:00Z"),
        OffsetDateTime.parse("2026-09-25T10:00:00Z"),
        OffsetDateTime.parse("2026-09-26T10:00:00Z"), "PUBLICACAO",
        conteudo,
        mapper.createObjectNode().put("cpf", "00000000000"),
        comercial, mapper.createObjectNode(), mapper.createObjectNode(),
        "0".repeat(64), List.of());
    when(jdbc.query(anyString(), any(RowMapper.class), eq(id))).thenAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      if (sql.contains("from arquivo_publicidade_story_veiculacao where id")) return List.of(base);
      if (sql.contains("from arquivo_publicidade_story_versao")) return List.of(versao);
      return List.of();
    });
    @SuppressWarnings("unchecked")
    ObjectProvider<ObjectStorage> storage = mock(ObjectProvider.class);
    var service = new AdminArquivoStoryService(
        jdbc, mapper, storage, new R2StorageProperties(), audit);

    Detalhe operacional = service.detalhar(id, ator, "req-1", finalidade, false);
    assertTrue(operacional.versoes().get(0).contratante().isNull());
    assertEquals("Story sintetico", operacional.versoes().get(0).conteudo().path("titulo").asText());
    assertTrue(operacional.versoes().get(0).conteudo().path("whatsapp_normalizado").isMissingNode());
    assertTrue(operacional.versoes().get(0).conteudo().path("link_conteudo").isMissingNode());
    assertTrue(operacional.versoes().get(0).comercial().path("movimentoCreditoId").isMissingNode());
    verify(audit).registrar(ator, id, "ARQUIVO_PUBLICIDADE_STORY_DETALHE_CONSULTADO",
        "req-1", finalidade);

    Detalhe exportado = service.detalhar(id, ator, "req-2", finalidade, true);
    assertEquals("00000000000", exportado.versoes().get(0).contratante().path("cpf").asText());
    assertEquals("+5511988888888", exportado.versoes().get(0).conteudo().path("whatsapp_normalizado").asText());
    verify(audit).registrar(ator, id, "ARQUIVO_PUBLICIDADE_STORY_EXPORTACAO_PREPARADA",
        "req-2", finalidade);
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void midiaDiretaExigeBucketEChaveDaVersaoArquivada() throws Exception {
    UUID veiculacao = UUID.randomUUID();
    UUID versao = UUID.randomUUID();
    UUID arquivoMidia = UUID.randomUUID();
    UUID midia = UUID.randomUUID();
    UUID ator = UUID.randomUUID();
    byte[] bytes = {4, 5, 6};
    String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    String chave = "hml/qa/arquivo-publicidade/stories/" + versao
        + "/direta/" + arquivoMidia + "/original";
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    ResultSet rs = mock(ResultSet.class);
    when(rs.getObject("versao_id", UUID.class)).thenReturn(versao);
    when(rs.getObject("arquivo_midia_id", UUID.class)).thenReturn(arquivoMidia);
    when(rs.getString("variante")).thenReturn("ORIGINAL");
    when(rs.getString("storage_provider")).thenReturn("R2");
    when(rs.getString("bucket")).thenReturn("bucket-qa");
    when(rs.getString("chave_privada")).thenReturn(chave);
    when(rs.getString("sha256")).thenReturn(hash);
    when(rs.getString("mime_type")).thenReturn("image/png");
    when(rs.getLong("tamanho_bytes")).thenReturn((long) bytes.length);
    when(jdbc.query(anyString(), any(RowMapper.class), eq(veiculacao), eq(midia)))
        .thenAnswer(call -> List.of(((RowMapper<?>) call.getArgument(1)).mapRow(rs, 0)));
    ObjectProvider<ObjectStorage> provider = mock(ObjectProvider.class);
    ObjectStorage storage = mock(ObjectStorage.class);
    when(provider.getIfAvailable()).thenReturn(storage);
    R2StorageProperties properties = new R2StorageProperties();
    properties.setPrivateMediaBucket("bucket-qa");
    properties.setPrivateMediaPrefix("hml/qa/");
    var audit = mock(AdminArquivoStoryAccessAuditService.class);
    var service = new AdminArquivoStoryService(jdbc, new ObjectMapper(), provider, properties, audit);
    var finalidade = FinalidadeAcessoArquivoPublicidade.AUDITORIA_INTERNA;

    when(rs.getString("chave_privada")).thenReturn("hml/qa/outra-area/foto");
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
        assertThrows(ResponseStatusException.class,
            () -> service.midia(veiculacao, midia, ator, "req-1", finalidade)).getStatusCode());
    when(rs.getString("chave_privada")).thenReturn(chave);
    when(storage.get(StorageArea.PRIVATE_MEDIA, chave)).thenReturn(new StoredObject(bytes, "image/png"));
    assertEquals(bytes.length, service.midia(veiculacao, midia, ator, "req-2", finalidade).bytes().length);
    verify(audit).registrar(ator, veiculacao, "ARQUIVO_PUBLICIDADE_STORY_MIDIA_PREPARADA",
        "req-2", finalidade);
  }
}
