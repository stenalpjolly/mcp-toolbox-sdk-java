/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.mcp.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.cloud.mcp.McpToolboxClient;
import com.google.cloud.mcp.tool.Tool;
import com.google.cloud.mcp.tool.ToolDefinition;
import com.google.cloud.mcp.tool.ToolResult;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

@Timeout(value = 60, unit = TimeUnit.SECONDS)
class McpToolboxClientE2ETest {

  @RegisterExtension static ToolboxE2ESetup server = new ToolboxE2ESetup();

  private McpToolboxClient client;

  @BeforeEach
  void setUp() {
    client = McpToolboxClient.builder().baseUrl(server.getBaseUrl()).build();
  }

  // --- Toolset Loading & Error Tests ---

  @Test
  void testLoadToolsetSpecific() {
    Map<String, ToolDefinition> tools1 = client.loadToolset("my-toolset").join();
    assertEquals(1, tools1.size());
    assertTrue(tools1.containsKey("get-row-by-id"));

    Map<String, ToolDefinition> tools2 = client.loadToolset("my-toolset-2").join();
    assertEquals(2, tools2.size());
    assertTrue(tools2.containsKey("get-n-rows"));
    assertTrue(tools2.containsKey("get-row-by-id"));
  }

  @Test
  void testLoadToolsetDefault() {
    Map<String, ToolDefinition> tools = client.loadToolset().join();
    assertEquals(7, tools.size());
    assertTrue(tools.containsKey("get-row-by-content-auth"));
    assertTrue(tools.containsKey("get-row-by-email-auth"));
    assertTrue(tools.containsKey("get-row-by-id-auth"));
    assertTrue(tools.containsKey("get-row-by-id"));
    assertTrue(tools.containsKey("get-n-rows"));
    assertTrue(tools.containsKey("search-rows"));
    assertTrue(tools.containsKey("process-data"));
  }

  @Test
  void testLoadNonExistentToolset() {
    assertThrows(
        Exception.class,
        () -> {
          client.loadToolset("non-existent-toolset").join();
        });
  }

  @Test
  void testLoadNonExistentTool() {
    assertThrows(
        Exception.class,
        () -> {
          client.loadTool("non-existent-tool").join();
        });
  }

  // --- Tool Invocation & Argument Validations ---

  @Test
  void testRunTool() {
    Tool tool = client.loadTool("get-n-rows").join();
    ToolResult result = tool.execute(Map.of("num_rows", "2")).join();

    assertFalse(
        result.isError(), "Expected successful result, but got error: " + getTextContent(result));
    String output = getTextContent(result);
    assertTrue(output.contains("row1"), "Output didn't contain row1. Actual output: " + output);
    assertTrue(output.contains("row2"));
    assertFalse(output.contains("row3"));
  }

  @Test
  void testRunToolMissingRequiredParams() {
    Tool tool = client.loadTool("get-n-rows").join();
    CompletionException ex =
        assertThrows(
            CompletionException.class,
            () -> {
              tool.execute(Map.of()).join();
            });
    assertNotNull(ex.getCause());
    assertTrue(
        ex.getCause() instanceof IllegalArgumentException,
        "Expected IllegalArgumentException but got: " + ex.getCause().getClass().getName());
    assertTrue(
        ex.getCause().getMessage().contains("Missing required parameter 'num_rows'"),
        "Unexpected message: " + ex.getCause().getMessage());
  }

  @Test
  void testRunToolWrongParamType() {
    Tool tool = client.loadTool("get-n-rows").join();
    CompletionException ex =
        assertThrows(
            CompletionException.class,
            () -> {
              tool.execute(Map.of("num_rows", 2)).join();
            });
    assertNotNull(ex.getCause());
    assertTrue(
        ex.getCause() instanceof IllegalArgumentException,
        "Expected IllegalArgumentException but got: " + ex.getCause().getClass().getName());
    assertTrue(
        ex.getCause().getMessage().contains("expected type 'string'"),
        "Unexpected message: " + ex.getCause().getMessage());
  }

  // --- Parameter Binding & Schema Pruning ---

  @Test
  void testBindParams() {
    Tool tool = client.loadTool("get-n-rows").join();
    Tool boundTool = tool.bindParam("num_rows", "3");

    ToolResult result = boundTool.execute(Map.of()).join();
    String output = getTextContent(result);

    assertTrue(output.contains("row1"), "Actual output: " + output);
    assertTrue(output.contains("row2"));
    assertTrue(output.contains("row3"));
    assertFalse(output.contains("row4"));
  }

