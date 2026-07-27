package br.com.topsdojob.v3.persistence.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ChatV036ContractTest {

    @Test
    void migrationCriaParUnicoIdempotenciaEProtegeParticipantes() throws Exception {
        String sql = Files.readString(Path.of(
                "src",
                "main",
                "resources",
                "db",
                "migration",
                "V036__chat_direto_entre_usuarios.sql"));

        assertThat(sql)
                .contains("CREATE TABLE chat_conversa")
                .contains("CREATE TABLE chat_mensagem")
                .contains("UNIQUE (participante_a_id, participante_b_id)")
                .contains("UNIQUE (remetente_usuario_id, idempotency_key)")
                .contains("validar_participantes_chat_mensagem")
                .contains("WHERE lido_em IS NULL")
                .contains("char_length(corpo) BETWEEN 1 AND 2000")
                .doesNotContain("mensagem_suporte")
                .doesNotContain("ON DELETE CASCADE");
    }

    @Test
    void openApiDocumentaSomenteOsContratosCanonicosDoChatDireto() throws Exception {
        String openApi = Files.readString(Path.of(
                "..",
                "contracts",
                "openapi",
                "topsdojob-v3-local.yaml"));

        assertThat(openApi)
                .contains("/api/public/chat/conversas:")
                .contains("/api/public/chat/conversas/{conversaId}/mensagens:")
                .contains("/api/public/chat/conversas/{conversaId}/leitura:")
                .contains("/api/public/chat/nao-lidas:")
                .contains("ChatConversaDetalhe:")
                .contains("ChatEnviarMensagemRequest:")
                .doesNotContain("/api/public/chat/anexos")
                .doesNotContain("/api/public/chat/bloqueios");
    }
}
