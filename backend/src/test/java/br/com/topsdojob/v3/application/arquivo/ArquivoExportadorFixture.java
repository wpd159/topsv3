package br.com.topsdojob.v3.application.arquivo;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class ArquivoExportadorFixture {
  private ArquivoExportadorFixture() {
  }

  static UUID inserir(JdbcTemplate jdbc) {
    UUID id = UUID.randomUUID();
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    jdbc.update("""
        INSERT INTO usuario (id, nome, email_normalizado, status, tipo_conta,
          email_verificado_em, criado_em, atualizado_em, versao)
        VALUES (?, 'Exportador sintetico', ?, 'ATIVO', 'STAFF', ?, ?, ?, 0)
        """, id, "exportador-" + id + "@example.invalid", agora, agora, agora);
    jdbc.update("""
        INSERT INTO papel_usuario (usuario_id, papel, criado_em)
        VALUES (?, 'ADMIN', ?), (?, 'ARQUIVO_EXPORTADOR', ?)
        """, id, agora, id, agora);
    return id;
  }
}
