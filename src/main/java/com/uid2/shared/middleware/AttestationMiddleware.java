package com.uid2.shared.middleware;

import com.uid2.shared.Const;
import com.uid2.shared.attest.IAttestationTokenService;
import com.uid2.shared.attest.JwtService;
import com.uid2.shared.attest.JwtValidationResponse;
import com.uid2.shared.attest.RoleBasedJwtClaimValidator;
import com.uid2.shared.auth.*;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

public class AttestationMiddleware {

    private final IAttestationTokenService tokenService;
    private final JwtService jwtService;
    private final String jwtAudience;
    private final String jwtIssuer;
    private final boolean enforceJwt;

    public AttestationMiddleware(IAttestationTokenService tokenService, JwtService jwtService, String jwtAudience, String jwtIssuer, boolean enforceJwt) {
        this.tokenService = tokenService;
        this.jwtService = jwtService;
        this.jwtAudience = jwtAudience;
        this.jwtIssuer = jwtIssuer;
        this.enforceJwt = enforceJwt;
    }

    //region RequestHandler

    public Handler<RoutingContext> handle(Handler<RoutingContext> handler, com.uid2.shared.auth.Role... roles) {
        final RoleBasedJwtClaimValidator validator = new RoleBasedJwtClaimValidator(Collections.unmodifiableSet(new HashSet<>(Arrays.asList(roles))));
        final AttestationHandler wrapper = new AttestationHandler(handler, this.tokenService, this.jwtService, this.jwtAudience, this.jwtIssuer, this.enforceJwt, validator);
        return wrapper::handle;
    }

    private static class AttestationHandler {
        private final static Logger LOGGER = LoggerFactory.getLogger(AttestationHandler.class);
        private final Handler<RoutingContext> next;
        private final IAttestationTokenService attestor;
        private final JwtService jwtService;
        private final String jwtAudience;
        private final String jwtIssuer;
        private final boolean enforceJwt;
        private final RoleBasedJwtClaimValidator roleBasedJwtClaimValidator;

        AttestationHandler(Handler<RoutingContext> next, IAttestationTokenService attestor, JwtService jwtService, String jwtAudience, String jwtIssuer, boolean enforceJwt, RoleBasedJwtClaimValidator roleBasedJwtClaimValidator) {
            this.next = next;
            this.attestor = attestor;
            this.jwtService = jwtService;
            this.jwtAudience = jwtAudience;
            this.jwtIssuer = jwtIssuer;
            this.enforceJwt = enforceJwt;
            this.roleBasedJwtClaimValidator = roleBasedJwtClaimValidator;
        }

        public void handle(RoutingContext rc) {
            boolean success = false;

            final IAuthorizable profile = AuthMiddleware.getAuthClient(rc);
            if (profile instanceof OperatorKey) {
                OperatorKey operatorKey = (OperatorKey) profile;
                final String protocol = operatorKey.getProtocol();
                final String userToken = AuthMiddleware.getAuthToken(rc);
                final String jwt = getAttestationJWT(rc);

                final String encryptedToken = getAttestationToken(rc);
                if ("trusted".equals(protocol)) {
                    // (pre-)trusted operator requires no-attestation
                    success = true;
                } else if (encryptedToken != null && userToken != null) {
                    success = attestor.validateToken(userToken, encryptedToken);
                }

                if (success) {
                    if (jwt != null && !jwt.isBlank()) {
                        try {
                            JwtValidationResponse response = jwtService.validateJwt(jwt, this.jwtAudience, this.jwtIssuer);
                            success = response.getIsValid();
                            if (success && !this.roleBasedJwtClaimValidator.hasRequiredRoles(response)) {
                                success = false;
                                LOGGER.info("JWT missing required role. Required roles: {}, JWT Presented Roles: {}, SiteId: {}, Name: {}, Contact: {}", this.roleBasedJwtClaimValidator.getRequiredRoles(), response.getRoles(), operatorKey.getSiteId(), operatorKey.getName(), operatorKey.getContact());
                            }
                        } catch (JwtService.ValidationException e) {
                            LOGGER.info("Error validating JWT. Attestation validation failed. SiteId: {}, Name: {}, Contact: {}. Error: {}", operatorKey.getSiteId(), operatorKey.getName(), operatorKey.getContact(), e);
                            success = false;
                        }
                    } else {
                        if (this.enforceJwt) {
                            LOGGER.info("JWT is required, but was not received. Attestation validation failed. SiteId: {}, Name: {}, Contact: {}", operatorKey.getSiteId(), operatorKey.getName(), operatorKey.getContact());
                            success = false;
                        }
                    }
                }
            }

            if (success) {
                next.handle(rc);
            } else {
                onFailedAttestation(rc);
            }
        }

        private void onFailedAttestation(RoutingContext rc) {
            rc.fail(401);
        }

        private String getAttestationToken(RoutingContext rc) {
            return rc.request().getHeader(Const.Attestation.AttestationTokenHeader);
        }

        private String getAttestationJWT(RoutingContext rc) {
            return rc.request().getHeader(Const.Attestation.AttestationJWTHeader);
        }
    }

    //endregion RequestHandler
}
