package com.shoploc.shoplocapigateway.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SCRUM-63 : traduit le JWT Keycloak en X-User-Id/X-User-Role, les en-têtes que
 * catalog-service et order-service lisent déjà (voir CurrentUser côté order-service).
 *
 * Toute valeur de ces deux en-têtes envoyée par l'appelant est systématiquement effacée
 * avant routage : sans ça, n'importe qui pourrait se faire passer pour un autre utilisateur
 * en les fixant lui-même, puisque catalog/order leur font une confiance aveugle.
 *
 * Un jeton absent ou invalide n'est pas bloqué ici : la requête part sans ces en-têtes, et
 * c'est au service appelé de refuser si son endpoint les exige (comme aujourd'hui).
 */
@Component
public class JwtIdentityFilter implements GlobalFilter, Ordered {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    /** Rôles ShopLoc (MCD), mêmes valeurs que shoploc-identity-service/shoploc-frontend. */
    private static final List<String> ROLES = List.of("CLIENT", "COMMERCANT", "COLLECTIVITE", "ADMIN");

    private final ReactiveJwtDecoder jwtDecoder;

    public JwtIdentityFilter(ReactiveJwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String token = bearerToken(exchange.getRequest().getHeaders());
        Mono<UserIdentity> identity = token == null
                ? Mono.empty()
                : jwtDecoder.decode(token)
                        .mapNotNull(jwt -> {
                            String role = roleOf(jwt);
                            return role == null ? null : new UserIdentity(jwt.getSubject(), role);
                        })
                        .onErrorResume(e -> Mono.empty());

        return identity.map(Optional::of).defaultIfEmpty(Optional.empty())
                .flatMap(maybeUser -> {
                    ServerHttpRequest mutated = exchange.getRequest().mutate()
                            .headers(headers -> {
                                headers.remove(USER_ID_HEADER);
                                headers.remove(USER_ROLE_HEADER);
                                maybeUser.ifPresent(user -> {
                                    headers.set(USER_ID_HEADER, user.id());
                                    headers.set(USER_ROLE_HEADER, user.role());
                                });
                            })
                            .build();
                    return chain.filter(exchange.mutate().request(mutated).build());
                });
    }

    /** Même logique que ProfileController.roleOf côté identity-service : un seul rôle ShopLoc par compte. */
    private static String roleOf(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return null;
        }
        return ROLES.stream().filter(roles::contains).findFirst().orElse(null);
    }

    private static String bearerToken(HttpHeaders headers) {
        String value = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (value == null || !value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        return value.substring(7).trim();
    }

    /** Avant la Route Predicate Handler Mapping : doit s'exécuter avant le routage lui-même. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private record UserIdentity(String id, String role) {}
}
