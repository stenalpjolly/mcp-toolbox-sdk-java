# MCP Toolbox Spring Boot Starter Design Report

### **Objective**
To create a new Spring Boot Starter module (`mcp-toolbox-spring-boot-starter`) that automatically configures and provides an instance of `McpToolboxClient` as a Spring `@Bean`. This instantiation will be driven dynamically by configurations specified in standard Spring property files (e.g., `application.properties` or `application.yml`).

### **Purpose**
The primary purpose is to provide a seamless, idiomatic integration of the Model Context Protocol (MCP) Toolbox SDK into Spring Boot applications. By adhering to Spring Boot's "convention over configuration" philosophy, this starter eliminates the need for developers to write boilerplate `@Configuration` classes and manual builder logic every time they want to interact with the MCP Toolbox in a Spring ecosystem.

### **Benefits / How It Helps**
- **Enhanced Developer Experience (DX):** Developers only need to declare the dependency and provide the necessary properties (like `baseUrl` and `apiKey`). The rest happens automatically.
- **Standardized Consistency:** Centralizes the initialization logic. All microservices adopting the starter will construct and manage the `McpToolboxClient` in a uniform, tested manner.
- **Robust Configurability:** By exposing properties through Spring’s `@ConfigurationProperties`, it allows dynamic reconfiguration via environment variables, Kubernetes ConfigMaps, or HashiCorp Vault without changing source code.
- **IDE Support:** Implementing a configuration processor generates metadata, enabling auto-completion and documentation directly within IDEs (IntelliJ, Eclipse, VSCode) when typing `mcp.toolbox.*` in `application.yml`.

### **Demerits / Cons**
- **Maintenance Overhead:** A Spring Boot starter ties the SDK to the Spring Boot release cycle. If Spring Boot introduces breaking changes in its AutoConfiguration framework (as seen between 2.7 and 3.0), the starter must be updated.
- **"Magic" Complexity:** AutoConfiguration can be opaque. Junior developers may find it difficult to troubleshoot if beans are not resolving correctly or if conditions are failing silently.
- **Project Restructuring Risk:** If we transition the repository into a multi-module structure, it will temporarily disrupt the current CI/CD pipelines (e.g., GitHub Actions, release-please).

### **Java Community Requirements & Best Practices**
1. **Naming Conventions:** Official project starters should be named `{project}-spring-boot-starter` (e.g., `mcp-toolbox-spring-boot-starter`). Only official Spring starters use the `spring-boot-starter-{project}` format.
2. **Conditional Initialization:** The starter should use `@ConditionalOnClass(McpToolboxClient.class)` to guarantee the AutoConfiguration only fires when the core SDK is present, and `@ConditionalOnMissingBean` so developers can safely override the default bean with their own custom implementation if needed.
3. **Immutability:** Configurations should be immutable where possible, using constructor binding for `@ConfigurationProperties`.
4. **Spring Boot 3.x Compatibility:** AutoConfiguration registration should use the `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` file (the standard since Spring Boot 2.7+), abandoning the legacy `spring.factories`.

---

### **Architectural Options**

#### **Option 1: Multi-Module Project (Recommended for SDKs)**
Transform the current repository into a multi-module Maven build.
- **Structure:** 
  - `/` (Root `pom.xml` with `<packaging>pom</packaging>`)
  - `/mcp-toolbox-core` (The current SDK implementation)
  - `/mcp-toolbox-spring-boot-starter` (The new AutoConfiguration module)
- **Pros:** Shared properties (Java version, compiler plugins), unified build/test cycle, tightly coupled dependency management, standard for open-source Java libraries.
- **Cons:** Requires refactoring the root directory and updating deployment/CI scripts.

#### **Option 2: Standalone Sibling Module**
Keep the existing structure and add the starter as a standalone folder alongside `example/`.
- **Structure:**
  - `/` (Current Root SDK)
  - `/mcp-toolbox-spring-boot-starter` (Standalone `pom.xml`)
- **Pros:** No disruption to existing root code or pipelines. Fast to implement.
- **Cons:** Code duplication in `pom.xml`, disjointed release versions, higher long-term technical debt for maintainers.

---

### **Detailed Implementation Plan**

If we move forward with the implementation, the step-by-step process would be:

**1. Project Initialization**
- (Assuming Option 1): Refactor the repository into a multi-module structure. Create the `mcp-toolbox-spring-boot-starter` directory with a `pom.xml` that imports `spring-boot-autoconfigure`, `spring-boot-configuration-processor`, and `mcp-toolbox-core`.

**2. Property Binding Definition**
- Create `McpToolboxProperties.java` annotated with `@ConfigurationProperties(prefix = "mcp.toolbox")`.
- Define bindable fields matching the builder: `String baseUrl;`, `String apiKey;`. Add validation annotations (e.g., `@NotBlank` on `baseUrl`).

**3. AutoConfiguration Implementation**
- Create `McpToolboxAutoConfiguration.java` annotated with `@AutoConfiguration`.
- Apply `@EnableConfigurationProperties(McpToolboxProperties.class)`.
- Apply `@ConditionalOnClass(McpToolboxClient.class)`.
- Define a method:
  ```java
  @Bean
  @ConditionalOnMissingBean(McpToolboxClient.class)
  public McpToolboxClient mcpToolboxClient(McpToolboxProperties properties) {
      return McpToolboxClient.builder()
          .baseUrl(properties.getBaseUrl())
          .apiKey(properties.getApiKey())
          .build();
  }
  ```

**4. AutoConfiguration Registration**
- Create `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- Add the fully qualified name: `com.google.cloud.mcp.spring.McpToolboxAutoConfiguration`.

**5. Testing Strategy**
- Create a Spring Boot Context Test using `@SpringBootTest(classes = McpToolboxAutoConfiguration.class)`.
- Write unit tests utilizing Spring's `ApplicationContextRunner` to assert that:
  - The `McpToolboxClient` bean is correctly created when properties are provided.
  - The bean is *not* created if another bean of the same type is manually provided by the user.
  - The configuration processor correctly outputs the JSON metadata file.