package com.uk.certifynow.certify_now.integration.cucumber;

import com.uk.certifynow.certify_now.repos.CustomerProfileRepository;
import com.uk.certifynow.certify_now.repos.EmailVerificationTokenRepository;
import com.uk.certifynow.certify_now.repos.EngineerProfileRepository;
import com.uk.certifynow.certify_now.repos.RefreshTokenRepository;
import com.uk.certifynow.certify_now.repos.UserConsentRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import io.cucumber.java.Before;
import io.restassured.RestAssured;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;

public class Hooks {

  @Autowired private ScenarioContext scenarioContext;
  @Autowired private EmailVerificationTokenRepository emailVerificationTokenRepository;
  @Autowired private RefreshTokenRepository refreshTokenRepository;
  @Autowired private UserConsentRepository userConsentRepository;
  @Autowired private CustomerProfileRepository customerProfileRepository;
  @Autowired private EngineerProfileRepository engineerProfileRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private StringRedisTemplate stringRedisTemplate;

  @Value("${local.server.port}")
  private int port;

  @Before
  public void beforeScenario() {
    scenarioContext.clear();
    RestAssured.baseURI = "http://localhost";
    RestAssured.port = port;

    emailVerificationTokenRepository.deleteAllInBatch();
    refreshTokenRepository.deleteAllInBatch();
    userConsentRepository.deleteAllInBatch();
    customerProfileRepository.deleteAllInBatch();
    engineerProfileRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();

    try {
      final var connection = stringRedisTemplate.getConnectionFactory().getConnection();
      connection.serverCommands().flushAll();
      connection.close();
    } catch (Exception ignored) {
      // Redis can be temporarily unavailable; tests still run against live app behavior.
    }
  }
}
