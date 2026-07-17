package br.com.topsdojob.v3.importacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CredenciaisUsuariosDryRunImportadorTest {

    private static final Path IMPORTACAO = Path.of("..", "scripts", "local", "importacao");

    @Test
    void reconciliadorPreservaSomenteBcryptComIdentidadeInequivoca() throws Exception {
        String sql = Files.readString(IMPORTACAO.resolve("reconciliar-credenciais-usuarios.sql"));
        String classificacaoCredencial = sql.substring(
                sql.indexOf("WHEN o.usuario_v3_id IS NULL"),
                sql.indexOf("END AS classificacao"));

        assertThat(sql)
                .contains("^\\$2a\\$10\\$[./A-Za-z0-9]{53}$")
                .contains("cred.senha_hash <> c.hash_origem")
                .contains("credencial V3 existente diverge do snapshot")
                .contains("md5('legacy:credencial:' || candidato.origem_id)::uuid")
                .contains("ON CONFLICT DO NOTHING")
                .contains("versaoImportadorCredencial', 'credenciais-usuarios-v2'")
                .doesNotContain("passwordEncoder.encode")
                .doesNotContain("UPDATE credencial_usuario")
                .doesNotContain("DELETE FROM credencial_usuario");
        assertThat(classificacaoCredencial)
                .doesNotContain("status_origem")
                .doesNotContain("email_verificado")
                .doesNotContain("segundo_fator_ativo");
    }

    @Test
    void hashValidoEImportadoMesmoQuandoAcessoEstaPendenteOuSegundoFatorFoiDesativado() throws Exception {
        String sql = Files.readString(IMPORTACAO.resolve("reconciliar-credenciais-usuarios.sql"));

        assertThat(sql)
                .contains("RECUSADO_CONTA_DESATIVADA")
                .contains("RECUSADO_EMAIL_PENDENTE")
                .contains("PERMITIDO_SENHA")
                .contains("'segundoFatorLegadoDesativadoV3', c.segundo_fator_ativo")
                .contains("WHERE c.classificacao <> 'ELEGIVEL'")
                .doesNotContain("decisao de desativacao do segundo fator so autoriza contas USUARIO")
                .doesNotContain("conta com segundo fator fora do papel USUARIO")
                .doesNotContain("SEGUNDO_FATOR_NAO_SUPORTADO")
                .doesNotContain("CREDENCIAL_SEGUNDO_FATOR_PENDENTE");
    }

    @Test
    void stagingEManifestoRecebemSomenteMetadadosSanitizados() throws Exception {
        String sql = Files.readString(IMPORTACAO.resolve("reconciliar-credenciais-usuarios.sql"));
        String staging = sql.substring(
                sql.indexOf("UPDATE stg_usuario"),
                sql.indexOf("INSERT INTO importacao_pendencia"));

        assertThat(staging)
                .contains("credencialAlgoritmo")
                .contains("credencialHashComprimento")
                .contains("credencialClassificacao")
                .doesNotContain("hash_origem")
                .doesNotContain("senha_hash")
                .doesNotContain("'emailOrigem'")
                .doesNotContain("'emailNormalizado'");
        assertThat(sql)
                .contains("'sessoesImportadas', 0")
                .contains("'tokensImportados', 0");
    }

    @Test
    void validadorExigeHashExatoSemSessaoTokenOuDuplicidade() throws Exception {
        String sql = Files.readString(IMPORTACAO.resolve("validar-credenciais-usuarios.sql"));

        assertThat(sql)
                .contains("cred.senha_hash <> c.hash_origem")
                .contains("credencial invalida ou inconsistente foi promovida")
                .contains("SEGUNDO_FATOR_LEGADO_DESATIVADO_V3|")
                .contains("usuario recebeu credenciais duplicadas")
                .contains("sessao ou token foi importado indevidamente")
                .contains("CREDENCIAL_FINGERPRINT|");
    }
}
