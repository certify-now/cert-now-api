Feature: Current user profile
  As an authenticated user (customer or engineer)
  I want to read and update my own profile
  So that my profile data is accurate for downstream features

  Background:
    Given the registration service is available
    And the email service is stubbed to accept all sends

  Scenario: Customer reads current user profile
    Given an active customer is authenticated
    When the active customer requests their current user profile
    Then the API response status is 200
    And the current user role is "CUSTOMER"
    And the current user email matches the authenticated customer
    And the response includes request_id

  Scenario: Customer updates current user profile
    Given an active customer is authenticated
    When the active customer updates their current user profile with valid data
    Then the API response status is 200
    And the current user changes are persisted for the customer

  Scenario: Engineer reads current user profile
    Given an active engineer is authenticated
    When the engineer requests their current user profile
    Then the API response status is 200
    And the current user role is "ENGINEER"
    And the current user email matches the authenticated engineer
    And the response includes request_id

  Scenario: Engineer updates current user profile
    Given an active engineer is authenticated
    When the engineer updates their current user profile with valid data
    Then the API response status is 200
    And the current user changes are persisted for the engineer
