# Agent Guidelines for MCP Toolbox Java SDK

Welcome to the MCP Toolbox SDK for Java! This document provides guidelines for AI agents and developers working within this repository.

## 1. Build, Lint, and Test Commands

This project uses Maven for dependency management and building.

### Building
```bash
mvn clean compile
```

### Formatting & Linting
The project strictly follows the Google Java Style Guide. Lines must not exceed the character limit.
```bash
# Format code (Required before pushing)
mvn com.spotify.fmt:fmt-maven-plugin:format
```

### Testing
Tests are critical. E2E tests interact with a real locally-downloaded MCP Toolbox binary.

*   **Testing Expectations:** If you add a new feature or fix a bug, you must add corresponding unit tests or update the E2E tests (`McpToolboxClientTest.java`) to cover your changes.

**Running All Tests:**
```bash
mvn clean test
```

**Running a Single Test:**
```bash
mvn test -Dtest=ClassName#methodName
```

**Environment-Specific Test Requirements:**
When running tests in specific internal/Google environments, you may encounter `401 Unauthorized` for Maven artifacts, `invalid_grant` for GCP auth, or JaCoCo compatibility errors. Use this robust test command:
```bash
# Refresh ADC (fixes 'invalid_grant' when downloading toolbox binary from GCS)
gcloud auth application-default login

# Run tests with required environment variables and bypasses
AIRLOCK_OAUTH_TOKEN=$(gcloud auth print-access-token) \
GOOGLE_CLOUD_PROJECT=$(gcloud config get-value project) \
TOOLBOX_VERSION=0.26.0 \
TOOLBOX_MANIFEST_VERSION=34 \
mvn clean test -Djacoco.skip=true -Dtest=McpToolboxClientE2ETest
```
*(Note: JaCoCo is skipped here due to `IllegalClassFormatException` with Java 25 class file versions in some setups).*

## 2. Architecture & Design Principles

*   **Async-First:** Use `CompletableFuture` for all network/I/O bound operations. Avoid blocking (`.join()`) within library code.
*   **Java 11+ Standard:** Utilize modern Java standards like the built-in `java.net.http.HttpClient`.
*   **Framework Agnostic:** The SDK is designed to work in standard Java, Spring Boot, and Quarkus. Do not introduce Spring-specific or Quarkus-specific dependencies in the core logic.
*   **Dependency Constraints:** Do not add new dependencies to the `pom.xml` unless explicitly requested. Rely on standard Java 11+ APIs and the existing Jackson dependency.

## 3. Code Style & Naming Conventions

*   **Strict Google Java Format:** Avoid long lines. Always run the format plugin.
*   **Documentation & Javadoc Requirements:** All new public classes, interfaces, and methods must include standard Javadoc explaining their purpose, parameters, and return values.
*   **No Interface Pollution:** Do not add internal state getters (e.g., `getBaseUrl()`) to public interfaces just to perform internal checks. Keep the public API clean.
*   **Encapsulation:** Keep validation and transport-layer logic contained within the specific implementation (e.g., `HttpMcpToolboxClient`). 
*   **Constants over Magic Strings:** Do not duplicate strings (e.g., warning messages). Declare them as `private static final String` constants.
*   **Case-Insensitive Validation:** When validating schemes or headers, always use case-insensitive checks (e.g., `url.toLowerCase(Locale.ROOT).startsWith("http://")`).
*   **Trust Class Invariants:** If a Builder explicitly validates that a parameter is not null, do not add redundant defensive null checks deeper in the code.
*   **Error Handling:** Use `CompletableFuture.failedFuture(e)` to propagate asynchronous errors. Ensure credentials are scrubbed from exception messages.

## 4. Operational Learnings
*   **Credential Exposure:** Do not log or transmit authentication headers if the `baseUrl` uses unencrypted HTTP. Always warn the user about credential exposure over HTTP.

## 5. SDK Parity (Python & Go)
The Java SDK aims for feature parity with the Python and Go SDKs. Whenever you are asked to modify or create a new feature or fix a bug, take a look at the Python and Go counterparts to ensure general consistency and shared implementation patterns across the ecosystem.

*   **Python SDK:** https://github.com/Google/mcp-toolbox-sdk-python
*   **Go SDK:** https://github.com/Google/mcp-toolbox-sdk-go

## 6. Branching & PR Guidelines
*   **Syncing:** Whenever there is a new request, always fetch the origin and sync the main branch on your fork before creating a new branch and working on it.
*   **Branch Naming:** All new branches must use the prefix `stenalpjolly_`. For example: `stenalpjolly_feature_name` or `stenalpjolly_bugfix_name`.
*   **Commit Message Conventions:** All commit messages and PR titles must strictly follow Conventional Commits format (e.g., `feat: ...`, `fix: ...`, `chore: ...`, `docs: ...`).
*   **PR Creation Command:** When creating a PR, use the `gh` CLI with a detailed body: `gh pr create --title "feat: description" --body "Detailed context..."`.
*   Each PR should be very small in size. If multiple features are requested, they should be split into different PRs. Split changes logically whenever possible.
