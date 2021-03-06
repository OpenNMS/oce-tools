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

package org.opennms.oce.tools.onms.model.v1;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.Test;

public class SyslogAlarmDefinitionsTest {

    @Test
    public void canFormatTime() {
        LocalDateTime dt = LocalDateTime.of(2018, 5, 11, 1, 1, 1, 1);
        ZonedDateTime nowWithTimeZone = ZonedDateTime.of(dt, ZoneId.of("America/New_York"));
        assertThat(SyslogAlarmDefinitions.FORMATTER_A.format(nowWithTimeZone), equalTo("2018 May 11 01:01:01 EDT"));
        assertThat(SyslogAlarmDefinitions.FORMATTER_B.format(nowWithTimeZone), equalTo("May 11 01:01:01.000 EDT"));
    }
}
