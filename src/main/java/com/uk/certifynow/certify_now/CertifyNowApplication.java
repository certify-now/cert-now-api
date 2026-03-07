package com.uk.certifynow.certify_now;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CertifyNowApplication {

  public static void main(final String[] args) {
    SpringApplication.run(CertifyNowApplication.class, args);
  }
}
