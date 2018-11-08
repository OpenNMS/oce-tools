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

package org.opennms.oce.tools.main;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import org.opennms.oce.tools.cpn.ESBackedCpnDataset;
import org.opennms.oce.tools.cpn.ESDataProvider;
import org.opennms.oce.tools.es.ESClient;

public class CommandUtils {

    public static DateRange parseDateRange(String from, String to) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d yyyy");
        LocalDate localStart = LocalDate.parse(from, formatter);
        ZonedDateTime zonedStartTime = localStart
                .atStartOfDay()
                .atZone(ZoneId.systemDefault());
        LocalDate localEnd = LocalDate.parse(to, formatter);
        ZonedDateTime zonedEndTime = localEnd
                .atStartOfDay()
                .atZone(ZoneId.systemDefault());
        return new DateRange(zonedStartTime, zonedEndTime);
    }

    public static ESBackedCpnDataset load(Context context, String from, String to) {
        // Parse the date range
        DateRange range = parseDateRange(from, to);

        // Load the data set
        ESClient esClient = context.getEsClient();
        ESDataProvider esDataProvider = new ESDataProvider(esClient);
        return new ESBackedCpnDataset(esDataProvider, range.getStart(), range.getEnd());
    }

    public static class DateRange {
        private ZonedDateTime start;
        private ZonedDateTime end;

        public DateRange(ZonedDateTime start, ZonedDateTime end) {
            this.start = Objects.requireNonNull(start);
            this.end = Objects.requireNonNull(end);
        }

        public ZonedDateTime getStart() {
            return start;
        }

        public ZonedDateTime getEnd() {
            return end;
        }
    }
}