  @Test
  void testBindParamsCallable() {
    Tool tool = client.loadTool("get-n-rows").join();
    Tool boundTool = tool.bindParam("num_rows", () -> "3");

    ToolResult result = boundTool.execute(Map.of()).join();
    String output = getTextContent(result);

    assertTrue(output.contains("row1"), "Actual output: " + output);
    assertTrue(output.contains("row2"));
    assertTrue(output.contains("row3"));
    assertFalse(output.contains("row4"));
  }

  @Test
  void testBoundParamPruningSchema() {
    Tool tool = client.loadTool("get-n-rows").join();
    boolean hadParam =
        tool.definition().parameters() != null
            && tool.definition().parameters().stream().anyMatch(p -> "num_rows".equals(p.name()));
    assertTrue(hadParam, "Original tool definition should have 'num_rows' parameter");

    Tool boundTool = tool.bindParam("num_rows", "3");
    boolean hasParamAfter =
        boundTool.definition().parameters() != null
            && boundTool.definition().parameters().stream()
                .anyMatch(p -> "num_rows".equals(p.name()));
    assertFalse(hasParamAfter, "Bound parameter 'num_rows' must be pruned from definition schema");
  }

  // --- Authentication & Claim Injections ---

  @Test
  void testRunToolAuth() {
    Tool tool =
        client
            .loadTool("get-row-by-id-auth")
            .join()
            .addAuthTokenGetter(
                "my-test-auth", () -> CompletableFuture.completedFuture(server.getAuthToken1()));

    ToolResult result = tool.execute(Map.of("id", "2")).join();
    assertFalse(result.isError());
    String output = getTextContent(result);
    assertTrue(output.contains("row2"));
  }

  @Test
  void testRunToolWrongAuth() {
    Tool tool =
        client
            .loadTool("get-row-by-id-auth")
            .join()
            .addAuthTokenGetter(
                "my-test-auth", () -> CompletableFuture.completedFuture(server.getAuthToken2()));

    ToolResult result = tool.execute(Map.of("id", "2")).join();
    assertTrue(
        result.isError(),
        "Expected error for wrong auth. Actual output: " + getTextContent(result));
    assertTrue(
        getTextContent(result).toLowerCase().contains("unauthorized"),
        "Actual output: " + getTextContent(result));
  }

  @Test
  void testRunToolAuthWithoutProvidingAuth() {
    Tool tool = client.loadTool("get-row-by-id-auth").join();
    // Running authenticated tool without adding auth token getter
    try {
      ToolResult result = tool.execute(Map.of("id", "2")).join();
      assertTrue(
          result.isError(),
          "Expected error when invoking tool without auth token. Output: "
              + getTextContent(result));
    } catch (CompletionException e) {
      // An exception on unauthenticated execution is also valid
      assertNotNull(e.getCause());
    }
  }

  @Test
  void testRunToolParamAuth() {
    Tool tool =
        client
            .loadTool("get-row-by-email-auth")
            .join()
            .addAuthTokenGetter(
                "my-test-auth", () -> CompletableFuture.completedFuture(server.getAuthToken1()));

    ToolResult result = tool.execute(Map.of()).join();
    assertFalse(result.isError(), "Expected success but got error: " + getTextContent(result));
    String output = getTextContent(result);
    assertTrue(output.contains("row4"), "Actual output: " + output);
    assertTrue(output.contains("row5"));
    assertTrue(output.contains("row6"));
  }

  @Test
  void testRunToolParamAuthNoField() {
    Tool tool =
        client
            .loadTool("get-row-by-content-auth")
            .join()
            .addAuthTokenGetter(
                "my-test-auth", () -> CompletableFuture.completedFuture(server.getAuthToken1()));

    ToolResult result = tool.execute(Map.of()).join();
    assertTrue(result.isError());
    assertTrue(getTextContent(result).contains("no field named row_data"));
  }

  @Test
  void testRunToolWithFailingTokenSupplier() {
    Tool tool =
        client
            .loadTool("get-row-by-id-auth")
            .join()
            .addAuthTokenGetter(
                "my-test-auth",
                () -> CompletableFuture.failedFuture(new RuntimeException("Token unavailable")));

    assertThrows(
        Exception.class,
        () -> {
          tool.execute(Map.of("id", "2")).join();
        });
  }

  private String getTextContent(ToolResult result) {
    if (result.content() == null) return "";
    return result.content().stream()
        .filter(c -> "text".equals(c.type()) && c.text() != null)
        .map(c -> c.text())
        .collect(java.util.stream.Collectors.joining("\n"));
  }
}
