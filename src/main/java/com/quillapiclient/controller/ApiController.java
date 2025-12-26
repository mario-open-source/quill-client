package com.quillapiclient.controller;

import com.quillapiclient.server.ApiCallBuilder;
import com.quillapiclient.server.ApiResponse;
import com.quillapiclient.components.ResponsePanel;
import com.quillapiclient.db.CollectionDao;
import com.quillapiclient.utility.ResponseFormatter;

import javax.swing.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ApiController {
    private ResponsePanel responsePanel;
    private static final int NUMBER_OF_THREADS = 5;
    private static final ExecutorService executorService = 
        Executors.newFixedThreadPool(NUMBER_OF_THREADS);
    
    public ApiController(ResponsePanel responsePanel) {
        this.responsePanel = responsePanel;
    }
    
    public void executeApiCall(String url, String method, String headersText, 
                              String bodyText, String authType, String username, 
                              String password, String token, String paramsText, int itemId) {
        if (url.isEmpty()) {
            responsePanel.setResponse("Error: URL cannot be empty");
            responsePanel.resetStatusAndDuration();
            return;
        }
        
        // Show loading message
        String loadingMessage = createLoadingMessage(url, method, headersText, bodyText, authType);
        responsePanel.setResponse(loadingMessage);
        responsePanel.resetStatusAndDuration(); // Reset while loading
        
        // Submit the API call to the executor service
        executorService.submit(() -> {
            long startTime = System.currentTimeMillis();
            try {
                ApiResponse response = ApiCallBuilder.fromUI(
                    url, method, headersText, bodyText, authType,
                    username, password, token, paramsText
                ).execute();

                // Calculate duration and set it on the response
                long duration = System.currentTimeMillis() - startTime;
                response.setDuration(duration);

                // Save response to database if we have a valid item ID
                if (itemId > 0) {
                    int requestId = CollectionDao.getRequestIdByItemId(itemId);
                    if (requestId > 0) {
                        CollectionDao.saveResponse(response, requestId);
                    }
                }

                SwingUtilities.invokeLater(() ->
                    displayResponse(response)
                );
                
            } catch (Exception e) {
                SwingUtilities.invokeLater(() ->
                    displayError(e)
                );
            }
        });
    }
    
    private String createLoadingMessage(String url, String method, String headersText, 
                                       String bodyText, String authType) {
        StringBuilder sb = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        String timestamp = LocalDateTime.now().format(formatter);
        
        sb.append("[").append(timestamp).append("] Sending request...\n");
        sb.append("═".repeat(60)).append("\n");
        sb.append("URL: ").append(url).append("\n");
        sb.append("Method: ").append(method).append("\n");
        sb.append("Auth: ").append(authType).append("\n");
        
        if (headersText != null && !headersText.trim().isEmpty()) {
            sb.append("Headers: ").append(headersText.split("\n").length)
              .append(" header(s) configured\n");
        }
        
        if (bodyText != null && !bodyText.trim().isEmpty()) {
            sb.append("Body: ").append(bodyText.length())
              .append(" character(s)\n");
        }
        
        sb.append("═".repeat(60)).append("\n");
        sb.append("Waiting for response...\n");
        
        return sb.toString();
    }
    
    private void displayResponse(ApiResponse response) {
        // Use the unified ResponseFormatter utility
        String formattedResponse = ResponseFormatter.formatResponse(response, "Response received");
        
        // Update the response panel
        responsePanel.setResponse(formattedResponse);
        
        // Update status and duration labels
        responsePanel.setStatus(response.getStatusCode());
        responsePanel.setDuration(response.getDuration());
        
        // If there's an error status, show it more prominently
        if (!response.isSuccess()) {
            responsePanel.setErrorState(true);
        } else {
            responsePanel.setErrorState(false);
        }
    }
    
    private void displayError(Exception e) {
        StringBuilder errorBuilder = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        String timestamp = LocalDateTime.now().format(formatter);
        
        errorBuilder.append("[").append(timestamp).append("] ERROR: Request failed\n");
        errorBuilder.append("═".repeat(60)).append("\n");
        
        errorBuilder.append("EXCEPTION: ").append(e.getClass().getSimpleName()).append("\n\n");
        
        // Error message
        errorBuilder.append("MESSAGE:\n");
        errorBuilder.append("-".repeat(40)).append("\n");
        errorBuilder.append(e.getMessage() != null ? e.getMessage() : "No error message available");
        errorBuilder.append("\n\n");
        
        // Root cause if available
        if (e.getCause() != null) {
            errorBuilder.append("ROOT CAUSE:\n");
            errorBuilder.append("-".repeat(40)).append("\n");
            errorBuilder.append(e.getCause().getMessage());
            errorBuilder.append("\n\n");
        }
        
        // Stack trace (limited)
        errorBuilder.append("STACK TRACE (first 5 lines):\n");
        errorBuilder.append("-".repeat(40)).append("\n");
        StackTraceElement[] stackTrace = e.getStackTrace();
        int lines = Math.min(5, stackTrace.length);
        for (int i = 0; i < lines; i++) {
            errorBuilder.append("  at ").append(stackTrace[i].toString()).append("\n");
        }
        
        if (stackTrace.length > 5) {
            errorBuilder.append("  ... and ").append(stackTrace.length - 5)
                       .append(" more lines\n");
        }
        
        errorBuilder.append("\n").append("═".repeat(60)).append("\n");
        errorBuilder.append("TROUBLESHOOTING:\n");
        
        // Add helpful troubleshooting tips based on exception type
        if (e instanceof java.net.UnknownHostException) {
            errorBuilder.append("  • Check your internet connection\n");
            errorBuilder.append("  • Verify the domain name is correct\n");
            errorBuilder.append("  • Try pinging the hostname\n");
        } else if (e instanceof java.net.ConnectException) {
            errorBuilder.append("  • The server may be down or unreachable\n");
            errorBuilder.append("  • Check firewall settings\n");
            errorBuilder.append("  • Verify the port number\n");
        } else if (e instanceof javax.net.ssl.SSLHandshakeException) {
            errorBuilder.append("  • SSL certificate issue\n");
            errorBuilder.append("  • Try disabling SSL verification (for testing)\n");
            errorBuilder.append("  • Check certificate validity\n");
        } else if (e instanceof java.net.SocketTimeoutException) {
            errorBuilder.append("  • Request timed out\n");
            errorBuilder.append("  • Server might be busy\n");
            errorBuilder.append("  • Increase timeout settings\n");
        } else {
            errorBuilder.append("  • Check your request parameters\n");
            errorBuilder.append("  • Verify the API endpoint\n");
            errorBuilder.append("  • Ensure all required fields are filled\n");
        }
        
        // Update the response panel
        responsePanel.setResponse(errorBuilder.toString());
        responsePanel.setErrorState(true);
        responsePanel.resetStatusAndDuration(); // Reset on error (no valid response)
    }
    
    /**
     * Shuts down the executor service gracefully.
     * Waits for running tasks to complete, then terminates.
     * 
     * @param timeoutSeconds Maximum time to wait for tasks to complete
     * @return true if shutdown completed, false if timeout occurred
     */
    public static boolean shutdownGracefully(long timeoutSeconds) {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                // Timeout occurred, force shutdown
                executorService.shutdownNow();
                // Wait a bit more for forced shutdown
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("ExecutorService did not terminate");
                    return false;
                }
            }
            return true;
        } catch (InterruptedException e) {
            // Thread was interrupted, force shutdown
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
            return false;
        }
    }
    
    /**
     * Shuts down the executor service gracefully with default timeout (10 seconds).
     * 
     * @return true if shutdown completed, false if timeout occurred
     */
    public static boolean shutdownGracefully() {
        return shutdownGracefully(10);
    }
    
    /**
     * Immediately shuts down the executor service.
     * Attempts to stop all actively executing tasks and halts processing.
     * 
     * @return List of tasks that were awaiting execution
     */
    public static java.util.List<Runnable> shutdownNow() {
        return executorService.shutdownNow();
    }
    
    /**
     * Initiates shutdown but does not wait for completion.
     * Use shutdownGracefully() for proper shutdown.
     */
    public static void shutdown() {
        executorService.shutdown();
    }
    
}