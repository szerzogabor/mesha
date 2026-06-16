package com.mesha.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "blocks.encryption")
public class BlocksEncryptionProperties {

    private String secret = "";

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
}
