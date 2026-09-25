package br.com.topsdojob.v3.application.arquivo;

import static br.com.topsdojob.v3.application.publico.anunciante.midia.LimiteMidiasAnuncioService.FOTOS_BASE;
import static br.com.topsdojob.v3.application.publico.anunciante.midia.LimiteMidiasAnuncioService.FOTOS_COM_EXTRA;

import br.com.topsdojob.v3.application.publico.mapper.SelecaoMidiasPublicas;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoFlagsDto;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoMapper;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.repository.projection.MidiaVinculoLeitura;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Prospective archive. Never reconstructs a past version from the current ad row. */
@Service
public class ArquivoPublicidadeRegistroService {
  private static final Logger LOG = LoggerFactory.getLogger(ArquivoPublicidadeRegistroService.class);
  private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

  private final NamedParameterJdbcTemplate jdbc;
  private final EntityManager entityManager;
  private final ObjectMapper mapper;
  private final ObjectProvider<ObjectStorage> storageProvider;
  private final R2StorageProperties storageProperties;
  private final ArquivoPublicidadeStoryRegistroService stories;
  private final PremiumPublicoMapper premiumPublico;

  public ArquivoPublicidadeRegistroService(
      NamedParameterJdbcTemplate jdbc,
      EntityManager entityManager,
      ObjectMapper mapper,
      ObjectProvider<ObjectStorage> storageProvider,
      R2StorageProperties storageProperties,
      ArquivoPublicidadeStoryRegistroService stories,
      PremiumPublicoMapper premiumPublico) {
    this.jdbc = jdbc;
    this.entityManager = entityManager;
    this.mapper = mapper;
    this.storageProvider = storageProvider;
    this.storageProperties = storageProperties;
    this.stories = stories;
    this.premiumPublico = premiumPublico;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void registrarEstado(UUID anuncioId, String motivo, String requestId, OffsetDateTime instante) {
    Objects.requireNonNull(anuncioId, "anuncioId");
    Objects.requireNonNull(instante, "instante");
    if (motivo == null || motivo.isBlank()) {
      throw new IllegalArgumentException("motivo obrigatorio para arquivo publicitario");
    }
    entityManager.flush();
    Map<String, Object> anuncio = unico("""
        SELECT a.id, a.usuario_id, a.slug, a.titulo, a.descricao, a.status,
               a.status_moderacao, a.categoria, a.preco, a.whatsapp_normalizado,
               a.link_conteudo, a.publicado_em, a.ultima_publicacao_em,
               a.atendimento_exclusivamente_virtual, a.removido_em,
               u.status AS usuario_status, u.tipo_conta, u.desativado_em, u.excluido_em,
               u.nome, u.nome_civil, u.cpf_normalizado, u.email_normalizado
        FROM anuncio a JOIN usuario u ON u.id = a.usuario_id
        WHERE a.id = :anuncioId FOR UPDATE OF a
        """, Map.of("anuncioId", anuncioId));
    if (anuncio == null) {
      return;
    }
    // A concurrent command may have waited for the ad lock. Never assign its old
    // request timestamp to a state observed only after a newer archive event.
    instante = jdbc.queryForObject("""
        SELECT GREATEST(
          clock_timestamp(), CAST(:instante AS timestamptz),
          COALESCE((SELECT max(inicio_em) FROM arquivo_publicidade_veiculacao
            WHERE anuncio_id = :anuncioId), CAST(:instante AS timestamptz)),
          COALESCE((SELECT max(v.vigente_desde) FROM arquivo_publicidade_versao v
            JOIN arquivo_publicidade_veiculacao j ON j.id = v.veiculacao_id
            WHERE j.anuncio_id = :anuncioId), CAST(:instante AS timestamptz)))
        """, Map.of("anuncioId", anuncioId, "instante", instante), OffsetDateTime.class);
    boolean publicada = "PUBLICADO".equals(anuncio.get("status"))
        && "APROVADO".equals(anuncio.get("status_moderacao"))
        && anuncio.get("removido_em") == null
        && "ATIVO".equals(anuncio.get("usuario_status"))
        && "ANUNCIANTE".equals(anuncio.get("tipo_conta"))
        && anuncio.get("desativado_em") == null
        && anuncio.get("excluido_em") == null;
    Set<UUID> ativacoesComEfeitoPublico = publicada
        ? premiumPublico.idsAtivacoesComEfeitoPublico(anuncioId) : Set.of();
    List<Map<String, Object>> ativacoes = publicada
        ? ativacoesVigentes(anuncioId, instante).stream()
            .filter(ativacao -> ativacoesComEfeitoPublico.contains(ativacao.get("id")))
            .toList()
        : List.of();
    List<Map<String, Object>> midias = ativacoes.isEmpty() ? List.of() : midiasExibiveis(anuncioId);
    List<Map<String, Object>> abertas = jdbc.queryForList("""
        SELECT id, ativacao_beneficio_id, classificacao, relacao_material, cobertura,
               inicio_em, fim_em
        FROM arquivo_publicidade_veiculacao
        WHERE anuncio_id = :anuncioId AND (fim_em IS NULL OR fim_em > :instante)
        ORDER BY id FOR UPDATE
        """, Map.of("anuncioId", anuncioId, "instante", instante));
    Map<UUID, Map<String, Object>> abertasPorAtivacao = new HashMap<>();
    for (Map<String, Object> aberta : abertas) {
      abertasPorAtivacao.put((UUID) aberta.get("ativacao_beneficio_id"), aberta);
    }

    if (!publicada) {
      for (Map<String, Object> aberta : abertas) {
        encerrar((UUID) aberta.get("id"), instante, motivo);
      }
      stories.registrarEstadoPorAnuncio(anuncioId, motivo, requestId, instante);
      return;
    }

    for (Map<String, Object> ativacao : ativacoes) {
      UUID ativacaoId = (UUID) ativacao.get("id");
      atualizarJanela(anuncio, midias, ativacao, Classificacao.de(ativacao),
          abertasPorAtivacao.remove(ativacaoId), motivo, requestId, instante);
    }
    for (Map<String, Object> encerrada : abertasPorAtivacao.values()) {
      encerrar((UUID) encerrada.get("id"), instante, motivo);
    }
    stories.registrarEstadoPorAnuncio(anuncioId, motivo, requestId, instante);
  }

  private void atualizarJanela(
      Map<String, Object> anuncio,
      List<Map<String, Object>> midias,
      Map<String, Object> ativacao,
      Classificacao classe,
      Map<String, Object> aberta,
      String motivo,
      String requestId,
      OffsetDateTime instante) {
    if (aberta != null && (!classe.nome().equals(aberta.get("classificacao"))
        || !classe.relacaoMaterial().equals(aberta.get("relacao_material"))
        || !classe.cobertura().equals(aberta.get("cobertura")))) {
      encerrar((UUID) aberta.get("id"), instante, "CLASSIFICACAO_ALTERADA");
      aberta = null;
    }
    UUID janelaId;
    if (aberta == null) {
      janelaId = UUID.randomUUID();
      Map<String, Object> parametros = new HashMap<>();
      parametros.put("id", janelaId);
      parametros.put("anuncioId", anuncio.get("id"));
      parametros.put("usuarioId", anuncio.get("usuario_id"));
      parametros.put("ativacaoId", ativacao == null ? null : ativacao.get("id"));
      parametros.put("grupoId", ativacao == null ? null : ativacao.get("grupo_ativacao_id"));
      parametros.put("movimentoId", ativacao == null ? null : ativacao.get("movimento_id"));
      parametros.put("classificacao", classe.nome());
      parametros.put("relacao", classe.relacaoMaterial());
      parametros.put("cobertura", classe.cobertura());
      parametros.put("instante", instante);
      parametros.put("limite", ativacao == null ? null : ativacao.get("fim_em"));
      parametros.put("encerramento", ativacao == null ? null : "LIMITE_AUTOMATICO_ATIVACAO");
      jdbc.update("""
          INSERT INTO arquivo_publicidade_veiculacao (
            id, anuncio_id, contratante_usuario_id, ativacao_beneficio_id,
            grupo_ativacao_id, movimento_credito_id, pagamento_id, classificacao,
            relacao_material, cobertura, inicio_em, fim_em, retencao_ate,
            encerramento_motivo, criado_em, atualizado_em)
          VALUES (:id, :anuncioId, :usuarioId, :ativacaoId,
            :grupoId, :movimentoId, NULL, :classificacao,
            :relacao, :cobertura, :instante, :limite,
            CASE WHEN :cobertura IN ('ABRANGIDA', 'PREVENTIVA')
                AND CAST(:limite AS timestamptz) IS NOT NULL
              THEN CAST(:limite AS timestamptz) + INTERVAL '1 year' ELSE NULL END,
            :encerramento, :instante, :instante)
          """, parametros);
    } else {
      janelaId = (UUID) aberta.get("id");
    }
    criarVersaoSeMudou(janelaId, anuncio, midias, ativacao, classe, motivo, requestId, instante);
  }

  private void criarVersaoSeMudou(
      UUID janelaId,
      Map<String, Object> anuncio,
      List<Map<String, Object>> midias,
      Map<String, Object> ativacao,
      Classificacao classe,
      String motivo,
      String requestId,
      OffsetDateTime instante) {
    boolean completa = !"NAO_ABRANGIDA".equals(classe.cobertura());
    Map<String, Object> conteudo = new LinkedHashMap<>();
    conteudo.put("estado", completa ? "CAPTURADO_NA_EXIBICAO" : "METADADOS_OPERACIONAIS");
    conteudo.put("anuncioId", anuncio.get("id"));
    if (completa) {
      for (String campo : List.of("slug", "titulo", "descricao", "categoria", "preco",
          "whatsapp_normalizado", "link_conteudo", "atendimento_exclusivamente_virtual")) {
        conteudo.put(campo, anuncio.get(campo));
      }
      conteudo.put("urlPublicaNaCaptura", "/anuncios/" + anuncio.get("slug"));
      conteudo.put("produtoServicoMarca", "NAO_DISPONIVEL_EM_CAMPO_ESTRUTURADO");
      UUID anuncioId = (UUID) anuncio.get("id");
      conteudo.put("localizacao", unico("""
          SELECT estado_id, cidade_id, bairro_id, endereco_resumido
          FROM anuncio_localizacao WHERE anuncio_id = :anuncioId
          """, Map.of("anuncioId", anuncioId)));
      conteudo.put("servicos", valores("SELECT servico FROM anuncio_servicos WHERE anuncio_id = :anuncioId ORDER BY servico", anuncioId));
      conteudo.put("locaisAtendimento", valores("SELECT local_atendimento FROM anuncio_local_atendimento WHERE anuncio_id = :anuncioId ORDER BY local_atendimento", anuncioId));
      conteudo.put("midias", midias.stream().map(m -> Map.of(
          "anuncioMidiaId", m.get("id"),
          "arquivoMidiaId", m.get("arquivo_midia_id"),
          "tipo", m.get("tipo"),
          "finalidade", m.get("finalidade"),
          "ordem", m.get("ordem"),
          "visibilidade", m.get("visibilidade_midia"))).toList());
    }
    Map<String, Object> contratante = new LinkedHashMap<>();
    if (completa) {
      contratante.put("usuarioId", anuncio.get("usuario_id"));
      contratante.put("nome", anuncio.get("nome"));
      contratante.put("nomeCivil", anuncio.get("nome_civil"));
      contratante.put("cpf", anuncio.get("cpf_normalizado"));
      contratante.put("email", anuncio.get("email_normalizado"));
      contratante.put("terceiroBeneficiario", "NAO_REGISTRADO");
    }
    Map<String, Object> comercial = new LinkedHashMap<>();
    comercial.put("classificacao", classe.nome());
    comercial.put("relacaoMaterial", classe.relacaoMaterial());
    comercial.put("cobertura", classe.cobertura());
    if (ativacao != null) {
      comercial.put("ativacaoBeneficioId", ativacao.get("id"));
      comercial.put("grupoAtivacaoId", ativacao.get("grupo_ativacao_id"));
      comercial.put("beneficioCodigo", ativacao.get("codigo"));
      comercial.put("origem", ativacao.get("origem"));
      comercial.put("movimentoCreditoId", ativacao.get("movimento_id"));
      comercial.put("pagamentoId", null);
      comercial.put("vinculoPagamento", "SALDO_FUNGIVEL_SEM_ALOCACAO_EXATA");
    }
    Map<String, Object> segmentacao = Map.of("estado", "NAO_AFERIDA_NA_CAPTURA");
    Map<String, Object> alcance = Map.of("estado", "NAO_MENSURADO", "destinatariosUnicos", "DESCONHECIDO");
    String conteudoJson = json(conteudo);
    String contratanteJson = json(contratante);
    String comercialJson = json(comercial);
    String segmentacaoJson = json(segmentacao);
    String alcanceJson = json(alcance);
    String hash = sha256((conteudoJson + contratanteJson + comercialJson
        + segmentacaoJson + alcanceJson).getBytes(StandardCharsets.UTF_8));
    OffsetDateTime limite = ativacao == null ? null : instanteJdbc(ativacao.get("fim_em"));
    Map<String, Object> anterior = unico("""
        SELECT id, numero, conteudo_sha256 FROM arquivo_publicidade_versao
        WHERE veiculacao_id = :janelaId
          AND (vigente_ate IS NULL OR vigente_ate > :instante)
        ORDER BY numero DESC LIMIT 1 FOR UPDATE
        """, Map.of("janelaId", janelaId, "instante", instante));
    if (anterior != null && hash.equals(anterior.get("conteudo_sha256"))) {
      return;
    }
    UUID versaoId = UUID.randomUUID();
    List<MidiaCopiada> copias = completa ? copiarMidias(versaoId, midias) : List.of();
    if (anterior != null) {
      jdbc.update("""
          UPDATE arquivo_publicidade_versao SET vigente_ate = :instante WHERE id = :id
          """, Map.of("instante", instante, "id", anterior.get("id")));
    }
    Map<String, Object> parametros = new HashMap<>();
    parametros.put("id", versaoId);
    parametros.put("janelaId", janelaId);
    parametros.put("numero", anterior == null ? 1 : ((Number) anterior.get("numero")).intValue() + 1);
    parametros.put("instante", instante);
    parametros.put("limite", limite);
    parametros.put("motivo", motivo);
    parametros.put("requestId", requestId);
    parametros.put("conteudo", conteudoJson);
    parametros.put("contratante", contratanteJson);
    parametros.put("comercial", comercialJson);
    parametros.put("segmentacao", segmentacaoJson);
    parametros.put("alcance", alcanceJson);
    parametros.put("hash", hash);
    jdbc.update("""
        INSERT INTO arquivo_publicidade_versao (
          id, veiculacao_id, numero, vigente_desde, vigente_ate,
          capturado_em, motivo, request_id,
          conteudo_json, contratante_json, comercial_json, segmentacao_json,
          alcance_json, conteudo_sha256)
        VALUES (:id, :janelaId, :numero, :instante, :limite,
          :instante, :motivo, :requestId,
          CAST(:conteudo AS jsonb), CAST(:contratante AS jsonb), CAST(:comercial AS jsonb),
          CAST(:segmentacao AS jsonb), CAST(:alcance AS jsonb), :hash)
        """, parametros);
    for (MidiaCopiada midia : copias) {
      Map<String, Object> midiaParametros = new HashMap<>();
      midiaParametros.put("id", midia.id());
      midiaParametros.put("versaoId", versaoId);
      midiaParametros.put("anuncioMidiaId", midia.anuncioMidiaId());
      midiaParametros.put("arquivoMidiaId", midia.arquivoMidiaId());
      midiaParametros.put("variante", midia.variante());
      midiaParametros.put("bucket", storageProperties.getPrivateMediaBucket());
      midiaParametros.put("chave", midia.chave());
      midiaParametros.put("sha256", midia.sha256());
      midiaParametros.put("mimeType", midia.mimeType());
      midiaParametros.put("tamanhoBytes", midia.tamanhoBytes());
      midiaParametros.put("ordem", midia.ordem());
      jdbc.update("""
          INSERT INTO arquivo_publicidade_midia (
            id, versao_id, anuncio_midia_id, arquivo_midia_id, variante,
            storage_provider, bucket, chave_privada, sha256, mime_type,
            tamanho_bytes, ordem)
          VALUES (:id, :versaoId, :anuncioMidiaId, :arquivoMidiaId, :variante,
            'R2', :bucket, :chave, :sha256, :mimeType, :tamanhoBytes, :ordem)
          """, midiaParametros);
    }
  }

  private List<MidiaCopiada> copiarMidias(UUID versaoId, List<Map<String, Object>> midias) {
    if (midias.isEmpty()) {
      return List.of();
    }
    ObjectStorage storage = storageProvider.getIfAvailable();
    if (storage == null || !storageProperties.isEnabled()
        || storageProperties.getPrivateMediaBucket() == null
        || storageProperties.getPrivateMediaPrefix() == null) {
      throw new IllegalStateException("storage privado indisponivel para arquivo publicitario");
    }
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException("captura de midia exige transacao ativa");
    }
    List<MidiaCopiada> copias = new java.util.ArrayList<>();
    for (Map<String, Object> midia : midias) {
      String bucket = String.valueOf(midia.get("bucket"));
      String chave = String.valueOf(midia.get("chave_objeto"));
      String provider = String.valueOf(midia.get("storage_provider"));
      String visibilidade = String.valueOf(midia.get("visibilidade_midia"));
      if (!"R2".equals(provider)) {
        throw new IllegalStateException("midia exibida fora do storage verificavel: " + midia.get("id"));
      }
      StorageArea area;
      if ("LIVRE".equals(visibilidade)
          && bucket.equals(storageProperties.getPublicMediaBucket())
          && chave.startsWith(storageProperties.getPublicMediaPrefix())) {
        area = StorageArea.PUBLIC_MEDIA;
      } else if ("LIVRE".equals(visibilidade)
          && bucket.equals(storageProperties.getPreservedPublicMediaBucket())
          && storageProperties.getPreservedPublicMediaPrefix() != null
          && chave.startsWith(storageProperties.getPreservedPublicMediaPrefix())) {
        area = StorageArea.PRESERVED_PUBLIC_MEDIA;
      } else if ("RESTRITA_18".equals(visibilidade)
          && bucket.equals(storageProperties.getPrivateMediaBucket())
          && chave.startsWith(storageProperties.getPrivateMediaPrefix())) {
        area = StorageArea.PRIVATE_MEDIA;
      } else {
        throw new IllegalStateException("origem de midia exibida nao verificavel: " + midia.get("id"));
      }
      copias.add(copiarUma(storage, versaoId, midia, "ORIGINAL", area, chave));
      if ("RESTRITA_18".equals(visibilidade)
          && "DISPONIVEL".equals(midia.get("preview_restrito_status"))) {
        String preview = (String) midia.get("preview_restrito_chave");
        if (preview == null || !preview.startsWith(storageProperties.getPublicMediaPrefix())) {
          throw new IllegalStateException("preview restrito exibido sem origem integra: " + midia.get("id"));
        }
        copias.add(copiarUma(storage, versaoId, midia,
            "PREVIEW_RESTRITO", StorageArea.PUBLIC_MEDIA, preview));
      }
    }
    return List.copyOf(copias);
  }

