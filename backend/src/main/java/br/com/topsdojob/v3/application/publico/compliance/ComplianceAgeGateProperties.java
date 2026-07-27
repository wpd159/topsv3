package br.com.topsdojob.v3.application.publico.compliance;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.age-gate")
public class ComplianceAgeGateProperties {

  private int globalTtlDays = 7;
  private int sessionTtlDays = 30;
  private int challengeTtlMinutes = 10;
  private int lightTokenTtlMinutes = 10_080;
  private int reinforcedTokenTtlMinutes = 10_080;
  private int strongTokenTtlMinutes = 10_080;
  private int explicitTokenTtlMinutes = 10_080;
  private int riskWindowMinutes = 60;
  private int restrictedBurstWindowMinutes = 10;
  private int tempBlockMinutes = 30;
  private int hardBlockHours = 12;
  private int maxAttemptsPerWindow = 6;
  private int maxFailuresPerWindow = 4;
  private int maxExplicitAccessPerWindow = 22;
  private int maxRestrictedBurstAccesses = 14;
  private int reviewFlagScore = 92;
  private int requireLevel2Score = 58;
  private int requireLevel3Score = 102;
  private int tempBlockScore = 148;
  private int hardBlockScore = 215;
  private boolean riskEngineEnabled = true;
  private boolean documentOnDemandEnabled = true;

  public Duration globalTtl() {
    return Duration.ofDays(globalTtlDays);
  }

  public Duration sessionTtl() {
    return Duration.ofDays(sessionTtlDays);
  }

  public Duration challengeTtl() {
    return Duration.ofMinutes(challengeTtlMinutes);
  }

  public Duration lightTokenTtl() {
    return Duration.ofMinutes(lightTokenTtlMinutes);
  }

  public Duration reinforcedTokenTtl() {
    return Duration.ofMinutes(reinforcedTokenTtlMinutes);
  }

  public Duration strongTokenTtl() {
    return Duration.ofMinutes(strongTokenTtlMinutes);
  }

  public Duration explicitTokenTtl() {
    return Duration.ofMinutes(explicitTokenTtlMinutes);
  }

  public Duration riskWindow() {
    return Duration.ofMinutes(riskWindowMinutes);
  }

  public Duration restrictedBurstWindow() {
    return Duration.ofMinutes(restrictedBurstWindowMinutes);
  }

  public Duration tempBlockDuration() {
    return Duration.ofMinutes(tempBlockMinutes);
  }

  public Duration hardBlockDuration() {
    return Duration.ofHours(hardBlockHours);
  }

  public int getGlobalTtlDays() {
    return globalTtlDays;
  }

  public void setGlobalTtlDays(int globalTtlDays) {
    this.globalTtlDays = positive(globalTtlDays, "globalTtlDays");
  }

  public int getSessionTtlDays() {
    return sessionTtlDays;
  }

  public void setSessionTtlDays(int sessionTtlDays) {
    this.sessionTtlDays = positive(sessionTtlDays, "sessionTtlDays");
  }

  public int getChallengeTtlMinutes() {
    return challengeTtlMinutes;
  }

  public void setChallengeTtlMinutes(int challengeTtlMinutes) {
    this.challengeTtlMinutes = positive(challengeTtlMinutes, "challengeTtlMinutes");
  }

  public int getLightTokenTtlMinutes() {
    return lightTokenTtlMinutes;
  }

  public void setLightTokenTtlMinutes(int lightTokenTtlMinutes) {
    this.lightTokenTtlMinutes = positive(lightTokenTtlMinutes, "lightTokenTtlMinutes");
  }

  public int getReinforcedTokenTtlMinutes() {
    return reinforcedTokenTtlMinutes;
  }

  public void setReinforcedTokenTtlMinutes(int reinforcedTokenTtlMinutes) {
    this.reinforcedTokenTtlMinutes = positive(reinforcedTokenTtlMinutes, "reinforcedTokenTtlMinutes");
  }

  public int getStrongTokenTtlMinutes() {
    return strongTokenTtlMinutes;
  }

  public void setStrongTokenTtlMinutes(int strongTokenTtlMinutes) {
    this.strongTokenTtlMinutes = positive(strongTokenTtlMinutes, "strongTokenTtlMinutes");
  }

  public int getExplicitTokenTtlMinutes() {
    return explicitTokenTtlMinutes;
  }

  public void setExplicitTokenTtlMinutes(int explicitTokenTtlMinutes) {
    this.explicitTokenTtlMinutes = positive(explicitTokenTtlMinutes, "explicitTokenTtlMinutes");
  }

