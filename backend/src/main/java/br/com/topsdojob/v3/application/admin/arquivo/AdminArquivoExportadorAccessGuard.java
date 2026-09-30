package br.com.topsdojob.v3.application.admin.arquivo;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Confere o vinculo atual antes de entregar conteudo integral do arquivo. */
final class AdminArquivoExportadorAccessGuard {
  private AdminArquivoExportadorAccessGuard() {
  }

  static void exigirAcessoAtual(JdbcTemplate jdbc, UUID atorId) {
    Boolean permitido = jdbc.queryForObject("""
        SELECT EXISTS (
          SELECT 1
          FROM usuario u
          JOIN papel_usuario admin ON admin.usuario_id = u.id AND admin.papel = 'ADMIN'
          JOIN papel_usuario exportador
            ON exportador.usuario_id = u.id AND exportador.papel = 'ARQUIVO_EXPORTADOR'
          WHERE u.id = ?
            AND u.tipo_conta = 'STAFF'
            AND u.status = 'ATIVO'
            AND u.desativado_em IS NULL
        )
        """, Boolean.class, atorId);
    if (!Boolean.TRUE.equals(permitido)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "acesso ao arquivo para exportacao negado");
    }
  }
}
