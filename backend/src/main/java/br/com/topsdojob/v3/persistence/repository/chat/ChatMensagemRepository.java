package br.com.topsdojob.v3.persistence.repository.chat;

import br.com.topsdojob.v3.persistence.entity.chat.ChatMensagemEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMensagemRepository extends JpaRepository<ChatMensagemEntity, UUID> {

    List<ChatMensagemEntity> findByConversaIdOrderByCriadoEmAscIdAsc(UUID conversaId);

    Optional<ChatMensagemEntity> findByRemetenteUsuarioIdAndIdempotencyKey(
            UUID remetenteUsuarioId,
            String idempotencyKey);

    long countByDestinatarioUsuarioIdAndLidoEmIsNull(UUID destinatarioUsuarioId);

    @Modifying
    @Query(value = """
            INSERT INTO chat_mensagem (
              id,
              conversa_id,
              remetente_usuario_id,
              destinatario_usuario_id,
              corpo,
              idempotency_key,
              request_id,
              criado_em,
              lido_em
            )
            VALUES (
              :id,
              :conversaId,
              :remetenteId,
              :destinatarioId,
              :corpo,
              :idempotencyKey,
              :requestId,
              :agora,
              NULL
            )
            ON CONFLICT (remetente_usuario_id, idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int inserirSeAusente(
            @Param("id") UUID id,
            @Param("conversaId") UUID conversaId,
            @Param("remetenteId") UUID remetenteId,
            @Param("destinatarioId") UUID destinatarioId,
            @Param("corpo") String corpo,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestId") String requestId,
            @Param("agora") OffsetDateTime agora);

    @Modifying
    @Query("""
            update ChatMensagemEntity mensagem
            set mensagem.lidoEm = :agora
            where mensagem.conversaId = :conversaId
              and mensagem.destinatarioUsuarioId = :usuarioId
              and mensagem.lidoEm is null
            """)
    int marcarComoLidas(
            @Param("conversaId") UUID conversaId,
            @Param("usuarioId") UUID usuarioId,
            @Param("agora") OffsetDateTime agora);
}
