# Managing Deployments with application-profile.yml Files

  The key to managing configuration for deployments across different environments in our system is through structured and named application.yml files. These files are critical to defining how different features and behaviors are handled, based on the deployment environment.

## Core Configuration File 
### application-calculation-params.yml

This is [the main configuration file used in production](../src/main/resources/application-calculation-params.yml). It defines all the key parameters for handling calculations across various services, including HDC, ERSED, and release-point multipliers. Below is a typical structure for this file:


This is the main configuration file used in production. It defines all the key parameters for handling calculations across various services, including HDC, ERSED, and release-point multipliers. This file is the default configuration deployed to production and serves as the basis for all other environment configurations.

Key sections of the configuration include:
- **HDCED Configuration**: Governs the minimum custodial period, deduction days, and specific commencement dates.
- **ERSED**: Defines the maximum period in days.
- **Release Point Multipliers**: Handles how different sentence types and release points are calculated, with different multipliers for tracks like SDS early release and BOTUS.
- **SDS Early Release Tranches**: Specifies tranche dates for early release calculations, which change the applicable release multipliers.

This file should contain accurate clarified information. Where information is speculative or subject to change, environment or calculation-configuration files should be used. 

## Environment-Specific YML Files

Each environment (e.g., `dev`, `preprod`, `prod`) has its own dedicated configuration file that builds on the defaults set in `application-calculation-params.yml`. For example, `values-prod.yaml` and `values-dev.yaml` contain environment-specific settings while referring to the same core parameters from the main file.

## Calculation Configuration-Specific Files
Each environment may override some of these parameters with specific values. For instance, in a development environment, you might use application-dev.yml to include different early release multipliers or additional debug-related settings.

#### Examples include:

- **application-sds-50.yml** for scenarios specific to SDS50 legislative changes.
- **application-sds-45.yml** for managing early release variations (e.g., 45% early release scenario).
- **application-sds-43.yml** for the 43% multiplier on early release.
  
## Helm Configuration
  Helm charts are used to manage deployment configurations. For example:


```env:
SPRING_PROFILES_ACTIVE: "calculation-params"
```
By setting SPRING_PROFILES_ACTIVE in Helm charts, we point the system to use the relevant application.yml configuration for that environment.
This allows you to deploy specific calculation configurations to different environments to test long-lasting features and their configurations.