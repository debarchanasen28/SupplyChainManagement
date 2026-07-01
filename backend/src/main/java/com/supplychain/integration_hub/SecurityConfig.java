package com.supplychain.integration_hub;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers(
                    "/api/cpi/inbound/stock-offer",
                    "/api/cpi/inbound/**"
                ).permitAll()   // CPI inbound: no user JWT; gated by X-CPI-Secret in the controller
                .requestMatchers("/api/cpi/**").permitAll()   // CPI bridge: test + inbound (CPI carries no user JWT)
                .requestMatchers("/api/dashboard/vendor/**").hasAnyAuthority("VENDOR", "ADMIN", "MANAGER")
                .requestMatchers("/api/dashboard/procurement/**").hasAnyAuthority("PROCUREMENT", "ADMIN", "MANAGER")
                .requestMatchers("/api/dashboard/admin/**").hasAnyAuthority("ADMIN", "MANAGER")
                .requestMatchers("/api/admin/**").hasAnyAuthority("ADMIN", "MANAGER")
                .requestMatchers("/api/users/**").hasAnyAuthority("ADMIN", "MANAGER")
                .requestMatchers("/api/vendor/shipments/**").hasAuthority("VENDOR")
                .requestMatchers("/api/vendor/orders/**").hasAuthority("VENDOR")
                .requestMatchers("/api/procurement/shipments/**").hasAuthority("PROCUREMENT")
                .requestMatchers(
                    "/api/procurement/system2-vendor-inventory",
                    "/api/procurement/vendor-inventory/**"
                ).hasAnyAuthority("PROCUREMENT", "ADMIN", "MANAGER")
                .requestMatchers("/api/orders/**").hasAnyAuthority("ADMIN", "MANAGER", "VENDOR", "PROCUREMENT")
                .requestMatchers("/api/shipments/**").hasAnyAuthority("ADMIN", "MANAGER", "VENDOR", "PROCUREMENT")
                .requestMatchers("/api/inventory/**").hasAnyAuthority("ADMIN", "MANAGER", "VENDOR", "PROCUREMENT")
                .requestMatchers("/api/suppliers/**").hasAnyAuthority("ADMIN", "MANAGER", "PROCUREMENT")
                .requestMatchers("/api/alerts/**").hasAnyAuthority("ADMIN", "MANAGER", "VENDOR", "PROCUREMENT")
                .requestMatchers("/api/logs/**").hasAnyAuthority("ADMIN", "MANAGER")
                .requestMatchers("/api/simulator/**").hasAnyAuthority("ADMIN", "MANAGER")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.setCorsConfigurations(Map.of("/**", config));
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
