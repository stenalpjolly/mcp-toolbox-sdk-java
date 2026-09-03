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

import static com.google.cloud.mcp.e2e.ToolboxE2ESetup.getTextContent;
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
import java.util.LinkedHashMap;
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
          assertEquals("string", p.type());
        } else if ("data".equals(p.name())) {
          hasData = true;
          assertFalse(p.required(), "Parameter 'data' should be optional");
          assertEquals("string", p.type());
        } else if ("id".equals(p.name())) {
          hasId = true;
          assertFalse(p.required(), "Parameter 'id' should be optional");
          assertEquals("integer", p.type());
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
        ex.getCause() instanceof IllegalArgumentException,
        "Expected IllegalArgumentException but got: " + ex.getCause().getClass().getName());
    assertTrue(
        ex.getCause().getMessage().contains("Missing required parameter 'email'"),
        "Unexpected message: " + ex.getCause().getMessage());
  }

  @Test
  void testSearchRowsNonMatchingData() {
    Tool tool = client.loadTool("search-rows").join();
    Map<String, Object> args = new HashMap<>();
    args.put("email", "twishabansal@google.com");
    args.put("id", 3L);
    args.put("data", "row4");

    ToolResult result = tool.execute(args).join();
    assertFalse(result.isError(), "Expected success: " + getTextContent(result));
    String output = getTextContent(result).trim();
    assertTrue(
        output.isEmpty() || "null".equals(output),
        "Expected empty or 'null' response for non-matching data, got: " + output);
    assertFalse(output.contains("row1"));
    assertFalse(output.contains("row2"));
    assertFalse(output.contains("row3"));
  }

  // --- Map / Structured Payloads Suite (process-data) ---

  @Test
  void testProcessDataDefinitionSchema() {
    Tool tool = client.loadTool("process-data").join();
    assertEquals("process-data", tool.name());
    assertNotNull(tool.definition());

    boolean hasExecutionContext = false;
    boolean hasUserScores = false;
    boolean hasFeatureFlags = false;

    if (tool.definition().parameters() != null) {
      for (ToolDefinition.Parameter p : tool.definition().parameters()) {
        if ("execution_context".equals(p.name())) {
          hasExecutionContext = true;
          assertTrue(p.required(), "Parameter 'execution_context' should be required");
          assertNotNull(p.type());
          assertTrue(
              "object".equalsIgnoreCase(p.type()),
              "Parameter 'execution_context' type should be 'object', got: " + p.type());
        } else if ("user_scores".equals(p.name())) {
          hasUserScores = true;
          assertTrue(p.required(), "Parameter 'user_scores' should be required");
          assertNotNull(p.type());
          assertTrue(
              "object".equalsIgnoreCase(p.type()),
              "Parameter 'user_scores' type should be 'object', got: " + p.type());
        } else if ("feature_flags".equals(p.name())) {
          hasFeatureFlags = true;
          assertFalse(p.required(), "Parameter 'feature_flags' should be optional");
          assertNotNull(p.type());
          assertTrue(
              "object".equalsIgnoreCase(p.type()),
              "Parameter 'feature_flags' type should be 'object', got: " + p.type());
        }
      }
    }
    assertTrue(hasExecutionContext, "Missing required parameter 'execution_context' in definition");
    assertTrue(hasUserScores, "Missing required parameter 'user_scores' in definition");
    assertTrue(hasFeatureFlags, "Missing optional parameter 'feature_flags' in definition");
  }

  @Test
  void testProcessDataWithMapParams() {
    Tool tool = client.loadTool("process-data").join();
    Map<String, Object> execCtx = new LinkedHashMap<>();
    execCtx.put("env", "prod");
    execCtx.put("id", 1234);
    execCtx.put("user", 1234.5);

    Map<String, Object> userScores = new LinkedHashMap<>();
    userScores.put("user1", 100);
    userScores.put("user2", 200);

    Map<String, Object> featureFlags = new LinkedHashMap<>();
    featureFlags.put("new_feature", true);

    Map<String, Object> args = new LinkedHashMap<>();
    args.put("execution_context", execCtx);
    args.put("user_scores", userScores);
    args.put("feature_flags", featureFlags);

    ToolResult result = tool.execute(args).join();

    assertFalse(result.isError(), "Expected success: " + getTextContent(result));
    String output = getTextContent(result);
    assertTrue(
        output.contains("\"execution_context\":{\"env\":\"prod\",\"id\":1234,\"user\":1234.5}"),
        "Output did not contain expected execution_context: " + output);
    assertTrue(
        output.contains("\"user_scores\":{\"user1\":100,\"user2\":200}"),
        "Output did not contain expected user_scores: " + output);
    assertTrue(
        output.contains("\"feature_flags\":{\"new_feature\":true}"),
        "Output did not contain expected feature_flags: " + output);
  }

  @Test
  void testProcessDataOmittingOptionalMap() {
    Tool tool = client.loadTool("process-data").join();
    Map<String, Object> execCtx = new LinkedHashMap<>();
    execCtx.put("env", "dev");

    Map<String, Object> userScores = new LinkedHashMap<>();
    userScores.put("user3", 300);

    Map<String, Object> args = new LinkedHashMap<>();
    args.put("execution_context", execCtx);
    args.put("user_scores", userScores);

    ToolResult result = tool.execute(args).join();

    assertFalse(result.isError(), "Expected success: " + getTextContent(result));
    String output = getTextContent(result);
    assertTrue(
        output.contains("\"execution_context\":{\"env\":\"dev\"}"),
        "Output did not contain expected execution_context: " + output);
    assertTrue(
        output.contains("\"user_scores\":{\"user3\":300}"),
        "Output did not contain expected user_scores: " + output);
    assertTrue(
        output.contains("\"feature_flags\":null"),
        "Output did not contain expected null feature_flags: " + output);
  }
}
