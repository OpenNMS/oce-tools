/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2019 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2019 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.oce.tools.tsaudit;

import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.opennms.netmgt.syslogd.ByteBufferParser;
import org.opennms.netmgt.syslogd.RadixTreeSyslogParser;
import org.opennms.netmgt.syslogd.SyslogMessage;

public class SyslogParser {
    private static final ByteBufferParser<SyslogMessage> parser = RadixTreeSyslogParser.getRadixParser();

    public static SyslogMessage parse(String syslogMessageString) throws InterruptedException, ExecutionException {
        CompletableFuture<SyslogMessage> messageFuture = parser.parse(ByteBuffer.wrap(syslogMessageString.getBytes()));
        SyslogMessage syslogMessage = messageFuture.get();

        // Attempt to set the date if it wasn't already set
        if (syslogMessage.getDate() == null) {
            Calendar cal = Calendar.getInstance();

            if (syslogMessage.getYear() != null) {
                cal.set(Calendar.YEAR, syslogMessage.getYear());
            }
            if (syslogMessage.getMonth() != null) {
                // Calendar months are 0 indexed so we need to subtract one month here
                cal.set(Calendar.MONTH, syslogMessage.getMonth() - 1);
            }
            if (syslogMessage.getDayOfMonth() != null) {
                cal.set(Calendar.DAY_OF_MONTH, syslogMessage.getDayOfMonth());
            }
            if (syslogMessage.getHourOfDay() != null) {
                cal.set(Calendar.HOUR_OF_DAY, syslogMessage.getHourOfDay());
            }
            if (syslogMessage.getMinute() != null) {
                cal.set(Calendar.MINUTE, syslogMessage.getMinute());
            }
            if (syslogMessage.getSecond() != null) {
                cal.set(Calendar.SECOND, syslogMessage.getSecond());
            }
            if (syslogMessage.getMillisecond() != null) {
                cal.set(Calendar.MILLISECOND, syslogMessage.getMillisecond());
            }

            syslogMessage.setDate(cal.getTime());
        }

        return syslogMessage;
    }
}
