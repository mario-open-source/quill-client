package com.quillapiclient.objects;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Set of configurations used to alter the usual behavior of sending the request
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProtocolProfileBehavior {
    // This is an extensible object - properties can be added as needed
    // Common properties might include followRedirects, etc.
}
