package br.com.topsdojob.v3.persistence.repository.chat;

import br.com.topsdojob.v3.persistence.entity.chat.ChatConversaEntity;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatConversaRepository extends JpaRepository<ChatConversaEntity, UUID> {

    Optional<ChatConversaEntity> findByParticipanteAIdAndParticipanteBId(
            UUID participanteAId,
            UUID participanteBId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select conversa from ChatConversaEntity conversa where conversa.id = :id")
    Optional<ChatConversaEntity> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query(value = """
            INSERT INTO chat_conversa (
              id,
              participante_a_id,
              participante_b_id,
              criado_request_id,
              criado_em,
              atualizado_em,
              versao
            )
            VALUES (:id, :participanteAId, :participanteBId, :requestId, :agora, :agora, 0)
            ON CONFLICT (participante_a_id, participante_b_id) DO NOTHING
            """, nativeQuery = true)
    int inserirSeAusente(
            @Param("id") UUID id,
            @Param("participanteAId") UUID participanteAId,
            @Param("participanteBId") UUID participanteBId,
            @Param("requestId") String requestId,
            @Param("agora") OffsetDateTime agora);

    @Query(value = """
            SELECT
              conversa.id AS id,
              participante.nome AS participanteUsername,
              ultima.corpo AS ultimaMensagem,
              ultima.criado_em AS ultimaMensagemEm,
              (
                SELECT count(*)
                FROM chat_mensagem nao_lida
                WHERE nao_lida.conversa_id = conversa.id
                  AND nao_lida.destinatario_usuario_id = :usuarioId
                  AND nao_lida.lido_em IS NULL
              ) AS naoLidas
            FROM chat_conversa conversa
            JOIN usuario participante
              ON participante.id = CASE
                WHEN conversa.participante_a_id = :usuarioId THEN conversa.participante_b_id
                ELSE conversa.participante_a_id
              END
            LEFT JOIN LATERAL (
              SELECT mensagem.corpo, mensagem.criado_em
              FROM chat_mensagem mensagem
              WHERE mensagem.conversa_id = conversa.id
              ORDER BY mensagem.criado_em DESC, mensagem.id DESC
              LIMIT 1
            ) ultima ON true
            WHERE conversa.participante_a_id = :usuarioId
               OR conversa.participante_b_id = :usuarioId
            ORDER BY conversa.atualizado_em DESC, conversa.id
            """, nativeQuery = true)
    List<Object[]> listarResumos(@Param("usuarioId") UUID usuarioId);
}
