Feature: Property Management
  As a customer
  I want to be able to manage my properties
  So that I can eventually book certifications for them

  Scenario: Create a new property and retrieve it
    Given a logged in customer
    When they create a property with the following details:
      | addressLine1 | 10 Downing Street |
      | city         | London            |
      | postcode     | SW1A 2AA          |
      | propertyType | TERRACED          |
      | bedrooms     | 4                 |
      | hasElectric  | true              |
      | hasGasSupply | true              |
    Then the property is created successfully
    And the customer's property count is incremented
    And the customer can retrieve their property from the list
