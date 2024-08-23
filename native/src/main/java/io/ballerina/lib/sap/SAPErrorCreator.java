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

public class SAPErrorCreator {
    public static BError fromJCoException(Throwable e) {
        return fromJavaException("JCo Error", e);
    }

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

    public static BError fromBError(String message, BError cause) {
        return ErrorCreator.createError(
                ModuleUtils.getModule(), "Error", StringUtils.fromString(message), cause, null);
    }

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

    public static BError createError(String message) {
        return ErrorCreator.createError(ModuleUtils.getModule(), "Error",
                StringUtils.fromString(message), null, null);
    }
}