  public int getRiskWindowMinutes() {
    return riskWindowMinutes;
  }

  public void setRiskWindowMinutes(int riskWindowMinutes) {
    this.riskWindowMinutes = positive(riskWindowMinutes, "riskWindowMinutes");
  }

  public int getRestrictedBurstWindowMinutes() {
    return restrictedBurstWindowMinutes;
  }

  public void setRestrictedBurstWindowMinutes(int restrictedBurstWindowMinutes) {
    this.restrictedBurstWindowMinutes = positive(
        restrictedBurstWindowMinutes,
        "restrictedBurstWindowMinutes");
  }

  public int getTempBlockMinutes() {
    return tempBlockMinutes;
  }

  public void setTempBlockMinutes(int tempBlockMinutes) {
    this.tempBlockMinutes = positive(tempBlockMinutes, "tempBlockMinutes");
  }

  public int getHardBlockHours() {
    return hardBlockHours;
  }

  public void setHardBlockHours(int hardBlockHours) {
    this.hardBlockHours = positive(hardBlockHours, "hardBlockHours");
  }

  public int getMaxAttemptsPerWindow() {
    return maxAttemptsPerWindow;
  }

  public void setMaxAttemptsPerWindow(int maxAttemptsPerWindow) {
    this.maxAttemptsPerWindow = positive(maxAttemptsPerWindow, "maxAttemptsPerWindow");
  }

  public int getMaxFailuresPerWindow() {
    return maxFailuresPerWindow;
  }

  public void setMaxFailuresPerWindow(int maxFailuresPerWindow) {
    this.maxFailuresPerWindow = positive(maxFailuresPerWindow, "maxFailuresPerWindow");
  }

  public int getMaxExplicitAccessPerWindow() {
    return maxExplicitAccessPerWindow;
  }

  public void setMaxExplicitAccessPerWindow(int maxExplicitAccessPerWindow) {
    this.maxExplicitAccessPerWindow = positive(maxExplicitAccessPerWindow, "maxExplicitAccessPerWindow");
  }

  public int getMaxRestrictedBurstAccesses() {
    return maxRestrictedBurstAccesses;
  }

  public void setMaxRestrictedBurstAccesses(int maxRestrictedBurstAccesses) {
    this.maxRestrictedBurstAccesses = positive(
        maxRestrictedBurstAccesses,
        "maxRestrictedBurstAccesses");
  }

  public int getReviewFlagScore() {
    return reviewFlagScore;
  }

  public void setReviewFlagScore(int reviewFlagScore) {
    this.reviewFlagScore = nonNegative(reviewFlagScore, "reviewFlagScore");
  }

  public int getRequireLevel2Score() {
    return requireLevel2Score;
  }

  public void setRequireLevel2Score(int requireLevel2Score) {
    this.requireLevel2Score = nonNegative(requireLevel2Score, "requireLevel2Score");
  }

  public int getRequireLevel3Score() {
    return requireLevel3Score;
  }

  public void setRequireLevel3Score(int requireLevel3Score) {
    this.requireLevel3Score = nonNegative(requireLevel3Score, "requireLevel3Score");
  }

  public int getTempBlockScore() {
    return tempBlockScore;
  }

  public void setTempBlockScore(int tempBlockScore) {
    this.tempBlockScore = nonNegative(tempBlockScore, "tempBlockScore");
  }

  public int getHardBlockScore() {
    return hardBlockScore;
  }

  public void setHardBlockScore(int hardBlockScore) {
    this.hardBlockScore = nonNegative(hardBlockScore, "hardBlockScore");
  }

  public boolean isRiskEngineEnabled() {
    return riskEngineEnabled;
  }

  public void setRiskEngineEnabled(boolean riskEngineEnabled) {
    this.riskEngineEnabled = riskEngineEnabled;
  }

  public boolean isDocumentOnDemandEnabled() {
    return documentOnDemandEnabled;
  }

  public void setDocumentOnDemandEnabled(boolean documentOnDemandEnabled) {
    this.documentOnDemandEnabled = documentOnDemandEnabled;
  }

  private int positive(int value, String field) {
    if (value < 1) {
      throw new IllegalArgumentException(field + " deve ser positivo");
    }
    return value;
  }

  private int nonNegative(int value, String field) {
    if (value < 0) {
      throw new IllegalArgumentException(field + " nao pode ser negativo");
    }
    return value;
  }
}
