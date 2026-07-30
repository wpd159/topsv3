package br.com.topsdojob.v3.persistence.entity.anuncio;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.LocalAtendimentoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ServicoAnuncio;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "anuncio")
public class AnuncioEntity {
  protected AnuncioEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "usuario_id")
  private UUID usuarioId;

  @Column(name = "slug")
  private String slug;

  @Column(name = "titulo")
  private String titulo;

  @Column(name = "descricao")
  private String descricao;

  @Enumerated(EnumType.STRING)
  @Column(name = "status")
  private StatusAnuncio status;

  @Enumerated(EnumType.STRING)
  @Column(name = "status_moderacao")
  private StatusModeracaoAnuncio statusModeracao;

  @Column(name = "categoria")
  private String categoria;

  @Column(name = "atendimento_exclusivamente_virtual", nullable = false)
  private boolean atendimentoExclusivamenteVirtual;

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(name = "anuncio_local_atendimento", joinColumns = @JoinColumn(name = "anuncio_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "local_atendimento", nullable = false)
  @BatchSize(size = 100)
  private Set<LocalAtendimentoAnuncio> locaisAtendimento = new LinkedHashSet<>();

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(name = "anuncio_servicos", joinColumns = @JoinColumn(name = "anuncio_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "servico", nullable = false)
  @BatchSize(size = 100)
  private Set<ServicoAnuncio> servicos = new LinkedHashSet<>();

  @Column(name = "preco", precision = 12, scale = 2)
  private BigDecimal preco;

  @Column(name = "whatsapp_normalizado")
  private String whatsappNormalizado;

  @Column(name = "publicado_em")
  private OffsetDateTime publicadoEm;

  @Column(name = "ultima_publicacao_em")
  private OffsetDateTime ultimaPublicacaoEm;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  @Column(name = "atualizado_em")
  private OffsetDateTime atualizadoEm;

  @Column(name = "removido_em")
  private OffsetDateTime removidoEm;

  @Column(name = "origem_importacao_id")
  private UUID origemImportacaoId;

  @Version
  @Column(name = "versao")
  private Integer versao;

  public UUID getId() {
    return id;
  }

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public String getSlug() {
    return slug;
  }

  public String getTitulo() {
    return titulo;
  }

  public String getDescricao() {
    return descricao;
  }

  public StatusAnuncio getStatus() {
    return status;
  }

  public StatusModeracaoAnuncio getStatusModeracao() {
    return statusModeracao;
  }

  public String getCategoria() {
    return categoria;
  }

  public boolean isAtendimentoExclusivamenteVirtual() {
    return atendimentoExclusivamenteVirtual;
  }

  public boolean ofereceSexoVirtual() {
    return servicos.contains(ServicoAnuncio.VIDEOCHAMADA);
  }

  public Set<LocalAtendimentoAnuncio> getLocaisAtendimento() {
    return Set.copyOf(locaisAtendimento);
  }

  public Set<ServicoAnuncio> getServicos() {
    return Set.copyOf(servicos);
  }

  public BigDecimal getPreco() {
    return preco;
  }

  public String getWhatsappNormalizado() {
    return whatsappNormalizado;
  }

  public OffsetDateTime getPublicadoEm() {
    return publicadoEm;
  }

  public OffsetDateTime getUltimaPublicacaoEm() {
    return ultimaPublicacaoEm;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

  public OffsetDateTime getAtualizadoEm() {
    return atualizadoEm;
  }

  public OffsetDateTime getRemovidoEm() {
    return removidoEm;
  }

  public UUID getOrigemImportacaoId() {
    return origemImportacaoId;
  }

  public Integer getVersao() {
    return versao;
  }

  public boolean podePausarPeloProprietario() {
    return removidoEm == null
        && status == StatusAnuncio.PUBLICADO
        && statusModeracao == StatusModeracaoAnuncio.APROVADO;
  }

  public boolean podeReativarPeloProprietario() {
    return removidoEm == null
        && status == StatusAnuncio.PAUSADO
        && statusModeracao == StatusModeracaoAnuncio.APROVADO;
  }

  public boolean podeRemoverPeloProprietario() {
    return removidoEm == null
        && status != StatusAnuncio.BLOQUEADO
        && status != StatusAnuncio.REMOVIDO;
  }

  public void pausarPeloProprietario(OffsetDateTime atualizadoEm) {
    if (!podePausarPeloProprietario()) {
      throw new IllegalStateException("transicao para PAUSADO nao permitida");
    }
    this.status = StatusAnuncio.PAUSADO;
    this.atualizadoEm = atualizadoEm;
  }

  public void reativarPeloProprietario(OffsetDateTime atualizadoEm) {
    if (!podeReativarPeloProprietario()) {
      throw new IllegalStateException("transicao para PUBLICADO nao permitida");
    }
    this.status = StatusAnuncio.PUBLICADO;
    this.atualizadoEm = atualizadoEm;
  }

  public void reativarAdministrativamente(OffsetDateTime atualizadoEm) {
    reativarPeloProprietario(atualizadoEm);
  }

  public void bloquearJuridicamente(OffsetDateTime atualizadoEm) {
    if (removidoEm != null
        || status == StatusAnuncio.REMOVIDO
        || status == StatusAnuncio.BLOQUEADO) {
      throw new IllegalStateException("transicao para BLOQUEADO nao permitida");
    }
    this.status = StatusAnuncio.BLOQUEADO;
    this.atualizadoEm = atualizadoEm;
  }

  public boolean pausarPorBloqueioUsuario(OffsetDateTime atualizadoEm) {
    if (removidoEm != null
        || statusModeracao != StatusModeracaoAnuncio.APROVADO
        || (status != StatusAnuncio.PUBLICADO && status != StatusAnuncio.APROVADO)) {
      return false;
    }
    this.status = StatusAnuncio.PAUSADO;
    this.atualizadoEm = atualizadoEm;
    return true;
  }

  public void desbloquearJuridicamente(OffsetDateTime atualizadoEm) {
    if (removidoEm != null || status != StatusAnuncio.BLOQUEADO) {
      throw new IllegalStateException("transicao de BLOQUEADO para PAUSADO nao permitida");
    }
    this.status = StatusAnuncio.PAUSADO;
    this.atualizadoEm = atualizadoEm;
  }

  public void removerPeloProprietario(OffsetDateTime removidoEm) {
    removerLogicamente(removidoEm);
  }

  public void removerLogicamente(OffsetDateTime removidoEm) {
    if (!podeRemoverPeloProprietario()) {
      throw new IllegalStateException("transicao para REMOVIDO nao permitida");
    }
    this.status = StatusAnuncio.REMOVIDO;
    this.removidoEm = removidoEm;
    this.atualizadoEm = removidoEm;
  }

  public boolean removerPorExclusaoDaConta(OffsetDateTime removidoEm) {
    if (this.removidoEm != null || status == StatusAnuncio.REMOVIDO) {
      return false;
    }
    this.slug = "conta-excluida-" + id;
    this.titulo = "Anuncio de conta excluida";
    this.descricao = "Conteudo indisponivel por exclusao da conta.";
    this.whatsappNormalizado = null;
    this.status = StatusAnuncio.REMOVIDO;
    this.removidoEm = removidoEm;
    this.atualizadoEm = removidoEm;
    return true;
  }

  public void aplicarModeracao(
      StatusAnuncio status,
      StatusModeracaoAnuncio statusModeracao,
      OffsetDateTime atualizadoEm) {
    validarParAprovacaoPublicacao(status, statusModeracao);
    this.status = status;
    this.statusModeracao = statusModeracao;
    this.atualizadoEm = atualizadoEm;
  }

  public void aprovarEPublicarAdministrativamente(OffsetDateTime publicadoEm) {
    boolean aprovacaoPendente = status == StatusAnuncio.PENDENTE_REVISAO
        && statusModeracao == StatusModeracaoAnuncio.PENDENTE;
    boolean aprovacaoLegadaSemPublicacao = status == StatusAnuncio.APROVADO
        && statusModeracao == StatusModeracaoAnuncio.APROVADO;
    if (removidoEm != null || (!aprovacaoPendente && !aprovacaoLegadaSemPublicacao)) {
      throw new IllegalStateException("transicao administrativa para PUBLICADO/APROVADO nao permitida");
    }
    this.status = StatusAnuncio.PUBLICADO;
    this.statusModeracao = StatusModeracaoAnuncio.APROVADO;
    if (this.publicadoEm == null) {
      this.publicadoEm = publicadoEm;
    }
    this.ultimaPublicacaoEm = publicadoEm;
    this.atualizadoEm = publicadoEm;
  }

  public void remeterParaRevisao(OffsetDateTime atualizadoEm) {
    this.status = StatusAnuncio.PENDENTE_REVISAO;
    this.statusModeracao = StatusModeracaoAnuncio.PENDENTE;
    this.atualizadoEm = atualizadoEm;
  }

  public void sincronizarAtendimentoEstruturado(
      Set<LocalAtendimentoAnuncio> locaisAtendimento,
      Set<ServicoAnuncio> servicos) {
    sincronizarAtendimentoEstruturado(
        locaisAtendimento, servicos, atendimentoExclusivamenteVirtual);
  }

  public void sincronizarAtendimentoEstruturado(
      Set<LocalAtendimentoAnuncio> locaisAtendimento,
      Set<ServicoAnuncio> servicos,
      boolean atendimentoExclusivamenteVirtual) {
    Set<ServicoAnuncio> servicosSeguros = servicos == null ? Set.of() : Set.copyOf(servicos);
    if (atendimentoExclusivamenteVirtual
        && !servicosSeguros.contains(ServicoAnuncio.VIDEOCHAMADA)) {
      throw new IllegalArgumentException(
          "atendimento exclusivamente virtual exige o servico VIDEOCHAMADA");
    }
    this.locaisAtendimento.clear();
    this.locaisAtendimento.addAll(locaisAtendimento == null ? Set.of() : locaisAtendimento);
    this.servicos.clear();
    this.servicos.addAll(servicosSeguros);
    this.atendimentoExclusivamenteVirtual = atendimentoExclusivamenteVirtual;
  }

  public void atualizarPeloProprietario(
      String titulo,
      String descricao,
      String categoria,
      BigDecimal preco,
      String whatsappNormalizado,
      Set<LocalAtendimentoAnuncio> locaisAtendimento,
      Set<ServicoAnuncio> servicos,
      boolean atendimentoExclusivamenteVirtual,
      OffsetDateTime atualizadoEm) {
    this.titulo = titulo;
    this.descricao = descricao;
    this.categoria = categoria;
    this.preco = preco;
    this.whatsappNormalizado = whatsappNormalizado;
    sincronizarAtendimentoEstruturado(
        locaisAtendimento, servicos, atendimentoExclusivamenteVirtual);
    remeterParaRevisao(atualizadoEm);
  }

  public void atualizarPeloProprietario(
      String titulo,
      String descricao,
      String categoria,
      BigDecimal preco,
      String whatsappNormalizado,
      Set<LocalAtendimentoAnuncio> locaisAtendimento,
      Set<ServicoAnuncio> servicos,
      OffsetDateTime atualizadoEm) {
    atualizarPeloProprietario(
        titulo,
        descricao,
        categoria,
        preco,
        whatsappNormalizado,
        locaisAtendimento,
        servicos,
        atendimentoExclusivamenteVirtual,
        atualizadoEm);
  }

  public void atualizarAdministrativamente(
      String titulo,
      String descricao,
      String categoria,
      BigDecimal preco,
      String whatsappNormalizado,
      Set<LocalAtendimentoAnuncio> locaisAtendimento,
      Set<ServicoAnuncio> servicos,
      boolean atendimentoExclusivamenteVirtual,
      OffsetDateTime atualizadoEm) {
    this.titulo = titulo;
    this.descricao = descricao;
    this.categoria = categoria;
    this.preco = preco;
    this.whatsappNormalizado = whatsappNormalizado;
    sincronizarAtendimentoEstruturado(
        locaisAtendimento, servicos, atendimentoExclusivamenteVirtual);
    this.atualizadoEm = atualizadoEm;
  }

  public void atualizarAdministrativamente(
      String titulo,
      String descricao,
      String categoria,
      BigDecimal preco,
      String whatsappNormalizado,
      Set<LocalAtendimentoAnuncio> locaisAtendimento,
      Set<ServicoAnuncio> servicos,
      OffsetDateTime atualizadoEm) {
    atualizarAdministrativamente(
        titulo,
        descricao,
        categoria,
        preco,
        whatsappNormalizado,
        locaisAtendimento,
        servicos,
        atendimentoExclusivamenteVirtual,
        atualizadoEm);
  }

  public static AnuncioEntity criarSolicitacaoLocal(
      UUID id,
      UUID usuarioId,
      String slug,
      String titulo,
      String descricao,
      String categoria,
      BigDecimal preco,
      String whatsappNormalizado,
      OffsetDateTime criadoEm) {
    return criarSolicitacaoLocal(
        id,
        usuarioId,
        slug,
        titulo,
        descricao,
        categoria,
        preco,
        whatsappNormalizado,
        Set.of(),
        false,
        criadoEm);
  }

  public static AnuncioEntity criarSolicitacaoLocal(
      UUID id,
      UUID usuarioId,
      String slug,
      String titulo,
      String descricao,
      String categoria,
      BigDecimal preco,
      String whatsappNormalizado,
      Set<ServicoAnuncio> servicos,
      boolean atendimentoExclusivamenteVirtual,
      OffsetDateTime criadoEm) {
    AnuncioEntity entity = new AnuncioEntity();
    entity.id = id;
    entity.usuarioId = usuarioId;
    entity.slug = slug;
    entity.titulo = titulo;
    entity.descricao = descricao;
    entity.status = StatusAnuncio.PENDENTE_REVISAO;
    entity.statusModeracao = StatusModeracaoAnuncio.PENDENTE;
    entity.categoria = categoria;
    entity.sincronizarAtendimentoEstruturado(
        Set.of(), servicos, atendimentoExclusivamenteVirtual);
    entity.preco = preco;
    entity.whatsappNormalizado = whatsappNormalizado;
    entity.publicadoEm = null;
    entity.ultimaPublicacaoEm = null;
    entity.criadoEm = criadoEm;
    entity.atualizadoEm = criadoEm;
    entity.removidoEm = null;
    entity.origemImportacaoId = null;
    entity.versao = 0;
    return entity;
  }

  public static AnuncioEntity criarFixtureHomologacao(
      UUID id,
      UUID usuarioId,
      String slug,
      String titulo,
      String descricao,
      StatusAnuncio status,
      StatusModeracaoAnuncio statusModeracao,
      OffsetDateTime criadoEm) {
    return criarFixtureHomologacao(
        id,
        usuarioId,
        slug,
        titulo,
        descricao,
        "ACOMPANHANTE_FEMININA",
        status,
        statusModeracao,
        criadoEm);
  }

  public static AnuncioEntity criarFixtureHomologacao(
      UUID id,
      UUID usuarioId,
      String slug,
      String titulo,
      String descricao,
      String categoria,
      StatusAnuncio status,
      StatusModeracaoAnuncio statusModeracao,
      OffsetDateTime criadoEm) {
    validarParAprovacaoPublicacao(status, statusModeracao);
    AnuncioEntity entity = new AnuncioEntity();
    entity.id = id;
    entity.usuarioId = usuarioId;
    entity.slug = slug;
    entity.titulo = titulo;
    entity.descricao = descricao;
    entity.status = status;
    entity.statusModeracao = statusModeracao;
    entity.categoria = categoria;
    entity.atendimentoExclusivamenteVirtual = false;
    entity.preco = null;
    entity.whatsappNormalizado = null;
    entity.publicadoEm = status == StatusAnuncio.PUBLICADO ? criadoEm : null;
    entity.ultimaPublicacaoEm = entity.publicadoEm;
    entity.criadoEm = criadoEm;
    entity.atualizadoEm = criadoEm;
    entity.removidoEm = null;
    entity.origemImportacaoId = null;
    entity.versao = 0;
    return entity;
  }

  public void sincronizarFixtureHomologacao(
      String titulo,
      String descricao,
      String categoria,
      StatusAnuncio status,
      StatusModeracaoAnuncio statusModeracao,
      OffsetDateTime atualizadoEm) {
    validarParAprovacaoPublicacao(status, statusModeracao);
    this.titulo = titulo;
    this.descricao = descricao;
    this.categoria = categoria;
    this.status = status;
    this.statusModeracao = statusModeracao;
    this.removidoEm = null;
    if (status == StatusAnuncio.PUBLICADO) {
      if (this.publicadoEm == null) {
        this.publicadoEm = atualizadoEm;
      }
      this.ultimaPublicacaoEm = atualizadoEm;
    }
    this.atualizadoEm = atualizadoEm;
  }

  private static void validarParAprovacaoPublicacao(
      StatusAnuncio status,
      StatusModeracaoAnuncio statusModeracao) {
    if (status == StatusAnuncio.APROVADO
        && statusModeracao == StatusModeracaoAnuncio.APROVADO) {
      throw new IllegalArgumentException(
          "APROVADO/APROVADO e um estado legado invalido; use aprovarEPublicarAdministrativamente");
    }
  }

}
