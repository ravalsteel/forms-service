package com.ravalgroups.forms.observability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("management.server.port")
public class ManagementObservabilitySecurityConfiguration {

    @Bean
    @Order(0)
    SecurityFilterChain managementObservabilitySecurityFilterChain(
            HttpSecurity http, @Value("${management.server.port}") int managementPort) throws Exception {
        http.securityMatcher(request -> request.getLocalPort() == managementPort)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
