package com.uk.certifynow.certify_now.integration;

import com.uk.certifynow.certify_now.shared.security.RequiresVerifiedEmail;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A dummy controller used only in integration tests to verify security filters and the {@link
 * RequiresVerifiedEmail} aspect without depending on actual domain controllers.
 */
@RestController
@RequestMapping("/api/v1/test-protected")
public class TestProtectedController {

  @GetMapping("/standard")
  public Map<String, String> standardProtected() {
    return Map.of("message", "Standard protected endpoint accessed");
  }

  @PostMapping("/privileged")
  @RequiresVerifiedEmail
  public Map<String, String> privilegedEndpoint() {
    return Map.of("message", "Privileged endpoint accessed");
  }
}
