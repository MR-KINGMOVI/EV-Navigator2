# EV-Navigator Iran

**Version 13.3.6 | Build [ACTUAL_BUILD_NUMBER]**

An advanced Android Electric Vehicle (EV) navigation platform designed to combine intelligent route planning, physics-based energy prediction, battery intelligence, charging infrastructure information, and vehicle-aware trip optimization.

EV-Navigator aims to provide reliable and transparent electric vehicle trip planning, with particular attention to Iranian roads, vehicle specifications, and charging infrastructure.

## Core Features

- **Intelligent Route Planning:** Route calculation, route evaluation, and navigation-oriented decision support.
- **Physics-Based Energy Prediction:** Vehicle-aware energy consumption estimation using physical and environmental parameters.
- **Battery Intelligence:** Battery State of Charge (SOC) estimation and predicted battery level at destination.
- **Charging-Aware Trip Planning:** Charging-stop planning based on vehicle characteristics, battery state, and charging requirements.
- **Charging Infrastructure:** Charging station information, connector compatibility, charging power, and charging-related data.
- **Vehicle Database:** Structured electric vehicle specifications and configurable vehicle profiles.
- **Weather Integration:** Environmental conditions incorporated into energy consumption models.
- **Navigation Architecture:** Modular routing, road profiles, map matching, and navigation components.
- **Automotive Integration:** Android Auto and automotive experience development support.

## Engineering Principles

Reliability and data transparency are central engineering priorities.

- Prefer real, traceable data over fabricated or unsupported values.
- Represent missing information explicitly using UNKNOWN states.
- Track uncertainty instead of presenting assumptions as verified facts.
- Avoid silent fallback values that can compromise energy predictions.
- Maintain modular, testable, and maintainable architecture.
- Separate verified, estimated, assumed, stale, and unavailable information.
- Prioritize correctness, performance, and a consistent user experience.

## Technology Stack

- Kotlin
- Android SDK
- Jetpack Compose
- Gradle
- Room Database
- Hilt Dependency Injection
- Kotlin Coroutines
- AndroidX Libraries

## Project Architecture

The project follows a modular architecture:

- `app` — Android application and entry points.
- `core` — Shared core infrastructure.
- `core/energy` — Energy modeling and prediction.
- `core/roadprofile` — Road profile and elevation-related processing.
- `core/routing` — Routing engines and route models.
- `data` — Data sources, repositories, and catalogs.
- `domain` — Business logic, models, and use cases.
- `feature` — Application features and user interface.
- `design-system` — Shared UI components and visual system.
- `navigation` — Navigation and route execution components.

## Current Project Status

- Application version: **13.3.6**
- Version code: **[ACTUAL_BUILD_NUMBER]**
- Development status: Active development.
- Repository: Public GitHub repository.
- Release: See the GitHub Releases page for published versions.

Build, automated tests, static analysis, and device-level validation are separate verification stages. Their status must be determined from the actual results for the corresponding source revision.

## Development Goals

1. Improve route calculation reliability.
2. Increase energy prediction accuracy and transparency.
3. Improve battery and charging calculations.
4. Expand vehicle and charging infrastructure coverage.
5. Improve navigation usability and performance.
6. Strengthen automated testing and data integrity.
7. Improve maintainability, security, and release quality.

## Repository

https://github.com/MR-KINGMOVI/EV-Navigator2

## Releases

https://github.com/MR-KINGMOVI/EV-Navigator2/releases

## Contributions

Contributions, bug reports, technical reviews, and architecture improvements are welcome.

Please review the existing architecture and verify relevant tests before submitting pull requests.

## License

See the repository's `LICENSE` file for licensing terms.
