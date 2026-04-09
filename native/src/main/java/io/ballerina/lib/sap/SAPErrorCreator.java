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
import com.sap.conn.jco.JCoException;
import io.ballerina.runtime.api.creators.ErrorCreator;
import io.ballerina.runtime.api.creators.TypeCreator;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.types.PredefinedTypes;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;

/**
 * Factory for typed Ballerina error values that wrap SAP JCo and IDoc exceptions.
 * <p>
 * Each method creates the appropriate error subtype declared in {@code types.bal}:
 * <ul>
 *   <li>{@link #fromJCoException} routes {@link JCoException} to {@code ConnectionError},
 *       {@code LogonError}, {@code ResourceError}, {@code SystemError},
 *       {@code AbapApplicationError}, or {@code JCoError} based on the JCo error group.</li>
 *   <li>{@link #fromIDocException} produces an {@code IDocError}.</li>
 *   <li>{@link #createParameterError} produces a {@code ParameterError} for RFC parameter
 *       conversion and validation failures.</li>
 *   <li>{@link #createConfigError} produces a {@code ConfigurationError} for init and
 *       lifecycle failures.</li>
 * </ul>
 * Error messages are clean, human-readable strings. The original Java exception is attached
 * as the Ballerina error cause so that callers can inspect it via {@code error.cause()}.
 * No Java stack traces are embedded in error messages.
 */
public class SAPErrorCreator {

    private SAPErrorCreator() {
    }

    /**
     * Creates a typed Ballerina error from a {@link JCoException}, selecting the subtype based
     * on the JCo error group constant.
     *
     * @param e the JCo exception to wrap
     * @return a typed Ballerina error — one of {@code ConnectionError}, {@code LogonError},
     *         {@code ResourceError}, {@code SystemError}, {@code AbapApplicationError},
     *         or {@code JCoError}
     */
    public static BError fromJCoException(JCoException e) {
        int group = e.getGroup();
        String key = e.getKey();
        // JCoException.getMessage() returns SAP's verbose multi-line diagnostic block.
        // Trim to the first line so the Ballerina error message is a concise one-liner;
        // the full diagnostic is preserved in the cause chain via error.cause().
        String message = firstLine(e.getMessage(), "Unknown JCo error");
        BError cause = ErrorCreator.createError(e);

        String errorType;
        BMap<BString, Object> detail;

        switch (group) {
            case JCoException.JCO_ERROR_COMMUNICATION:
                errorType = SAPConstants.CONNECTION_ERROR_TYPE;
                detail = buildJCoDetail(group, key);
                break;
            case JCoException.JCO_ERROR_LOGON_FAILURE:
                errorType = SAPConstants.LOGON_ERROR_TYPE;
                detail = buildJCoDetail(group, key);
                break;
            case JCoException.JCO_ERROR_RESOURCE:
                errorType = SAPConstants.RESOURCE_ERROR_TYPE;
                detail = buildJCoDetail(group, key);
                break;
            case JCoException.JCO_ERROR_SYSTEM_FAILURE:
                errorType = SAPConstants.SYSTEM_ERROR_TYPE;
                detail = buildJCoDetail(group, key);
                break;
            case JCoException.JCO_ERROR_APPLICATION_EXCEPTION:
                errorType = SAPConstants.ABAP_APPLICATION_ERROR_TYPE;
                detail = buildAbapDetail(group, key, e);
                break;
            default:
                errorType = SAPConstants.JCO_ERROR_TYPE;
                detail = buildJCoDetail(group, key);
        }

        return ErrorCreator.createError(ModuleUtils.getModule(), errorType,
                StringUtils.fromString(message), cause, detail);
    }

    /**
     * Creates an {@code IDocError} from an {@link IDocException}.
     *
     * @param e the IDoc exception to wrap
     * @return a Ballerina {@code IDocError} with the exception as its cause
     */
    public static BError fromIDocException(IDocException e) {
        String message = firstLine(e.getMessage(), "Unknown IDoc error");
        BError cause = ErrorCreator.createError(e);
        return ErrorCreator.createError(ModuleUtils.getModule(), SAPConstants.IDOC_ERROR_TYPE,
                StringUtils.fromString(message), cause, null);
    }

    /**
     * Creates an {@code IDocError} wrapping an arbitrary Java throwable encountered during IDoc
     * processing. If the throwable is already a {@link BError} use
     * {@link #createIDocError(String, BError)} instead to avoid double-wrapping.
     *
     * @param message a human-readable description of what failed
     * @param e       the Java throwable to attach as cause
     * @return a Ballerina {@code IDocError}
     */
    public static BError createIDocError(String message, Throwable e) {
        BError cause = ErrorCreator.createError(e);
        return ErrorCreator.createError(ModuleUtils.getModule(), SAPConstants.IDOC_ERROR_TYPE,
                StringUtils.fromString(message), cause, null);
    }

