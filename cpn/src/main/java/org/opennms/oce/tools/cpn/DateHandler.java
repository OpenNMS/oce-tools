/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2018 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2018 The OpenNMS Group, Inc.
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

package org.opennms.oce.tools.cpn;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Objects;

/**
 * Parses the date format found in CPN's .csv files
 */
public class DateHandler {

    private final ZoneId zoneId;

    public DateHandler(ZoneId zoneId) {
        this.zoneId = Objects.requireNonNull(zoneId);
    }

    public Date parse(String str) {
        return Date.from(parseToZoneDateTime(str).toInstant());
    }

    public ZonedDateTime parseToZoneDateTime(String str) {
        // "2018-Apr-23, 10:27:41"
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MMM-dd, HH:mm:ss");
        final LocalDateTime dateTime = LocalDateTime.parse(str, formatter);
        return dateTime.atZone(zoneId);
    }

}
