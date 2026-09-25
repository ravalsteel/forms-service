package com.ravalgroups.forms.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "forms.reporting")
public record FormsReportingProperties(int defaultMinAggregationThreshold) {

    public FormsReportingProperties {
        if (defaultMinAggregationThreshold < 0) {
            defaultMinAggregationThreshold = 5;
        }
    }
}
