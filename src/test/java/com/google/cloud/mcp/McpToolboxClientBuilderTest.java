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

package com.google.cloud.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpToolboxClientBuilderTest {

  @Test
  void testBaseUrlIsRequired() {
    IllegalArgumentException nullException =
        assertThrows(
            IllegalArgumentException.class,
            () -> McpToolboxClient.builder().build());
    assertEquals("Base URL must be provided", nullException.getMessage());

    IllegalArgumentException emptyException =
        assertThrows(
            IllegalArgumentException.class,
            () -> McpToolboxClient.builder().baseUrl("").build());
    assertEquals("Base URL must be provided", emptyException.getMessage());
  }

  @Test
  void testTrailingSlashRemoval() throws Exception {
    McpToolboxClient client = McpToolboxClient.builder().baseUrl("http://example.com/").build();
    
    Field baseUrlField = HttpMcpToolboxClient.class.getDeclaredField("baseUrl");
    baseUrlField.setAccessible(true);
    String baseUrl = (String) baseUrlField.get(client);
    
    assertEquals("http://example.com", baseUrl);
  }

  @Test
  void testHeadersAndApiKeyPriority() throws Exception {
    Map<String, String> headers = new HashMap<>();
    headers.put("Custom-Header", "Value");
    headers.put("Authorization", "Bearer custom-token");

    McpToolboxClient client = McpToolboxClient.builder()
        .baseUrl("http://example.com")
        .apiKey("api-key-token")
        .headers(headers)
        .build();

    Field headersField = HttpMcpToolboxClient.class.getDeclaredField("headers");
    headersField.setAccessible(true);
    @SuppressWarnings("unchecked")
    Map<String, String> clientHeaders = (Map<String, String>) headersField.get(client);

    // Verify headers map contains the provided ones
    assertEquals("Value", clientHeaders.get("Custom-Header"));
    assertEquals("Bearer custom-token", clientHeaders.get("Authorization"));
    
    // Verify apiKey is set
    Field apiKeyField = HttpMcpToolboxClient.class.getDeclaredField("apiKey");
    apiKeyField.setAccessible(true);
    String apiKey = (String) apiKeyField.get(client);
    assertEquals("api-key-token", apiKey);

    // Call getAuthorizationHeader to verify apiKey is prioritized
    java.lang.reflect.Method getAuthMethod = HttpMcpToolboxClient.class.getDeclaredMethod("getAuthorizationHeader");
    getAuthMethod.setAccessible(true);
    String authHeader = (String) getAuthMethod.invoke(client);
    assertEquals("Bearer api-key-token", authHeader);
  }

  @Test
  void testApiKeyBackwardsCompatibility() throws Exception {
    McpToolboxClient client = McpToolboxClient.builder()
        .baseUrl("http://example.com")
        .apiKey("my-api-key")
        .build();

    Field apiKeyField = HttpMcpToolboxClient.class.getDeclaredField("apiKey");
    apiKeyField.setAccessible(true);
    String apiKey = (String) apiKeyField.get(client);
    
    assertEquals("my-api-key", apiKey);
  }
}
