package br.com.topsdojob.v3.importacao.integracao;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.migracao.integral")
public class MigracaoIntegralProperties {

  private boolean enabled;
  private OperacaoMigracaoIntegral operacao;
  private Path pacote;
  private Path diretorioManifestos;
  private Path manifestoFaseCinco;
  private String origemId;
  private String snapshotSha256;
  private OffsetDateTime capturadoEm;
  private String execucaoId;
  private ModoMigracaoIntegral modo;
  private int tamanhoLote;
  private boolean retomar;
  private boolean confirmarApply;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public OperacaoMigracaoIntegral getOperacao() {
    return operacao;
  }

  public void setOperacao(OperacaoMigracaoIntegral operacao) {
    this.operacao = operacao;
  }

  public Path getPacote() {
    return pacote;
  }

  public void setPacote(Path pacote) {
    this.pacote = pacote;
  }

  public Path getDiretorioManifestos() {
    return diretorioManifestos;
  }

  public void setDiretorioManifestos(Path diretorioManifestos) {
    this.diretorioManifestos = diretorioManifestos;
  }

  public Path getManifestoFaseCinco() {
    return manifestoFaseCinco;
  }

  public void setManifestoFaseCinco(Path manifestoFaseCinco) {
    this.manifestoFaseCinco = manifestoFaseCinco;
  }

  public String getOrigemId() {
    return origemId;
  }

  public void setOrigemId(String origemId) {
    this.origemId = origemId;
  }

  public String getSnapshotSha256() {
    return snapshotSha256;
  }

  public void setSnapshotSha256(String snapshotSha256) {
    this.snapshotSha256 = snapshotSha256;
  }

  public OffsetDateTime getCapturadoEm() {
    return capturadoEm;
  }

  public void setCapturadoEm(OffsetDateTime capturadoEm) {
    this.capturadoEm = capturadoEm;
  }

  public String getExecucaoId() {
    return execucaoId;
  }

  public void setExecucaoId(String execucaoId) {
    this.execucaoId = execucaoId;
  }

  public ModoMigracaoIntegral getModo() {
    return modo;
  }

  public void setModo(ModoMigracaoIntegral modo) {
    this.modo = modo;
  }

  public int getTamanhoLote() {
    return tamanhoLote;
  }

  public void setTamanhoLote(int tamanhoLote) {
    this.tamanhoLote = tamanhoLote;
  }

  public boolean isRetomar() {
    return retomar;
  }

  public void setRetomar(boolean retomar) {
    this.retomar = retomar;
  }

  public boolean isConfirmarApply() {
    return confirmarApply;
  }

  public void setConfirmarApply(boolean confirmarApply) {
    this.confirmarApply = confirmarApply;
  }

  public void validar() {
    if (!enabled) {
      throw new IllegalStateException("migracao integral nao foi habilitada explicitamente");
    }
    if (operacao == null) {
      throw new IllegalStateException("operacao da migracao integral nao foi informada");
    }
    if (origemId == null || origemId.isBlank()) {
      throw new IllegalStateException("origem esperada nao foi informada");
    }
    if (operacao == OperacaoMigracaoIntegral.PRODUZIR_MANIFESTO) {
      if (manifestoFaseCinco == null
          || snapshotSha256 == null
          || capturadoEm == null
          || execucaoId == null
          || execucaoId.isBlank()) {
        throw new IllegalStateException("parametros do produtor estao incompletos");
      }
      return;
    }
    if (operacao == OperacaoMigracaoIntegral.VALIDAR_MANIFESTO) {
      if (manifestoFaseCinco == null || snapshotSha256 == null) {
        throw new IllegalStateException("parametros do validador de manifesto estao incompletos");
      }
      return;
    }
    if (pacote == null) {
      throw new IllegalStateException("pacote da migracao integral nao foi informado");
    }
    if (diretorioManifestos == null) {
      throw new IllegalStateException("diretorio de manifests nao foi informado");
    }
    if (operacao == OperacaoMigracaoIntegral.PRODUZIR_PACOTE) {
      if (manifestoFaseCinco == null
          || snapshotSha256 == null
          || capturadoEm == null
          || execucaoId == null
          || execucaoId.isBlank()) {
        throw new IllegalStateException("parametros do produtor de pacote estao incompletos");
      }
      return;
    }
    if (operacao == OperacaoMigracaoIntegral.EXECUTAR) {
      if (modo == null) {
        throw new IllegalStateException("modo da migracao integral nao foi informado");
      }
      if (tamanhoLote < 1 || tamanhoLote > 10_000) {
        throw new IllegalStateException("tamanho de lote fora do intervalo permitido");
      }
      if (modo == ModoMigracaoIntegral.APPLY && !confirmarApply) {
        throw new IllegalStateException("apply exige confirmacao tecnica explicita");
      }
    }
  }
}
