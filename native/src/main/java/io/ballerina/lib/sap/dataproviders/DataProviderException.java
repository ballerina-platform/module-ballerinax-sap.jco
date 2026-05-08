/*
 * Copyright (c) 2026, WSO2 LLC. (http://www.wso2.org).
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

package io.ballerina.lib.sap.dataproviders;

/**
 * Thrown by {@link SAPDestinationDataProvider} and {@link SAPServerDataProvider} when a
 * requested destination or server name has not been registered.
 * <p>
 * JCo catches the exception thrown from {@code getDestinationProperties()} /
 * {@code getServerProperties()} and builds its own {@code JCoException} message by calling
 * {@link #toString()} on it. The default {@link RuntimeException#toString()} prepends the
 * fully-qualified class name ({@code java.lang.RuntimeException: …}), which leaks into the
 * JCo error message seen by callers. Overriding {@link #toString()} to return only the
 * message keeps JCo error text clean and human-readable.
 */
class DataProviderException extends RuntimeException {

    DataProviderException(String message) {
        super(message);
    }

    /**
     * Returns just the exception message, without the class name prefix, so that JCo
     * embeds clean text when it incorporates this exception into its own error message.
     *
     * @return the detail message string of this exception
     */
    @Override
    public String toString() {
        return getMessage();
    }
}
