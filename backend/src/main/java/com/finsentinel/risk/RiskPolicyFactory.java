package com.finsentinel.risk;

import org.springframework.stereotype.Component;

@Component
public class RiskPolicyFactory {

    private final RiskPolicyStrategy activeStrategy = new StandardRiskPolicyV1();

    public RiskPolicyStrategy getActivePolicy() {
        return activeStrategy;
    }

    public RiskPolicyStrategy getPolicy(String version) {
        if (version == null || version.isBlank() || StandardRiskPolicyV1.POLICY_VERSION.equals(version)) {
            return activeStrategy;
        }
        throw new IllegalArgumentException("Unsupported risk policy version: " + version);
    }
}
