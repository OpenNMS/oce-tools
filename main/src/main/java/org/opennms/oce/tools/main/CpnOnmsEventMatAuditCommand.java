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

import org.kohsuke.args4j.Option;
import org.opennms.oce.tools.cpn.ESDataProvider;
import org.opennms.oce.tools.es.ESClient;
import org.opennms.oce.tools.onms.MatchCpnToOnmsEventsAudit;

public class CpnOnmsEventMatAuditCommand extends AbstractCommand {

    public static final String NAME = "cpn-opennms-event-materialization-audit";

    @Option(name="--from",aliases = {"-f"}, usage="From date i.e. Oct 28 2018", required=true)
    private String from;

    @Option(name="--to",aliases = {"-t"}, usage="To date i.e. Oct 29 2018", required = true)
    private String to;

    public CpnOnmsEventMatAuditCommand() {
        super(NAME);
    }

    @Override
    public void doExec(Context context) throws Exception {
        CommandUtils.DateRange range = CommandUtils.parseDateRange(from, to);
        ESClient esClient = context.getEsClient();
        ESDataProvider esDataProvider = new ESDataProvider(esClient);
        MatchCpnToOnmsEventsAudit audit = new MatchCpnToOnmsEventsAudit.Builder()
                .withStart(range.getStart())
                .withEnd(range.getEnd())
                .withDataProvider(esDataProvider)
                .build();
        audit.match();
    }
}
