package br.com.topsdojob.v3.persistence.entity.seo;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.QualidadeSeo;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusEsperadoSeo;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoSeoUrl;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "seo_url")
public class SeoUrlEntity {
  protected SeoUrlEntity() {
  }

  public static SeoUrlEntity criarConteudoInstitucional(
      UUID id,
      String caminhoPublico,
      OffsetDateTime agora) {
    SeoUrlEntity entity = new SeoUrlEntity();
    entity.id = id;
    entity.caminhoPublico = caminhoPublico;
    entity.canonicalPath = caminhoPublico;
    entity.tipo = TipoSeoUrl.INSTITUCIONAL;
    entity.statusEsperado = StatusEsperadoSeo.OK_200;
    entity.indexavel = false;
    entity.incluirSitemap = false;
    entity.qualidadeStatus = QualidadeSeo.PENDENTE;
    entity.criadoEm = agora;
    entity.atualizadoEm = agora;
    entity.versao = 0;
    return entity;
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "caminho_publico")
  private String caminhoPublico;

  @Column(name = "canonical_path")
  private String canonicalPath;

  @Enumerated(EnumType.STRING)
  @Column(name = "tipo")
  private TipoSeoUrl tipo;

  @Column(name = "entidade_tipo")
  private String entidadeTipo;

  @Column(name = "entidade_id")
  private UUID entidadeId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status_esperado")
  private StatusEsperadoSeo statusEsperado;

  @Column(name = "indexavel")
  private Boolean indexavel;

  @Column(name = "incluir_sitemap")
  private Boolean incluirSitemap;

  @Enumerated(EnumType.STRING)
  @Column(name = "qualidade_status")
  private QualidadeSeo qualidadeStatus;

  @Column(name = "ultima_validacao_em")
  private OffsetDateTime ultimaValidacaoEm;

  @Column(name = "motivo_noindex")
  private String motivoNoindex;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  @Column(name = "atualizado_em")
  private OffsetDateTime atualizadoEm;

  @Version
  @Column(name = "versao")
  private Integer versao;

  public UUID getId() {
    return id;
  }

  public String getCaminhoPublico() {
    return caminhoPublico;
  }

  public String getCanonicalPath() {
    return canonicalPath;
  }

  public TipoSeoUrl getTipo() {
    return tipo;
  }

  public String getEntidadeTipo() {
    return entidadeTipo;
  }

  public UUID getEntidadeId() {
    return entidadeId;
  }

  public StatusEsperadoSeo getStatusEsperado() {
    return statusEsperado;
  }

  public Boolean getIndexavel() {
    return indexavel;
  }

  public Boolean getIncluirSitemap() {
    return incluirSitemap;
  }

  public QualidadeSeo getQualidadeStatus() {
    return qualidadeStatus;
  }

  public OffsetDateTime getUltimaValidacaoEm() {
    return ultimaValidacaoEm;
  }

  public String getMotivoNoindex() {
    return motivoNoindex;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

  public OffsetDateTime getAtualizadoEm() {
    return atualizadoEm;
  }

  public Integer getVersao() {
    return versao;
  }

}
