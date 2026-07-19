package com.ecommerce.gateway.filter;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.function.ServerRequest;

import java.util.function.Function;

/**
 * Strips any client-supplied X-User-Id (spoofing defense — applies to every
 * route, not just JWT-protected ones), then — only when the request carries
 * a validated JWT — re-sets it from the token's sub claim. Runs after Spring
 * Security's filter chain, so unauthenticated calls to protected routes never
 * reach a proxy call with this filter still pending.
 */
public final class IdentityHeaderFilterFunctions {

    public static final String USER_ID_HEADER = "X-User-Id";

    private IdentityHeaderFilterFunctions() {
    }

    public static Function<ServerRequest, ServerRequest> propagateUserIdFromJwt() {
        return request -> {
            ServerRequest.Builder builder = ServerRequest.from(request)
                    .headers(headers -> headers.remove(USER_ID_HEADER));
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                builder.headers(headers -> headers.add(USER_ID_HEADER, jwtAuth.getToken().getSubject()));
            }
            return builder.build();
        };
    }
}
