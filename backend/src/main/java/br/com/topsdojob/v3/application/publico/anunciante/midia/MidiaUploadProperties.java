package br.com.topsdojob.v3.application.publico.anunciante.midia;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.media-upload")
public class MidiaUploadProperties {

    private long maxImageBytes = 20L * 1024L * 1024L;
    private long maxVideoBytes = 100L * 1024L * 1024L;
    private long maxImagePixels = 40_000_000L;
    private int maxImageDimension = 20_000;
    private int maxProcessedSide = 2560;
    private float jpegQuality = 0.92f;
    private float webpQuality = 0.90f;

    public long getMaxImageBytes() {
        return maxImageBytes;
    }

    public void setMaxImageBytes(long maxImageBytes) {
        this.maxImageBytes = maxImageBytes;
    }

    public long getMaxVideoBytes() {
        return maxVideoBytes;
    }

    public void setMaxVideoBytes(long maxVideoBytes) {
        this.maxVideoBytes = maxVideoBytes;
    }

    public long getMaxImagePixels() {
        return maxImagePixels;
    }

    public void setMaxImagePixels(long maxImagePixels) {
        this.maxImagePixels = maxImagePixels;
    }

    public int getMaxImageDimension() {
        return maxImageDimension;
    }

    public void setMaxImageDimension(int maxImageDimension) {
        this.maxImageDimension = maxImageDimension;
    }

    public int getMaxProcessedSide() {
        return maxProcessedSide;
    }

    public void setMaxProcessedSide(int maxProcessedSide) {
        this.maxProcessedSide = maxProcessedSide;
    }

    public float getJpegQuality() {
        return jpegQuality;
    }

    public void setJpegQuality(float jpegQuality) {
        this.jpegQuality = jpegQuality;
    }

    public float getWebpQuality() {
        return webpQuality;
    }

    public void setWebpQuality(float webpQuality) {
        this.webpQuality = webpQuality;
    }
}
