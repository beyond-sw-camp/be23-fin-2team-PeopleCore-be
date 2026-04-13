package com.peoplecore.pay.repository;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "openbanking")
public class OpenBankingProperties {
    private String clientId;
    private String clientSecret;
    private String tokenUrl;
    private String verifyUrl;
}


