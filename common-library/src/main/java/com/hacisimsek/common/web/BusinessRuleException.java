package com.hacisimsek.common.web;

/** A request that is well-formed but conflicts with the current state. Maps to 409 Conflict. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
