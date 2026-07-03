package com.quillapiclient.objects;

/**
 * Lightweight DTO that bundles all raw parameters needed for API execution.
 * Extracted from RequestPanel so callers don't extract fields one by one.
 */
public class ExecutionRequest {

    public final String url;
    public final String method;
    public final String headersText;
    public final String bodyText;
    public final String paramsText;
    public final String authType;
    public final String username;
    public final String password;
    public final String token;

    public ExecutionRequest(
        String url,
        String method,
        String headersText,
        String bodyText,
        String paramsText,
        String authType,
        String username,
        String password,
        String token
    ) {
        this.url = url;
        this.method = method;
        this.headersText = headersText;
        this.bodyText = bodyText;
        this.paramsText = paramsText;
        this.authType = authType;
        this.username = username;
        this.password = password;
        this.token = token;
    }
}
