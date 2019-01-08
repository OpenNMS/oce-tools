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
import java.time.DateTimeException;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.opennms.core.time.ZonedDateTimeBuilder;
import org.opennms.netmgt.syslogd.ByteBufferParser;
import org.opennms.netmgt.syslogd.RadixTreeSyslogParser;
import org.opennms.netmgt.syslogd.SyslogMessage;

class SyslogParser {
    private static final ByteBufferParser<SyslogMessage> parser = RadixTreeSyslogParser.getRadixParser();

    static SyslogMessage parse(String syslogMessageString) throws InterruptedException, ExecutionException {
        CompletableFuture<SyslogMessage> messageFuture = parser.parse(ByteBuffer.wrap(syslogMessageString.getBytes()));
        SyslogMessage syslogMessage = messageFuture.get();

        // Attempt to set the date if it wasn't already set
        if (syslogMessage.getDate() == null) {
            ZonedDateTimeBuilder zonedDateTimeBuilder = new ZonedDateTimeBuilder();

            if (syslogMessage.getYear() != null) {
                zonedDateTimeBuilder.setYear(syslogMessage.getYear());
            }
            if (syslogMessage.getMonth() != null) {
                zonedDateTimeBuilder.setMonth(syslogMessage.getMonth());
            }
            if (syslogMessage.getDayOfMonth() != null) {
                zonedDateTimeBuilder.setDayOfMonth(syslogMessage.getDayOfMonth());
            }
            if (syslogMessage.getHourOfDay() != null) {
                zonedDateTimeBuilder.setHourOfDay(syslogMessage.getHourOfDay());
            }
            if (syslogMessage.getMinute() != null) {
                zonedDateTimeBuilder.setMinute(syslogMessage.getMinute());
            }
            if (syslogMessage.getSecond() != null) {
                zonedDateTimeBuilder.setSecond(syslogMessage.getSecond());
            }
            if (syslogMessage.getMillisecond() != null) {
                zonedDateTimeBuilder.setNanosecond(syslogMessage.getMillisecond() * 1000);
            }
            if (syslogMessage.getZoneId() != null) {
                zonedDateTimeBuilder.setZoneId(syslogMessage.getZoneId());
            }

            try {
                ZonedDateTime time = zonedDateTimeBuilder.build();
                syslogMessage.setDate(Date.from(time.toInstant()));
            } catch (DateTimeException e) {
                e.printStackTrace();
            }
        }

        return syslogMessage;
    }
}
