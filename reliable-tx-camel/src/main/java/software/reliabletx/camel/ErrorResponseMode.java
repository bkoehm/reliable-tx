/*-
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.  The
 * ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package software.reliabletx.camel;

/**
 * @author Brian Koehmstedt
 */
public enum ErrorResponseMode {
    /* Consumer exception is put into the Out message as the reply and the
     * Camel Exchange is considered successful. That is, exchange.isFailed()
     * returns false and exchange.getException() returns null. In a JMS or
     * messaging context, the message never has a chance of being put on a
     * DLQ since it's considered a successful consumption. */
    EXCEPTION_AS_REPLY,

    /* Consumer exception is put into Camel's Exchange such that
     * exchange.getException() returns the exception and exchange.isFailed()
     * returns true. There is no Out message reply that is sent back (so this
     * is also the appropriate mode for InOnly pattern). In a JMS or
     * messaging context, the message has a chance of being put onto the DLQ
     * if the messaging broker is configured to do that for failed consumer
     * transactions. */
    EXCHANGE_FAILURE_NO_REPLY
}
