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
import org.opennms.oce.tools.cpn.view.CpnDatasetView;
import org.opennms.oce.tools.cpn.view.ESBackedCpnDatasetViewer;
import org.opennms.oce.tools.onms.onms2oce.HybridOnmsCpnOceGenerator;

import com.google.common.collect.Sets;

public class HybridOnmsCpnOceExportCommand extends AbstractCommand {

    public static final String NAME = "hybrid-onms-cpn-oce-onms-export";

    @Option(name="--from",aliases = {"-f"}, usage="From date i.e. Oct 28 2018")
    private String from;

    @Option(name="--to",aliases = {"-t"}, usage="To date i.e. Oct 29 2018")
    private String to;

    @Option(name="--ticket-id", usage="Ticket ID")
    private String ticketId;

    @Option(name="--output",aliases = {"-o"}, usage="Target folder", required = true)
    private File targetFolder;

    @Option(name="--exclude-service-events",usage="Exclude service events and alarms from tickets")
    private boolean excludeServiceEvents = true;

    @Option(name="--no-model",usage="Disable model generation")
    private boolean modelGenerationDisabled = false;

    @Option(name="--include-ticket-with-one-alarm",usage="Include tickets with a single event/alarm")
    private boolean includeTicketsWithASingleAlarm = false;

    public HybridOnmsCpnOceExportCommand() {
        super(NAME);
    }

    @Override
    public void doExec(Context context) throws Exception {
        final CpnDatasetView.Builder viewBuilder = new CpnDatasetView.Builder();
        if (from != null) {
            viewBuilder.withStartTime(CommandUtils.parseDate(from));
        }
        if (to != null) {
            viewBuilder.withEndTime(CommandUtils.parseDate(to));
        }
        viewBuilder.includeTicketsWithASingleAlarm(includeTicketsWithASingleAlarm);
        if (excludeServiceEvents) {
            viewBuilder.withEventTypes(Sets.newHashSet(CpnDatasetView.EventType.SYSLOG, CpnDatasetView.EventType.TRAP));
        }

        // Ensure the target directory exists
        if (!targetFolder.isDirectory() && !targetFolder.mkdirs()) {
            throw new IOException("Failed to create the target directory: " + targetFolder);
        }

        final HybridOnmsCpnOceGenerator oceGenerator = new HybridOnmsCpnOceGenerator.Builder()
                .withViewer(new ESBackedCpnDatasetViewer(context.getEsClient(), viewBuilder.build()))
                .withNodeAndFactsService(context.getNodeAndFactsService())
                .withTicketId(ticketId)
                .withTargetFolder(targetFolder)
                .withModelGenerationDisabled(modelGenerationDisabled)
                .build();
        oceGenerator.generate();
        oceGenerator.writeResultsToDisk();
    }
}