  private MidiaCopiada copiarUma(
      ObjectStorage storage,
      UUID versaoId,
      Map<String, Object> midia,
      String variante,
      StorageArea origem,
      String chaveOrigem) {
    StoredObject original = storage.get(origem, chaveOrigem);
    if (original == null || original.content() == null || original.content().length == 0
        || original.contentType() == null) {
      throw new IllegalStateException("bytes da midia exibida indisponiveis: " + midia.get("id"));
    }
    if ("FOTO".equals(midia.get("tipo")) && !IMAGE_TYPES.contains(original.contentType())) {
      throw new IllegalStateException("tipo de foto exibida divergente: " + midia.get("id"));
    }
    String hash = sha256(original.content());
    Object esperado = midia.get("sha256");
    if ("ORIGINAL".equals(variante) && esperado != null && !hash.equals(esperado)) {
      throw new IllegalStateException("hash da fonte de midia divergente: " + midia.get("id"));
    }
    String chavePrivada = storageProperties.getPrivateMediaPrefix()
        + "arquivo-publicidade/" + versaoId + "/" + midia.get("id")
        + "/" + variante.toLowerCase(java.util.Locale.ROOT);
    ObjectWriteResult resultado = storage.putIfAbsent(
        StorageArea.PRIVATE_MEDIA, chavePrivada, original.content(), original.contentType());
    if (resultado == ObjectWriteResult.CREATED) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCompletion(int status) {
          if (status == STATUS_ROLLED_BACK) {
            try {
              storage.delete(StorageArea.PRIVATE_MEDIA, chavePrivada);
            } catch (RuntimeException exception) {
              LOG.error("Falha ao limpar copia privada apos rollback: versao={}, midia={}, variante={}",
                  versaoId, midia.get("id"), variante, exception);
            }
          }
        }
      });
    }
    StoredObject confirmada = storage.get(StorageArea.PRIVATE_MEDIA, chavePrivada);
    if (confirmada == null || confirmada.content() == null
        || !hash.equals(sha256(confirmada.content()))
        || !Objects.equals(original.contentType(), confirmada.contentType())) {
      throw new IllegalStateException("copia privada da midia nao confirmou integridade: " + midia.get("id"));
    }
    return new MidiaCopiada(UUID.randomUUID(), (UUID) midia.get("id"),
        (UUID) midia.get("arquivo_midia_id"), variante, chavePrivada,
        hash, original.contentType(), original.content().length,
        ((Number) midia.get("ordem")).intValue());
  }

  private void encerrar(UUID janelaId, OffsetDateTime instante, String motivo) {
    jdbc.update("""
        UPDATE arquivo_publicidade_versao
        SET vigente_ate = :instante
        WHERE veiculacao_id = :janelaId AND (vigente_ate IS NULL OR vigente_ate > :instante)
        """, Map.of("instante", instante, "janelaId", janelaId));
    jdbc.update("""
        UPDATE arquivo_publicidade_veiculacao
        SET fim_em = :instante,
            retencao_ate = CASE WHEN cobertura IN ('ABRANGIDA', 'PREVENTIVA')
              THEN :instante + INTERVAL '1 year' ELSE NULL END,
            encerramento_motivo = :motivo,
            atualizado_em = :instante
        WHERE id = :janelaId AND (fim_em IS NULL OR fim_em > :instante)
        """, Map.of("instante", instante, "motivo", motivo, "janelaId", janelaId));
  }

  private List<Map<String, Object>> ativacoesVigentes(UUID anuncioId, OffsetDateTime instante) {
    return jdbc.queryForList("""
        SELECT ab.id, ab.grupo_ativacao_id, ab.origem, ab.inicio_em,
               CASE WHEN gb.id IS NULL THEN ab.fim_em
                 ELSE LEAST(ab.fim_em, gb.validade_fim_em) END AS fim_em,
               bp.codigo, mc.id AS movimento_id
        FROM ativacao_beneficio ab
        JOIN beneficio_premium bp ON bp.id = ab.beneficio_id AND bp.escopo = 'ANUNCIO'
        LEFT JOIN grupo_ativacao_beneficio gb ON gb.id = ab.grupo_ativacao_id
        LEFT JOIN LATERAL (
          SELECT id FROM movimento_credito
          WHERE referencia_tipo = 'ATIVACAO_BENEFICIO' AND referencia_id = ab.id
            AND tipo = 'SAIDA' AND direcao = 'DEBITO'
          ORDER BY criado_em, id LIMIT 1
        ) mc ON true
        WHERE ab.anuncio_id = :anuncioId AND ab.status = 'ATIVA'
          AND ab.revogada_em IS NULL AND ab.inicio_em <= :instante AND ab.fim_em > :instante
          AND (gb.id IS NULL OR (gb.status = 'ATIVO' AND gb.validade_inicio_em <= :instante
            AND gb.validade_fim_em > :instante))
        ORDER BY ab.id
        """, Map.of("anuncioId", anuncioId, "instante", instante));
  }

  private List<Map<String, Object>> midiasExibiveis(UUID anuncioId) {
    // Match the public gallery's position policy before filtering invalid files:
    // an invalid photo still consumes one of the available public positions.
    List<Map<String, Object>> candidatas = jdbc.queryForList("""
        SELECT am.id, am.arquivo_midia_id, am.tipo, am.finalidade, am.ordem,
               am.visibilidade_midia, ar.storage_provider, ar.bucket, ar.chave_objeto,
                ar.mime_type, ar.sha256, ar.status_arquivo,
                ar.preview_restrito_chave, ar.preview_restrito_status,
                EXISTS (SELECT 1 FROM documento_usuario d
                  WHERE d.arquivo_midia_id = ar.id) AS documento_usuario
        FROM anuncio_midia am LEFT JOIN arquivo_midia ar ON ar.id = am.arquivo_midia_id
        WHERE am.anuncio_id = :anuncioId AND am.status = 'PUBLICAVEL'
          AND am.tipo IN ('FOTO', 'VIDEO')
          AND am.visibilidade_midia IS NOT NULL
        ORDER BY am.ordem, am.id
        """, Map.of("anuncioId", anuncioId));
    PremiumPublicoFlagsDto flags = premiumPublico.flagsPorAnuncioIds(List.of(anuncioId))
        .getOrDefault(anuncioId, PremiumPublicoFlagsDto.vazio());
    int maxFotos = flags.fotosExtrasAtivo() ? FOTOS_COM_EXTRA : FOTOS_BASE;
    Map<UUID, Map<String, Object>> porId = new HashMap<>();
    List<MidiaVinculoLeitura> vinculos = candidatas.stream().map(midia -> {
      UUID id = (UUID) midia.get("id");
      porId.put(id, midia);
      return new MidiaVinculoLeitura(id, anuncioId, (UUID) midia.get("arquivo_midia_id"),
          TipoAnuncioMidia.valueOf((String) midia.get("tipo")),
          FinalidadeAnuncioMidia.valueOf((String) midia.get("finalidade")),
          (Integer) midia.get("ordem"), StatusAnuncioMidia.PUBLICAVEL,
          VisibilidadeMidia.valueOf((String) midia.get("visibilidade_midia")), null);
    }).toList();
    return SelecaoMidiasPublicas.selecionar(vinculos, maxFotos, flags.videoAtivo()).stream()
        .map(vinculo -> porId.get(vinculo.id()))
        .filter(midia -> "VALIDADO".equals(midia.get("status_arquivo")))
        .filter(midia -> !Boolean.TRUE.equals(midia.get("documento_usuario")))
        .toList();
  }

  private List<String> valores(String sql, UUID anuncioId) {
    return jdbc.queryForList(sql, Map.of("anuncioId", anuncioId), String.class);
  }

  private Map<String, Object> unico(String sql, Map<String, ?> parametros) {
    List<Map<String, Object>> linhas = jdbc.queryForList(sql, parametros);
    return linhas.isEmpty() ? null : linhas.get(0);
  }

  String json(Object valor) {
    try {
      return mapper.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .writeValueAsString(valor);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("falha ao serializar arquivo publicitario", exception);
    }
  }

  private String sha256(byte[] bytes) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
      return java.util.HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 indisponivel", exception);
    }
  }

  private OffsetDateTime instanteJdbc(Object valor) {
    if (valor instanceof OffsetDateTime instante) {
      return instante;
    }
    if (valor instanceof java.sql.Timestamp instante) {
      return instante.toInstant().atOffset(java.time.ZoneOffset.UTC);
    }
    throw new IllegalStateException("instante de ativacao invalido");
  }

  private record Classificacao(String nome, String relacaoMaterial, String cobertura) {
    static Classificacao de(Map<String, Object> ativacao) {
      String origem = String.valueOf(ativacao.get("origem"));
      if ("CREDITO".equals(origem) || "COMPRA".equals(origem)) {
        // A debit against a fungible balance does not identify the payment that funded it.
        return new Classificacao("ORIGEM_INDETERMINADA", "DESCONHECIDA", "PREVENTIVA");
      }
      if ("CORTESIA".equals(origem) || "CAMPANHA".equals(origem)) {
        return new Classificacao("PROMOCIONAL", "DESCONHECIDA", "PREVENTIVA");
      }
      if ("ADMIN".equals(origem)) {
        return new Classificacao("ADMINISTRATIVA", "DESCONHECIDA", "PREVENTIVA");
      }
      return new Classificacao("ORIGEM_INDETERMINADA", "DESCONHECIDA", "PREVENTIVA");
    }
  }

  private record MidiaCopiada(UUID id, UUID anuncioMidiaId, UUID arquivoMidiaId,
      String variante, String chave, String sha256, String mimeType, long tamanhoBytes, int ordem) {
  }
}
