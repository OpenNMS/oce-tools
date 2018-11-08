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
import java.time.ZoneId;

import org.kohsuke.args4j.Option;
import org.opennms.oce.tools.cpn.CsvCpnDatasetLoader;
import org.opennms.oce.tools.cpn.CpnDataset;
import org.opennms.oce.tools.cpn.model.EventRecord;
import org.opennms.oce.tools.cpn.model.TicketRecord;
import org.opennms.oce.tools.es.ESClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CpnCsvImportCommand extends AbstractCommand {

    private static final Logger LOG = LoggerFactory.getLogger(CpnCsvImportCommand.class);

    public static final String NAME = "cpn-csv-import";
    public static final String DEFAULT_TIME_ZONE_ID = "America/Chicago";

    @Option(name="--source",aliases = {"-s"}, usage="Source folder containing the .csv files to import", required=true)
    private File sourceFolder;

    @Option(name="--timezone",aliases = {"-t"}, usage="Time-zone to use when importing the CPN records")
    private String timeZone = DEFAULT_TIME_ZONE_ID;

    public CpnCsvImportCommand() {
        super(NAME);
    }

    @Override
    public void doExec(Context context) throws Exception {
        final ZoneId timeZoneId = ZoneId.of(timeZone);
        LOG.info("Loading data from source folder: {}", sourceFolder);
        final CpnDataset dataset = CsvCpnDatasetLoader.loadDataset(sourceFolder, timeZoneId);
        LOG.info("Indexing dataset in Elasticsarch.");
        final ESClient esClient = context.getEsClient();
        esClient.bulkIndex(dataset.getTickets(), "tickets", "ticket", TicketRecord::getTicketId);
        esClient.bulkIndex(dataset.getServiceEvents(), "services", "service", EventRecord::getEventId);
        esClient.bulkIndex(dataset.getSyslogEvents(), "syslogs", "syslog", EventRecord::getEventId);
        esClient.bulkIndex(dataset.getTraps(), "traps", "trap", EventRecord::getEventId);
        LOG.info("Done indexing dataset in Elasticsearch.");
    }

}
