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

package io.ballerina.lib.sap.idoc;

/**
 * Thrown from {@link BallerinaIDocHandler#handleRequest} to tell JCo that an inbound IDoc could
 * not be rendered and was therefore not processed. JCo then fails the transactional RFC call, invokes
 * {@code JCoServerTIDHandler.rollback(tid)}, and reports the message to the SAP system, where the
 * call stays in error (SM58) so the IDoc can be reprocessed once the cause is fixed.
 */
public class IDocRejectedException extends RuntimeException {

    public IDocRejectedException(String message) {
        super(message);
    }

    public IDocRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
