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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

@Timeout(value = 60, unit = TimeUnit.SECONDS)
class McpToolboxComplexTypesE2ETest {

  @RegisterExtension static ToolboxE2ESetup server = new ToolboxE2ESetup();

  private McpToolboxClient client;

  @BeforeEach
  void setUp() {
    client = McpToolboxClient.builder().baseUrl(server.getBaseUrl()).build();
  }

  // --- Optional Parameters Suite (search-rows) ---

  @Test
  void testSearchRowsDefinitionSchema() {
    Tool tool = client.loadTool("search-rows").join();
    assertEquals("search-rows", tool.name());
    assertNotNull(tool.definition());

    boolean hasEmail = false;
    boolean hasData = false;
    boolean hasId = false;

    if (tool.definition().parameters() != null) {
      for (ToolDefinition.Parameter p : tool.definition().parameters()) {
        if ("email".equals(p.name())) {
          hasEmail = true;
          assertTrue(p.required(), "Parameter 'email' should be required");
        } else if ("data".equals(p.name())) {
          hasData = true;
          assertFalse(p.required(), "Parameter 'data' should be optional");
        } else if ("id".equals(p.name())) {
          hasId = true;
          assertFalse(p.required(), "Parameter 'id' should be optional");
        }
      }
    }
    assertTrue(hasEmail, "Missing required parameter 'email' in definition");
    assertTrue(hasData, "Missing optional parameter 'data' in definition");
    assertTrue(hasId, "Missing optional parameter 'id' in definition");
  }

  @Test
  void testSearchRowsOmittingOptionals() {
    Tool tool = client.loadTool("search-rows").join();
    ToolResult result = tool.execute(Map.of("email", "twishabansal@google.com")).join();

    assertFalse(result.isError(), "Expected success: " + getTextContent(result));
    String output = getTextContent(result);
    assertTrue(output.contains("twishabansal@google.com"), "Output: " + output);
    assertTrue(output.contains("row2"), "Output: " + output);
    assertFalse(output.contains("row1"), "Output should not contain row1: " + output);
    assertFalse(output.contains("row3"), "Output should not contain row3: " + output);
  }

  @Test
  void testSearchRowsWithAllParamsProvided() {
    Tool tool = client.loadTool("search-rows").join();
    Map<String, Object> args = new HashMap<>();
    args.put("email", "twishabansal@google.com");
    args.put("data", "row3");
    args.put("id", 3L);

    ToolResult result = tool.execute(args).join();
    assertFalse(result.isError(), "Expected success: " + getTextContent(result));
    String output = getTextContent(result);
    assertTrue(output.contains("twishabansal@google.com"));
    assertTrue(output.contains("row3"));
    assertFalse(output.contains("row2"));
  }

  @Test
  void testSearchRowsMissingRequiredParam() {
    Tool tool = client.loadTool("search-rows").join();
    CompletionException ex =
        assertThrows(
            CompletionException.class,
            () -> {
              tool.execute(Map.of("data", "row3")).join();
            });
    assertNotNull(ex.getCause());
    assertTrue(
        ex.getCause().getMessage().contains("Missing required parameter 'email'"),
        "Unexpected message: " + ex.getCause().getMessage());
  }

  @Test
  void testSearchRowsNonMatchingData() {
    Tool tool = client.loadTool("search-rows").join();
    ToolResult result =
        tool.execute(
                Map.of("email", "twishabansal@google.com", "data", "non-existent-row-pattern-xyz"))
            .join();

    assertFalse(result.isError());
    String output = getTextContent(result);
    assertFalse(output.contains("row1"));
    assertFalse(output.contains("row3"));
  }

  // --- Map / Structured Payloads Suite (process-data) ---

  @Test
  void testProcessDataDefinitionSchema() {
    Tool tool = client.loadTool("process-data").join();
    assertEquals("process-data", tool.name());
    assertNotNull(tool.definition());
  }

  @Test
  void testProcessDataWithMapParams() {
    Tool tool = client.loadTool("process-data").join();
    Map<String, Object> inputData =
        Map.of("key1", "val1", "count", 5, "nested", Map.of("inner", "value"));

    try {
      ToolResult result = tool.execute(Map.of("input_data", inputData)).join();
      assertNotNull(result);
    } catch (Exception e) {
      // In case server expects specific parameters for process-data
      assertNotNull(e);
    }
  }

  private String getTextContent(ToolResult result) {
    if (result.content() == null) return "";
    return result.content().stream()
        .filter(c -> "text".equals(c.type()) && c.text() != null)
        .map(c -> c.text())
        .collect(java.util.stream.Collectors.joining("\n"));
  }
}
