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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.Test;

public class CommandUtilsTest {

    @Test
    public void canParseDateRanges() {
        CommandUtils.DateRange range = CommandUtils.parseDateRange("Nov 1 2018", "May 30 2019");
        ZonedDateTime expectedStart = LocalDate.of(2018, 11, 1)
                .atStartOfDay()
                .atZone(ZoneId.systemDefault());
        ZonedDateTime expectedEnd = LocalDate.of(2019, 5, 30)
                .atStartOfDay()
                .atZone(ZoneId.systemDefault());
        assertThat(range.getStart(), equalTo(expectedStart));
        assertThat(range.getEnd(), equalTo(expectedEnd));

        range = CommandUtils.parseDateRange("Nov 1 2018 01:00", "May 30 2019 23:59");
        expectedStart = LocalDateTime.of(2018, 11, 1, 1, 0)
                .atZone(ZoneId.systemDefault());
        expectedEnd = LocalDateTime.of(2019, 5, 30, 23, 59)
                .atZone(ZoneId.systemDefault());
        assertThat(range.getStart(), equalTo(expectedStart));
        assertThat(range.getEnd(), equalTo(expectedEnd));

        range = CommandUtils.parseDateRange("Nov 1 2018 01:00 America/Los_Angeles", "May 30 2019 23:59 America/Chicago");
        expectedStart = LocalDateTime.of(2018, 11, 1, 1, 0)
                .atZone(ZoneId.of("America/Los_Angeles"));
        expectedEnd = LocalDateTime.of(2019, 5, 30, 23, 59)
                .atZone(ZoneId.of("America/Chicago"));
        assertThat(range.getStart(), equalTo(expectedStart));
        assertThat(range.getEnd(), equalTo(expectedEnd));
    }
}
