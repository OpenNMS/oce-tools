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

package org.opennms.oce.tools.cpn.reports;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;

import com.google.common.io.Resources;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

public class ReportToolTest {

    @Test
    public void canParseReportsFromXml() throws IOException {
        final String xml = Resources.toString(Resources.getResource("report.response.xml"), StandardCharsets.UTF_8);
        final List<Report> reports = ReportTool.getReportsFromXml(xml);
        assertThat(reports, hasSize(2));

        Report report1 = reports.get(0);
        assertThat(report1.getId(), equalTo(7459L));
        assertThat(report1.getType(), equalTo("Detailed Tickets"));
        assertThat(report1.getName(), equalTo("Daily_Detailed_Tickets"));
        assertThat(report1.getCreationTime(), equalTo(ZonedDateTime.of(2018, 10, 22,
                11, 10, 0, 0,
                ZoneId.of("US/Central"))));

        Report report2 = reports.get(1);
        assertThat(report2.getId(), equalTo(7221L));
        assertThat(report2.getType(), equalTo("Detailed Tickets"));
        assertThat(report2.getName(), equalTo("Daily_Detailed_Tickets"));
        assertThat(report2.getCreationTime(), equalTo(ZonedDateTime.of(2018, 10, 6,
                11, 10, 0, 0,
                ZoneId.of("US/Central"))));
    }

    @Ignore
    @Test
    public void canDownloadFile() throws IOException {
        OkHttpClient client = new OkHttpClient();
        HttpUrl url = HttpUrl.parse("http://localhost:8000/August9th_Daily_Detailed_Traps.CSV");
        File dst = new File("/tmp/out.csv");
        ReportTool.downloadFile(client, url, new File("/tmp/out.csv"));
        assertThat(dst.canRead(), equalTo(true));
    }
}
