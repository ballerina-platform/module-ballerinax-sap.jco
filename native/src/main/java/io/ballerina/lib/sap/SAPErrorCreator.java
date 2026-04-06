/*
 * Copyright (c) 2024, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.lib.sap;

import com.sap.conn.idoc.IDocException;
import io.ballerina.runtime.api.creators.ErrorCreator;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BError;

import java.util.Arrays;

/**
 * Factory for Ballerina {@link BError} values that wrap SAP JCo and IDoc exceptions.
 * All errors are created under the module-level {@code "Error"} type so that Ballerina callers
 * can match them with a single {@code error} catch clause.
 */
public class SAPErrorCreator {

    /**
     * Wraps an arbitrary {@link Throwable} (typically a {@link com.sap.conn.jco.JCoException})
     * as a Ballerina error prefixed with {@code "JCo Error"}.
     *
     * @param e the throwable to wrap
     * @return a Ballerina {@code Error} with a message containing the exception message, cause, and stack trace
     */
    public static BError fromJCoException(Throwable e) {
        return fromJavaException("JCo Error", e);
    }

    /**
     * Wraps an {@link IDocException} as a Ballerina error prefixed with {@code "IDoc Error"}.
     *
     * @param e the IDoc exception to wrap
     * @return a Ballerina {@code Error} with a message containing the exception message, cause, and stack trace
     */
    public static BError fromIDocException(IDocException e) {
        return fromJavaException("IDoc Error", e);
    }

    private static BError fromJavaException(String errorType, Throwable e) {
        String causeString = (e.getCause() != null && e.getCause().getMessage() != null)
                ? e.getCause().getMessage()
                : "No cause";

        String message = (e.getMessage() != null) ? e.getMessage() : "Unknown error";

        String fullMessage = errorType + ": " + message
                + " | Cause: " + causeString
                + " | Stack Trace: " + Arrays.toString(e.getStackTrace());

        return fromBError(fullMessage, ErrorCreator.createError(e));
    }

    /**
     * Creates a Ballerina module-level {@code Error} with the given message and an optional cause.
     *
     * @param message a human-readable error description
     * @param cause   an optional Ballerina {@link BError} to attach as the cause; may be {@code null}
     * @return the constructed Ballerina error
     */
    public static BError fromBError(String message, BError cause) {
        return ErrorCreator.createError(
                ModuleUtils.getModule(), "Error", StringUtils.fromString(message), cause, null);
    }

    /**
     * Creates a Ballerina module-level {@code Error} from an arbitrary Java exception,
     * prepending a custom {@code bMessage} to the Java exception's own message and cause.
     *
     * @param bMessage a Ballerina-facing prefix describing the operation that failed
     * @param e        the Java exception to wrap; must not be {@code null}
     * @return the constructed Ballerina error
     */
    public static BError createError(String bMessage, Throwable e) {
        Throwable cause = e.getCause();
        String causeString = "No cause";
        if (cause != null && cause.getMessage() != null) {
            causeString = cause.getMessage();
        }
        String message = (e.getMessage() != null) ? e.getMessage() : "Unknown error";
        String fullMessage = bMessage + "| Message: " + message + "| Cause: " + causeString + "| Stack Trace: "
                + Arrays.toString(e.getStackTrace());
        BError bCause = ErrorCreator.createError(e);
        return ErrorCreator.createError(ModuleUtils.getModule(), "Error",
                StringUtils.fromString(fullMessage), bCause, null);
    }

    /**
     * Creates a Ballerina module-level {@code Error} with only a message and no cause.
     * Prefer this overload when the error originates from Ballerina-level validation rather
     * than a Java exception (e.g., a missing mandatory parameter).
     *
     * @param message a human-readable error description
     * @return the constructed Ballerina error
     */
    public static BError createError(String message) {
        return ErrorCreator.createError(ModuleUtils.getModule(), "Error",
                StringUtils.fromString(message), null, null);
    }
}