    /**
     * Creates an {@code IDocError} wrapping an existing Ballerina error as its cause.
     * Use this when a Ballerina error (e.g. from XML parsing) must be forwarded as an
     * {@code IDocError} so that it satisfies the {@code Error} union type expected by
     * the service {@code onError} method.
     *
     * @param message a human-readable description of what failed
     * @param cause   the existing Ballerina error to attach as cause
     * @return a Ballerina {@code IDocError}
     */
    public static BError createIDocError(String message, BError cause) {
        return ErrorCreator.createError(ModuleUtils.getModule(), SAPConstants.IDOC_ERROR_TYPE,
                StringUtils.fromString(message), cause, null);
    }

    /**
     * Creates a {@code ParameterError} for RFC import/export parameter conversion failures.
     *
     * @param message a human-readable description of the parameter problem
     * @return a Ballerina {@code ParameterError}
     */
    public static BError createParameterError(String message) {
        return ErrorCreator.createError(ModuleUtils.getModule(), SAPConstants.PARAMETER_ERROR_TYPE,
                StringUtils.fromString(message), null, null);
    }

    /**
     * Creates a {@code ConfigurationError} for client/listener init and lifecycle failures,
     * attaching the Java exception as the error cause.
     *
     * @param message a human-readable description of what failed
     * @param e       the Java exception that triggered the failure
     * @return a Ballerina {@code ConfigurationError}
     */
    public static BError createConfigError(String message, Throwable e) {
        BError cause = ErrorCreator.createError(e);
        return ErrorCreator.createError(ModuleUtils.getModule(), SAPConstants.CONFIGURATION_ERROR_TYPE,
                StringUtils.fromString(message), cause, null);
    }

    /**
     * Creates a {@code ConfigurationError} with no underlying Java cause.
     *
     * @param message a human-readable description of the configuration problem
     * @return a Ballerina {@code ConfigurationError}
     */
    public static BError createConfigError(String message) {
        return ErrorCreator.createError(ModuleUtils.getModule(), SAPConstants.CONFIGURATION_ERROR_TYPE,
                StringUtils.fromString(message), null, null);
    }

    /**
     * Wraps an unexpected (non-JCo, non-IDoc) throwable as a {@code ConfigurationError}.
     * Use this for errors during client or listener initialization and lifecycle management.
     *
     * @param message a human-readable description of the context in which the error occurred
     * @param e       the throwable to attach as cause
     * @return a Ballerina {@code ConfigurationError}
     */
    public static BError fromThrowable(String message, Throwable e) {
        BError cause = ErrorCreator.createError(e);
        return ErrorCreator.createError(ModuleUtils.getModule(), SAPConstants.CONFIGURATION_ERROR_TYPE,
                StringUtils.fromString(message), cause, null);
    }

    /**
     * Wraps an unexpected (non-JCo, non-IDoc) throwable as an {@code ExecutionError}.
     * Use this for errors that occur during RFC execution or other runtime operations.
     *
     * @param message a human-readable description of the context in which the error occurred
     * @param e       the throwable to attach as cause
     * @return a Ballerina {@code ExecutionError}
     */
    public static BError fromExecutionThrowable(String message, Throwable e) {
        BError cause = ErrorCreator.createError(e);
        return ErrorCreator.createError(ModuleUtils.getModule(), SAPConstants.EXECUTION_ERROR_TYPE,
                StringUtils.fromString(message), cause, null);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static BMap<BString, Object> buildJCoDetail(int errorGroup, String key) {
        BMap<BString, Object> detail = ValueCreator.createMapValue(
                TypeCreator.createMapType(PredefinedTypes.TYPE_ANYDATA));
        detail.put(SAPConstants.DETAIL_ERROR_GROUP, (long) errorGroup);
        if (key != null && !key.isEmpty()) {
            detail.put(SAPConstants.DETAIL_KEY, StringUtils.fromString(key));
        }
        return detail;
    }

    private static BMap<BString, Object> buildAbapDetail(int errorGroup, String key, JCoException e) {
        // TODO: Extract individual ABAP message parts (message class, message number, message text,
        //  message variables V1-V4) once JCo exposes dedicated accessors for ABAP_EXCEPTION error groups.
        //  Currently JCoException does not provide these accessors; getKey() (e.g. "TABLE_NOT_AVAILABLE")
        //  is the only stable identifier available. Track progress in the connector issue tracker.
        return buildJCoDetail(errorGroup, key);
    }

    /**
     * Returns the first non-empty line of {@code raw}, or {@code fallback} if {@code raw} is
     * {@code null} or blank. SAP JCo exception messages are multi-line diagnostic blocks; callers
     * should use this to surface a concise one-liner while preserving the full text in the cause.
     */
    private static String firstLine(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        for (String line : raw.split("\n", -1)) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return fallback;
    }
}
