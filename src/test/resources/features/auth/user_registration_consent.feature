Feature: User registration consent recording
  As the system
  I want to record user consent to Terms of Service and Privacy Policy at registration
  So that we have an auditable record of when and from where consent was given

  Background:
    Given the registration service is available

  Rule: Consent records must be created for every successful registration

    @regression
    Scenario: Consent records are created for terms of service and privacy policy
      Given the client IP address is "192.168.1.100"
      When a CUSTOMER registers successfully
      Then 2 UserConsent records exist in the database for that user
      And one consent record has type "TERMS_OF_SERVICE"
      And one consent record has type "PRIVACY_POLICY"
      And both records store the IP address "192.168.1.100"

  Rule: The consenting IP address must be extracted correctly from the request

    @regression
    Scenario: IP is extracted from X-Forwarded-For header when present
      Given the request includes header X-Forwarded-For "10.0.0.1, 172.16.0.1"
      When a CUSTOMER registers successfully
      Then the consent records store IP "10.0.0.1"

