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

import com.sap.conn.jco.server.JCoServerContext;
import com.sap.conn.jco.server.JCoServerTIDHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Accept-all in-memory TID (Transaction ID) handler with no deduplication or retry protection.
 * <p>
 * JCo calls these methods in the following order for each transactional IDoc delivery:
 * <ol>
 *   <li>{@link #checkTID} — asks whether the TID has already been processed</li>
 *   <li>{@link #handleRequest} (on the handler) — actual IDoc processing</li>
 *   <li>{@link #commit} — marks the TID as successfully processed</li>
 *   <li>{@link #confirmTID} — SAP confirms that it has received the commit acknowledgement</li>
 * </ol>
 * If processing fails, JCo calls {@link #rollback} instead of {@code commit}.
 * <p>
 * <strong>Limitations:</strong> {@link #checkTID} always returns {@code true}, accepting every
 * TID unconditionally. No processed-TID set or map is maintained, so duplicate IDoc delivery
 * (e.g., after a JVM restart or a network retry from SAP) will result in duplicate processing.
 * True at-most-once guarantees require adding durable TID storage (database, file, etc.) and
 * corresponding deduplication logic in {@link #checkTID}, {@link #commit}, {@link #rollback},
 * and {@link #confirmTID}.
 */
public class BallerinaTidHandler implements JCoServerTIDHandler {

    private static final Logger logger = LoggerFactory.getLogger(BallerinaTidHandler.class);

    /**
     * Checks whether the given TID has already been processed. This implementation always
     * returns {@code true}, accepting every TID. A persistent TID store would be needed to
     * reject duplicate deliveries after a JVM restart.
     *
     * @param serverCtx the current JCo server context
     * @param tid       the Transaction ID supplied by SAP
     * @return {@code true} to indicate the TID is new and processing should proceed
     */
    public boolean checkTID(JCoServerContext serverCtx, String tid) {
        logger.info("checkTID called for TID={}", tid);
        return true;
    }

    /**
     * Called by JCo after SAP has acknowledged the commit confirmation. The TID may safely
     * be removed from any persistent store at this point.
     *
     * @param serverCtx the current JCo server context
     * @param tid       the Transaction ID being confirmed
     */
    public void confirmTID(JCoServerContext serverCtx, String tid) {
        logger.info("confirmTID called for TID={}", tid);
    }

    /**
     * Called by JCo after the IDoc handler has successfully processed the document.
     * A persistent TID store should mark the TID as committed here.
     *
     * @param serverCtx the current JCo server context
     * @param tid       the Transaction ID being committed
     */
    public void commit(JCoServerContext serverCtx, String tid) {
        logger.info("commit called for TID={}", tid);
    }

    /**
     * Called by JCo when IDoc processing failed. Any work associated with {@code tid} should
     * be undone and the TID returned to a pending state so that SAP may retry the delivery.
     *
     * @param serverCtx the current JCo server context
     * @param tid       the Transaction ID being rolled back
     */
    public void rollback(JCoServerContext serverCtx, String tid) {
        logger.info("rollback called for TID={}", tid);
    }
}
