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

package io.ballerina.lib.sap.idochandlers;

import com.sap.conn.jco.server.JCoServerContext;
import com.sap.conn.jco.server.JCoServerTIDHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BallerinaTidHandler implements JCoServerTIDHandler {

    private static final Logger logger = LoggerFactory.getLogger(BallerinaTidHandler.class);

    public boolean checkTID(JCoServerContext serverCtx, String tid) {
        logger.info("checkTID called for TID={}", tid);
        return true;
    }

    public void confirmTID(JCoServerContext serverCtx, String tid) {
        logger.info("confirmTID called for TID={}", tid);
    }

    public void commit(JCoServerContext serverCtx, String tid) {
        logger.info("commit called for TID={}", tid);
    }

    public void rollback(JCoServerContext serverCtx, String tid) {
        logger.info("rollback called for TID={}", tid);
    }
}
