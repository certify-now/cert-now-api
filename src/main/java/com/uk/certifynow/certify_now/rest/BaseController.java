package com.uk.certifynow.certify_now.rest;

import com.uk.certifynow.certify_now.config.RequestIdFilter;
import com.uk.certifynow.certify_now.service.auth.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/** Shared controller helpers extracted to eliminate copy-paste across all controllers. */
public abstract class BaseController {

  protected UUID extractUserId(final Authentication authentication) {
    return UUID.fromString((String) authentication.getPrincipal());
  }

  protected UserRole extractRole(final Authentication authentication) {
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .filter(a -> a.startsWith("ROLE_"))
        .map(a -> UserRole.valueOf(a.replace("ROLE_", "")))
        .findFirst()
        .orElseThrow(() -> new AccessDeniedException("No role found in authentication token"));
  }

  protected String requestId(final HttpServletRequest request) {
    return (String) request.getAttribute(RequestIdFilter.REQUEST_ID);
  }
}
