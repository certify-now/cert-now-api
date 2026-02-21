package com.uk.certifynow.certify_now.integration.cucumber.support;

import com.uk.certifynow.certify_now.shared.security.RequiresVerifiedEmail;
import org.springframework.stereotype.Service;

@Service
public class TestVerifiedActionService {

  @RequiresVerifiedEmail
  public String execute() {
    return "verified-action-ok";
  }
}
