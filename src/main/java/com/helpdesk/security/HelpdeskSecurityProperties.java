package com.helpdesk.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration for the optional SSO / RBAC layer (BRD §7).
 *
 * <p>Everything is gated behind {@link #enabled}, which defaults to {@code false}
 * so the app stays open for local dev, offline runs, and the unauthenticated test
 * suite. When enabled, the OIDC settings drive Spring Security's OAuth2 client
 * (interactive {@code oauth2Login}). Secrets are injected via environment
 * variables and never committed.
 */
@ConfigurationProperties(prefix = "helpdesk.security")
public class HelpdeskSecurityProperties {

    private boolean enabled = false;

    private final Oidc oidc = new Oidc();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Oidc getOidc() {
        return oidc;
    }

    public List<String> scopes() {
        return List.of(oidc.scope.split(",")).stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public static class Oidc {

        private String registrationId = "helpdesk";
        private String clientId = "change-me";
        private String clientSecret = "change-me";
        private String scope = "openid,profile,email";
        private String authorizationUri = "https://idp.example.com/oauth2/authorize";
        private String tokenUri = "https://idp.example.com/oauth2/token";
        private String jwkSetUri = "https://idp.example.com/oauth2/jwks";
        private String userInfoUri = "https://idp.example.com/oauth2/userinfo";
        private String userNameAttribute = "sub";
        private String redirectUri = "{baseUrl}/login/oauth2/code/{registrationId}";
        private String rolesClaim = "groups";

        public String getRegistrationId() {
            return registrationId;
        }

        public void setRegistrationId(String registrationId) {
            this.registrationId = registrationId;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getScope() {
            return scope;
        }

        public void setScope(String scope) {
            this.scope = scope;
        }

        public String getAuthorizationUri() {
            return authorizationUri;
        }

        public void setAuthorizationUri(String authorizationUri) {
            this.authorizationUri = authorizationUri;
        }

        public String getTokenUri() {
            return tokenUri;
        }

        public void setTokenUri(String tokenUri) {
            this.tokenUri = tokenUri;
        }

        public String getJwkSetUri() {
            return jwkSetUri;
        }

        public void setJwkSetUri(String jwkSetUri) {
            this.jwkSetUri = jwkSetUri;
        }

        public String getUserInfoUri() {
            return userInfoUri;
        }

        public void setUserInfoUri(String userInfoUri) {
            this.userInfoUri = userInfoUri;
        }

        public String getUserNameAttribute() {
            return userNameAttribute;
        }

        public void setUserNameAttribute(String userNameAttribute) {
            this.userNameAttribute = userNameAttribute;
        }

        public String getRedirectUri() {
            return redirectUri;
        }

        public void setRedirectUri(String redirectUri) {
            this.redirectUri = redirectUri;
        }

        public String getRolesClaim() {
            return rolesClaim;
        }

        public void setRolesClaim(String rolesClaim) {
            this.rolesClaim = rolesClaim;
        }
    }
}
