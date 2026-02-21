package com.uk.certifynow.certify_now.integration.cucumber.support;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class TestProtectedController {

  private final TestVerifiedActionService testVerifiedActionService;

  public TestProtectedController(final TestVerifiedActionService testVerifiedActionService) {
    this.testVerifiedActionService = testVerifiedActionService;
  }

  @GetMapping("/protected")
  public Map<String, String> protectedEndpoint() {
    return Map.of("message", "ok");
  }

  @PostMapping("/requires-verified-email")
  public Map<String, String> requiresVerifiedEmail() {
    return Map.of("result", testVerifiedActionService.execute());
  }
}
