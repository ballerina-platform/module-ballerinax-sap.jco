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

import com.sap.conn.jco.server.JCoServer;
import com.sap.conn.jco.server.JCoServerContextInfo;
import com.sap.conn.jco.server.JCoServerErrorListener;
import com.sap.conn.jco.server.JCoServerExceptionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs JCo server-level errors and exceptions that are not tied to a specific IDoc delivery.
 * Implements both {@link JCoServerErrorListener} (for {@link Error}-level JVM problems) and
 * {@link JCoServerExceptionListener} (for checked/runtime exceptions raised by the JCo server
 * framework). Errors and exceptions are forwarded to SLF4J at {@code ERROR} level.
 */
public class BallerinaThrowableListener implements JCoServerErrorListener, JCoServerExceptionListener {

    private static final Logger logger = LoggerFactory.getLogger(BallerinaThrowableListener.class);

    /**
     * Called by JCo when a {@link Error}-level throwable is raised on the server.
     *
     * @param jCoServer            the server that raised the error
     * @param s                    a descriptive session/connection identifier string supplied by JCo
     * @param jCoServerContextInfo additional context about the server state when the error occurred
     * @param error                the JVM {@link Error} that was raised
     */
    @Override
    public void serverErrorOccurred(JCoServer jCoServer, String s, JCoServerContextInfo jCoServerContextInfo,
                                    Error error) {
        logger.error("Server error occurred: {}", error.getMessage());
    }

    /**
     * Called by JCo when a checked or runtime {@link Exception} is raised on the server.
     *
     * @param jCoServer            the server that raised the exception
     * @param s                    a descriptive session/connection identifier string supplied by JCo
     * @param jCoServerContextInfo additional context about the server state when the exception occurred
     * @param e                    the exception that was raised
     */
    @Override
    public void serverExceptionOccurred(JCoServer jCoServer, String s, JCoServerContextInfo jCoServerContextInfo,
                                        Exception e) {
        logger.error("Server exception occurred: {}", e.getMessage());
    }
}
