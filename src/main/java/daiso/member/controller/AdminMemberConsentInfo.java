package daiso.member.controller;

import java.sql.Timestamp;

public class AdminMemberConsentInfo {
    private final boolean marketingSms;
    private final boolean marketingEmail;
    private final boolean marketingPersonalInfo;
    private final boolean locationTerms;
    private final String retentionPeriod;
    private final Timestamp updatedAt;

    public AdminMemberConsentInfo(
            boolean marketingSms,
            boolean marketingEmail,
            boolean marketingPersonalInfo,
            boolean locationTerms,
            String retentionPeriod,
            Timestamp updatedAt) {
        this.marketingSms = marketingSms;
        this.marketingEmail = marketingEmail;
        this.marketingPersonalInfo = marketingPersonalInfo;
        this.locationTerms = locationTerms;
        this.retentionPeriod = retentionPeriod;
        this.updatedAt = updatedAt;
    }

    public boolean isMarketingSms() { return marketingSms; }
    public boolean isMarketingEmail() { return marketingEmail; }
    public boolean isMarketingPersonalInfo() { return marketingPersonalInfo; }
    public boolean isLocationTerms() { return locationTerms; }
    public String getRetentionPeriod() { return retentionPeriod; }
    public Timestamp getUpdatedAt() { return updatedAt; }
}