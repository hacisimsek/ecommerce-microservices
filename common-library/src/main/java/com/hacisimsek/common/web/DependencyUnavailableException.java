package com.hacisimsek.common.web;

/** A downstream service did not answer in time or its circuit is open. Maps to 503. */
public class DependencyUnavailableException extends RuntimeException {

    public DependencyUnavailableException(String dependency, Throwable cause) {
        super(dependency + " is temporarily unavailable", cause);
    }
}
