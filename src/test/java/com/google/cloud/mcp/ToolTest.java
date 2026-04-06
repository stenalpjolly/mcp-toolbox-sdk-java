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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ToolTest {

    private McpToolboxClient mockClient;

    @BeforeEach
    void setUp() {
        mockClient = mock(McpToolboxClient.class);
    }

    @Test
    void testExecuteWithBoundParameters() throws Exception {
        ToolDefinition definition = new ToolDefinition("Test Tool", Collections.emptyList(), Collections.emptyList());
        Tool tool = new Tool("testTool", definition, mockClient);

        // 1. Bind static and supplier params
        tool.bindParam("staticParam", "staticValue");
        tool.bindParam("dynamicParam", (Supplier<Object>) () -> "dynamicValue");

        ToolResult expectedResult = new ToolResult(Collections.emptyList(), false);
        when(mockClient.invokeTool(eq("testTool"), anyMap(), anyMap()))
                .thenReturn(CompletableFuture.completedFuture(expectedResult));

        Map<String, Object> inputArgs = new HashMap<>();
        inputArgs.put("inputParam", "inputValue");

        ToolResult result = tool.execute(inputArgs).get();

        assertEquals(expectedResult, result);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> argsCaptor = ArgumentCaptor.forClass(Map.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);

        verify(mockClient).invokeTool(eq("testTool"), argsCaptor.capture(), headersCaptor.capture());

        Map<String, Object> finalArgs = argsCaptor.getValue();
        assertEquals("staticValue", finalArgs.get("staticParam"));
        assertEquals("dynamicValue", finalArgs.get("dynamicParam"));
        assertEquals("inputValue", finalArgs.get("inputParam"));
    }

    @Test
    void testExecuteThrowsWhenRequiredParameterMissing() {
        ToolDefinition.Parameter param = new ToolDefinition.Parameter(
                "reqParam", "string", true, "desc", null);
        ToolDefinition definition = new ToolDefinition("Desc", List.of(param), Collections.emptyList());
        Tool tool = new Tool("testTool", definition, mockClient);

        Map<String, Object> inputArgs = new HashMap<>();

        // 2. Ensure execute throws IllegalArgumentException (wrapped in ExecutionException)
        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            tool.execute(inputArgs).get();
        });

        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("Missing required parameter 'reqParam'"));
    }

    @Test
    void testExecuteThrowsOnTypeMismatch() {
        ToolDefinition.Parameter intParam = new ToolDefinition.Parameter(
                "intParam", "integer", false, "desc", null);
        ToolDefinition.Parameter boolParam = new ToolDefinition.Parameter(
                "boolParam", "boolean", false, "desc", null);
        ToolDefinition definition = new ToolDefinition("Desc", List.of(intParam, boolParam), Collections.emptyList());
        Tool tool = new Tool("testTool", definition, mockClient);

        Map<String, Object> inputArgs = new HashMap<>();
        // 3. Validate type checking: integer parameter receives a string
        inputArgs.put("intParam", "this-is-a-string");

        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            tool.execute(inputArgs).get();
        });

        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("Parameter 'intParam' expected type 'integer' but got 'String'"));

        // Validate type checking: boolean parameter receives an integer
        inputArgs.clear();
        inputArgs.put("boolParam", 123);

        exception = assertThrows(ExecutionException.class, () -> {
            tool.execute(inputArgs).get();
        });

        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("Parameter 'boolParam' expected type 'boolean' but got 'Integer'"));
    }

    @Test
    void testAddAuthTokenGetter() throws Exception {
        ToolDefinition.Parameter param = new ToolDefinition.Parameter(
                "tokenParam", "string", false, "desc", List.of("myService"));
        ToolDefinition definition = new ToolDefinition("Desc", List.of(param), Collections.emptyList());
        Tool tool = new Tool("testTool", definition, mockClient);

        // 4. Verify that when added, the token is fetched asynchronously
        tool.addAuthTokenGetter("myService", () -> CompletableFuture.supplyAsync(() -> "test-token"));

        ToolResult expectedResult = new ToolResult(Collections.emptyList(), false);
        when(mockClient.invokeTool(eq("testTool"), anyMap(), anyMap()))
                .thenReturn(CompletableFuture.completedFuture(expectedResult));

        Map<String, Object> inputArgs = new HashMap<>();
        tool.execute(inputArgs).get();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> argsCaptor = ArgumentCaptor.forClass(Map.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);

        // Intercepting invokeTool to verify params and headers
        verify(mockClient).invokeTool(eq("testTool"), argsCaptor.capture(), headersCaptor.capture());

        Map<String, Object> finalArgs = argsCaptor.getValue();
        // Included in the params before dispatching to the client
        assertEquals("test-token", finalArgs.get("tokenParam"));

        Map<String, String> finalHeaders = headersCaptor.getValue();
        // Included in the headers before dispatching to the client
        assertEquals("Bearer test-token", finalHeaders.get("Authorization"));
        assertEquals("test-token", finalHeaders.get("myService_token"));
    }
}