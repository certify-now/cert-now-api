package com.uk.certifynow.certify_now.integration.cucumber;

import com.uk.certifynow.certify_now.CertifyNowApplication;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

@CucumberContextConfiguration
@SpringBootTest(
    classes = CertifyNowApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CucumberSpringConfiguration {}
