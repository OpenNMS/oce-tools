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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.ExecutionException;

import org.junit.Test;
import org.junit.Ignore;

@Ignore("failing - weird syslog date math")
public class GenericSyslogMessageTest {
    private static final String dateString = "Jul 17 04:36:01.993";
    private static final String message = "%CDP-4-NATIVE_VLAN_MISMATCH: Native VLAN mismatch discovered on " +
            "GigabitEthernet0/43 (503), " +
            "with Switch GigabitEthernet1/0/24 (1).";
    private static final String syslogMessageString = "<188>1421602: " + dateString + ": " + message;
    private static final String host = "testhost";
    private static final Integer onmsId = 1;

    @Test
    public void testCompare() throws ParseException {
        GenericSyslogMessage messageFromCpn = GenericSyslogMessage.fromCpn("id", host, syslogMessageString);
        Date currentDate = new Date();
        // prepend the year since parser assumes current year
        Date expectedDate =
                new SimpleDateFormat("yyyy MMM dd hh:mm:ss.SSS").parse(Calendar.getInstance().get(Calendar.YEAR) + " "
                        + dateString);

        // Hack to correct the year to previous year if the date without year happens after now
        if(expectedDate.getTime() > currentDate.getTime()) {
            expectedDate = new SimpleDateFormat("yyyy MMM dd hh:mm:ss.SSS").parse((Calendar.getInstance().get(Calendar.YEAR) - 1) + " "
                    + dateString);
        }
        GenericSyslogMessage messageFromOnms = GenericSyslogMessage.fromOnms(onmsId, host, message, expectedDate);
        assertThat(messageFromCpn, is(equalTo(messageFromOnms)));
        assertThat(messageFromCpn.anyMatch(Collections.singleton(messageFromOnms)), is(equalTo(true)));

        messageFromOnms = GenericSyslogMessage.fromOnms(onmsId, "different", message, expectedDate);
        assertThat(messageFromCpn, not(equalTo(messageFromOnms)));
        assertThat(messageFromCpn.anyMatch(Collections.singleton(messageFromOnms)), is(equalTo(false)));
    }
}
