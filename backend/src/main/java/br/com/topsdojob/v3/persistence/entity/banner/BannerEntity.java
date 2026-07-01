package br.com.topsdojob.v3.persistence.entity.banner;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusBanner;
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
@Table(name = "banner")
public class BannerEntity {
  protected BannerEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "banner_espaco_id")
  private UUID bannerEspacoId;

  @Column(name = "titulo")
  private String titulo;

  @Column(name = "subtitulo")
  private String subtitulo;

  @Column(name = "texto_botao")
  private String textoBotao;

  @Column(name = "url_destino")
  private String urlDestino;

  @Column(name = "alt_text_desktop")
  private String altTextDesktop;

  @Column(name = "alt_text_mobile")
  private String altTextMobile;

  @Column(name = "arquivo_desktop_id")
  private UUID arquivoDesktopId;

  @Column(name = "arquivo_mobile_id")
  private UUID arquivoMobileId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status")
  private StatusBanner status;

  @Column(name = "inicio_em")
  private OffsetDateTime inicioEm;

  @Column(name = "fim_em")
  private OffsetDateTime fimEm;

  @Column(name = "ordem")
  private Integer ordem;

  @Version
  @Column(name = "versao")
  private Integer versao;

  @Column(name = "criado_por")
  private UUID criadoPor;

  @Column(name = "atualizado_por")
  private UUID atualizadoPor;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  @Column(name = "atualizado_em")
  private OffsetDateTime atualizadoEm;

  public UUID getId() {
    return id;
  }

  public UUID getBannerEspacoId() {
    return bannerEspacoId;
  }

  public String getTitulo() {
    return titulo;
  }

  public String getSubtitulo() {
    return subtitulo;
  }

  public String getTextoBotao() {
    return textoBotao;
  }

  public String getUrlDestino() {
    return urlDestino;
  }

  public String getAltTextDesktop() {
    return altTextDesktop;
  }

  public String getAltTextMobile() {
    return altTextMobile;
  }

  public UUID getArquivoDesktopId() {
    return arquivoDesktopId;
  }

  public UUID getArquivoMobileId() {
    return arquivoMobileId;
  }

  public StatusBanner getStatus() {
    return status;
  }

  public OffsetDateTime getInicioEm() {
    return inicioEm;
  }

  public OffsetDateTime getFimEm() {
    return fimEm;
  }

  public Integer getOrdem() {
    return ordem;
  }

  public Integer getVersao() {
    return versao;
  }

  public UUID getCriadoPor() {
    return criadoPor;
  }

  public UUID getAtualizadoPor() {
    return atualizadoPor;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

  public OffsetDateTime getAtualizadoEm() {
    return atualizadoEm;
  }

}
