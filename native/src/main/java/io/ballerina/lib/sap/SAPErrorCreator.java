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
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;
public class SAPErrorCreator {
    public static BError fromJCoException(Throwable e) {
        return fromJavaException("JCo Error: " + e.getMessage(), e);
    }
    public static BError fromIDocException(IDocException e) {
        return fromJavaException("IDoc Error: " + e.getMessage(), e);
    }
    private static BError fromJavaException(String message, Throwable cause) {
        return fromBError(message, ErrorCreator.createError(cause));
    }
    public static BError fromBError(String message, BError cause) {
        return ErrorCreator.createError(
                ModuleUtils.getModule(), "Error", StringUtils.fromString(message), cause, null);
    }

    public static BError createError(String message, Throwable throwable) {
        BError cause = ErrorCreator.createError(throwable);
        return ErrorCreator.createError(
                ModuleUtils.getModule(), "Error", StringUtils.fromString(message), cause, null);
    }

    /**
     * Creates a {@code sap.jco:TransactionError} carrying the TID or bgRFC unit ID of the failed
     * transaction, so callers can retry with the same identifier and keep exactly-once semantics.
     */
    public static BError transactionError(String message, Throwable cause, String tid, String unitId) {
        BMap<BString, Object> detail = ValueCreator.createRecordValue(
                ModuleUtils.getModule(), SAPConstants.TRANSACTION_ERROR_DETAIL);
        if (tid != null) {
            detail.put(StringUtils.fromString(SAPConstants.TID_FIELD), StringUtils.fromString(tid));
        }
        if (unitId != null) {
            detail.put(StringUtils.fromString(SAPConstants.UNIT_ID), StringUtils.fromString(unitId));
        }
        BError bCause = cause == null ? null : ErrorCreator.createError(cause);
        return ErrorCreator.createError(ModuleUtils.getModule(), SAPConstants.TRANSACTION_ERROR,
                StringUtils.fromString(message), bCause, detail);
    }
}
