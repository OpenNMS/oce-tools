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

import java.io.File;
import java.io.IOException;

import org.kohsuke.args4j.Option;
import org.opennms.oce.tools.cpn.reports.ReportTool;

public class CpnReportCommand extends AbstractCommand {
    public static final String NAME = "cpn-report";

    @Option(name="--url", usage="Executor service URL i.e. https://ana-cluster:6081/ana", required=true)
    private String url;

    @Option(name="--username",aliases = {"-u"}, usage="Username")
    private String username;

    @Option(name="--password",aliases = {"-p"}, usage="Password")
    private String password;

    @Option(name="--from",aliases = {"-f"}, usage="From date i.e. Oct 28 2018")
    private String from;

    @Option(name="--to",aliases = {"-t"}, usage="To date i.e. Oct 29 2018")
    private String to;

    @Option(name="--output",aliases = {"-o"}, usage="Target folder")
    private File targetFolder;

    @Option(name="--request-logging", usage="Enable request logging")
    private boolean requestLogging = false;

    @Option(name="--execute", usage="Execute")
    private File execute;

    public CpnReportCommand() {
        super(NAME);
    }

    @Override
    public void doExec(Context context) throws Exception {
        // Ensure the target directory exists
        if (!targetFolder.isDirectory() && !targetFolder.mkdirs()) {
            throw new IOException("Failed to create the target directory: " + targetFolder);
        }
        ReportTool.Builder builder = new ReportTool.Builder()
                .withUrl(url)
                .withUsername(username)
                .withPassword(password)
                .withLogging(requestLogging)
                .withDestinationFolder(targetFolder)
                .withExecute(execute);
        if (from != null) {
            builder.withStart(CommandUtils.parseDate(from));
        }
        if (to != null) {
            builder.withEnd(CommandUtils.parseDate(to));
        }
        builder.build().execute();
    }
}
