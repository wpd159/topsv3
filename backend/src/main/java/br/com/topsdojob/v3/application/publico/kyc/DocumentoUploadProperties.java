package br.com.topsdojob.v3.application.publico.kyc;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.document-upload")
public class DocumentoUploadProperties {

  private long maxBytes = 12L * 1024L * 1024L;

  public long getMaxBytes() {
    return maxBytes;
  }

  public void setMaxBytes(long maxBytes) {
    this.maxBytes = maxBytes;
  }
}
