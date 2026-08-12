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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.cloud.mcp.McpToolboxClient;
import com.google.cloud.mcp.ProtocolVersion;
import com.google.cloud.mcp.tool.Tool;
import com.google.cloud.mcp.tool.ToolResult;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

@Timeout(value = 60, unit = TimeUnit.SECONDS)
class McpToolboxProtocolE2ETest {

  @RegisterExtension static ToolboxE2ESetup server = new ToolboxE2ESetup();

  @Test
  void testClientWithCustomHeaders() {
    McpToolboxClient client =
        McpToolboxClient.builder()
            .baseUrl(server.getBaseUrl())
            .headers(Map.of("X-Integration-Test-Suite", "Java-Parity-E2E"))
            .build();

    Tool tool = client.loadTool("get-n-rows").join();
    assertNotNull(tool);
    ToolResult result = tool.execute(Map.of("num_rows", "2")).join();
    assertFalse(result.isError());
    String output = getTextContent(result);
    assertTrue(output.contains("row1"));
    assertTrue(output.contains("row2"));
  }

  @Test
  void testClientWithExplicitProtocolVersions() {
    ProtocolVersion[] versions =
        new ProtocolVersion[] {
          ProtocolVersion.VERSION_2024_11_05,
          ProtocolVersion.VERSION_2025_03_26,
          ProtocolVersion.VERSION_2025_06_18,
          ProtocolVersion.VERSION_2025_11_25
        };

    for (ProtocolVersion version : versions) {
      McpToolboxClient client =
          McpToolboxClient.builder().baseUrl(server.getBaseUrl()).protocolVersion(version).build();

      Tool tool = client.loadTool("get-n-rows").join();
      assertNotNull(tool, "Failed to load tool with protocol " + version);
      ToolResult result = tool.execute(Map.of("num_rows", "1")).join();
      assertFalse(result.isError(), "Execution failed for protocol " + version);
      String output = getTextContent(result);
      assertTrue(output.contains("row1"), "Expected row1 for protocol " + version);
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
