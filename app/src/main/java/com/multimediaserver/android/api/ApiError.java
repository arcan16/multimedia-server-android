package com.multimediaserver.android.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Any failed call. {@code status} is 0 when the server could not be reached.
 * {@code getMessage()} is user-facing Spanish and is shown as-is.
 * {@code fieldErrors} maps field -> message for bean-validation lists.
 */
public class ApiError extends Exception {
    public final int status;
    public final Map<String, String> fieldErrors;

    public ApiError(int status, String message) {
        this(status, message, new HashMap<>());
    }

    public ApiError(int status, String message, Map<String, String> fieldErrors) {
        super(message);
        this.status = status;
        this.fieldErrors = Collections.unmodifiableMap(fieldErrors);
    }

    public String field(String name) {
        return fieldErrors.get(name);
    }
}
