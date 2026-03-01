package com.uk.certifynow.certify_now.util;

import com.uk.certifynow.certify_now.service.security.InMemoryTokenDenylistService;
import org.springframework.stereotype.Component;

@Component
public class TokenDenylistTestUtils {

  private final InMemoryTokenDenylistService tokenDenylistService;

  public TokenDenylistTestUtils(final InMemoryTokenDenylistService tokenDenylistService) {
    this.tokenDenylistService = tokenDenylistService;
  }

  public boolean jtiIsDenylisted(final String jti) {
    return tokenDenylistService.isDenied(jti);
  }

  public void clearAll() {
    tokenDenylistService.clearAll();
  }
}
