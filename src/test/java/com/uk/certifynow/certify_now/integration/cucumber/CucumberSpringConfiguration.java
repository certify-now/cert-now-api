package com.uk.certifynow.certify_now.integration.cucumber;

import com.uk.certifynow.certify_now.CertifyNowApplication;
import com.uk.certifynow.certify_now.integration.TestContainersConfig;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@CucumberContextConfiguration
@SpringBootTest(
    classes = CertifyNowApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
public class CucumberSpringConfiguration extends TestContainersConfig {}
