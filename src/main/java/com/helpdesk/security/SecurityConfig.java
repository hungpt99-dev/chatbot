package com.helpdesk.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.AuthenticatedPrincipalOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.web.SecurityFilterChain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Single source of truth for the HTTP security boundary (BRD §7).
 *
 * <p>When {@code helpdesk.security.enabled=false} (default) the app runs fully
 * open: every request is permitted and CSRF is disabled so the existing
 * unauthenticated suite, the offline interpreter, and local dev keep working
 * unchanged.</p>
 *
 * <p>When enabled, the API is protected by OIDC SSO ({@code oauth2Login}):
 * {@code /api/health} stays public, the static UI is public, and every other
 * {@code /api/**} call requires an authenticated employee. Fine-grained RBAC
 * (IT_ADMIN vs EMPLOYEE) is enforced separately via {@code @PreAuthorize} on the
 * controllers (see {@link MethodSecurityConfig}).</p>
 *
 * <p>The multi-tenant {@code hotelId} flow is untouched: security only decides
 * <em>who</em> may call an endpoint; tenant scoping stays the controllers' and
 * services' responsibility.</p>
 */
@Configuration
@EnableConfigurationProperties(HelpdeskSecurityProperties.class)
public class SecurityConfig {

    private final HelpdeskSecurityProperties properties;

    public SecurityConfig(HelpdeskSecurityProperties properties) {
        this.properties = properties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        if (!properties.isEnabled()) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .csrf(AbstractHttpConfigurer::disable);
            return http.build();
        }

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers("/", "/ui/**", "/index.html", "/error").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated())
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(userInfo -> userInfo
                                .userAuthoritiesMapper(userAuthoritiesMapper())))
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }

    /**
     * Maps the OIDC roles claim onto Spring {@code ROLE_*} authorities so that
     * {@code @PreAuthorize("hasRole('IT_ADMIN')")} works. Only invoked during a
     * real SSO login (never in unit tests, which inject a mock principal).
     */
    private GrantedAuthoritiesMapper userAuthoritiesMapper() {
        String rolesClaim = properties.getOidc().getRolesClaim();
        return authorities -> {
            Set<GrantedAuthority> mapped = new HashSet<>(authorities);
            for (GrantedAuthority authority : authorities) {
                if (authority instanceof OidcUserAuthority oidcAuthority) {
                    OidcUserInfo userInfo = oidcAuthority.getUserInfo();
                    List<String> roles = userInfo.getClaimAsStringList(rolesClaim);
                    if (roles != null) {
                        for (String role : roles) {
                            mapped.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
                        }
                    }
                }
            }
            return mapped;
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "helpdesk.security", name = "enabled", havingValue = "true")
    public ClientRegistrationRepository clientRegistrationRepository() {
        HelpdeskSecurityProperties.Oidc oidc = properties.getOidc();
        ClientRegistration registration = ClientRegistration.withRegistrationId(oidc.getRegistrationId())
                .clientId(oidc.getClientId())
                .clientSecret(oidc.getClientSecret())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(oidc.getRedirectUri())
                .scope(properties.scopes())
                .authorizationUri(oidc.getAuthorizationUri())
                .tokenUri(oidc.getTokenUri())
                .jwkSetUri(oidc.getJwkSetUri())
                .userInfoUri(oidc.getUserInfoUri())
                .userNameAttributeName(oidc.getUserNameAttribute())
                .clientName("Helpdesk SSO")
                .build();
        return new InMemoryClientRegistrationRepository(registration);
    }

    @Bean
    @ConditionalOnProperty(prefix = "helpdesk.security", name = "enabled", havingValue = "true")
    public OAuth2AuthorizedClientRepository authorizedClientRepository(
            ClientRegistrationRepository clientRegistrationRepository) {
        OAuth2AuthorizedClientService service =
                new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);
        return new AuthenticatedPrincipalOAuth2AuthorizedClientRepository(service);
    }
}
