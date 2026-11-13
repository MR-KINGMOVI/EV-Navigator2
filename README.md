# EV-Navigator

**Version 13.3.1 | Build 134**

An advanced open-source Android Electric Vehicle (EV) navigation platform designed to combine intelligent route planning, physics-based energy prediction, charging infrastructure intelligence, and vehicle-aware trip optimization.

## Core Features

- **Intelligent Route Planning:** Route calculation, route evaluation, and navigation-oriented decision support.
- **Physics-Based Energy Prediction:** Vehicle-aware energy consumption estimation using driving and environmental parameters.
- **Battery Intelligence:** State of Charge (SOC) estimation and predicted battery level at destination.
- **Charging Infrastructure:** Charging station information, connector compatibility, charging power, and charging-aware trip planning.
- **Vehicle Database:** Structured EV specifications and configurable vehicle profiles.
- **Weather Integration:** Environmental conditions incorporated into energy consumption models.
- **Navigation Architecture:** Modular routing, road profile, map matching, and navigation components.
- **Automotive Integration:** Development support for Android Auto and automotive experiences.

## Engineering Principles

- Prefer real, traceable data over fabricated or unsupported values.
- Represent missing information explicitly using UNKNOWN states.
- Track uncertainty instead of presenting assumptions as verified facts.
- Avoid silent fallback values that can compromise energy predictions.
- Maintain modular, testable, and maintainable architecture.
- Prioritize reliability, performance, and a polished user experience.

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

The project is organized into modular components:

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

- Application version: **13.3.1**
- Version code: **134**
- Development status: Active development.
- Static integrity checks: 7 checks passed in the local V13.2 static audit.
- Android build and automated test status: Must be verified against the current source revision.

Passing static checks does not imply that the Android application has successfully compiled or passed every test.

## Development Goals

1. Improve route calculation reliability.
2. Increase energy prediction accuracy and transparency.
3. Expand vehicle and charging infrastructure coverage.
4. Improve navigation usability and performance.
5. Strengthen automated testing and data integrity.
6. Prepare the application for reliable real-world usage.

## Repository

https://github.com/MR-KINGMOVI/EV-Navigator2

## Contributions

Contributions, bug reports, technical reviews, and architecture improvements are welcome.

Please verify changes with the relevant tests before submitting pull requests.

## License

See the repository's `LICENSE` file for licensing terms.
