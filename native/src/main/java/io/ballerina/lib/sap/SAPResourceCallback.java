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

import io.ballerina.runtime.api.async.Callback;
import io.ballerina.runtime.api.values.BError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

/**
 * Callback implementation used to synchronize asynchronous Ballerina service method invocations
 * with the JCo IDoc handler thread. A {@link CountDownLatch} is decremented on every completion
 * (success or failure) so that the calling thread can block until the Ballerina strand finishes.
 */
public class SAPResourceCallback implements Callback {

    private static final Logger logger = LoggerFactory.getLogger(SAPResourceCallback.class);
    private final CountDownLatch countDownLatch;
    private volatile BError returnedError;

    public SAPResourceCallback(CountDownLatch countDownLatch) {
        this.countDownLatch = countDownLatch;
    }

    /**
     * Called when the Ballerina method completes without an unhandled panic.
     * If the return value is itself a {@link BError} (i.e., the method returned an error),
     * it is stored so that the calling handler can retrieve it via {@link #getReturnedError()}
     * and invoke the service error path.
     *
     * @param o the return value of the invoked Ballerina method (may be a {@link BError})
     */
    @Override
    public void notifySuccess(Object o) {
        if (o instanceof BError) {
            returnedError = (BError) o;
        }
        countDownLatch.countDown();
    }

    /**
     * Called when the Ballerina strand panics with an unrecoverable error.
     * The latch is decremented to unblock the waiting handler thread, and a
     * {@link RuntimeException} is thrown so the JCo worker thread can handle
     * or log the panic without terminating the JVM.
     *
     * @param bError the panic error from the Ballerina runtime
     */
    @Override
    public void notifyFailure(BError bError) {
        returnedError = bError;
        countDownLatch.countDown();
        logger.error("Ballerina strand panicked: {}", bError);
        throw new RuntimeException("Ballerina strand panicked: " + bError.getMessage(), bError);
    }

    /**
     * Returns the {@link BError} returned by the Ballerina method, or {@code null} if the
     * method completed successfully (returned {@code nil} or a non-error value).
     * Must be called only after the {@link CountDownLatch} has been counted down.
     *
     * @return the returned error, or {@code null}
     */
    public BError getReturnedError() {
        return returnedError;
    }
}
