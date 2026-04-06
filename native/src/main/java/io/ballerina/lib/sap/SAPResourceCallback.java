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

    public SAPResourceCallback(CountDownLatch countDownLatch) {
        this.countDownLatch = countDownLatch;
    }

    /**
     * Called when the Ballerina method completes without an unhandled panic.
     * If the return value is itself a {@link BError} (i.e., the method returned an error),
     * it is logged but processing is otherwise considered complete.
     *
     * @param o the return value of the invoked Ballerina method (may be a {@link BError})
     */
    @Override
    public void notifySuccess(Object o) {
        if (o instanceof BError) {
            logger.error("Error occurred: " + o);
        }
        countDownLatch.countDown();
    }

    /**
     * Called when the Ballerina strand panics with an unrecoverable error.
     * The latch is decremented first to unblock the waiting handler thread,
     * and then the JVM process is terminated because continuing after a panic
     * would leave the server in an undefined state.
     *
     * @param bError the panic error from the Ballerina runtime
     */
    @Override
    public void notifyFailure(BError bError) {
        countDownLatch.countDown();
        logger.error("Error occurred: " + bError.toString());
        System.exit(1);
    }
}
