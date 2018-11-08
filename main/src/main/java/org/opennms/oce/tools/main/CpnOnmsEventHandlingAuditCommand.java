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

import java.net.InetAddress;

import org.kohsuke.args4j.Option;
import org.opennms.oce.tools.onms.AlarmHandlingAudit;
import org.opennms.oce.tools.onms.client.OpennmsRestClient;
import org.opennms.oce.tools.onms.model.mock.MockNetwork;

public class CpnOnmsEventHandlingAuditCommand extends AbstractCommand {

    public static final String NAME = "cpn-opennms-event-handling-audit";

    @Option(name="--node-a-label", usage="Node label for A", required=true)
    private String aNodeLabel;

    @Option(name="--node-a-id", usage="Node ID for A (that matched the node label)", required=true)
    private Integer aNodeId;

    @Option(name="--node-a-ifindex", usage="A valid ifIndex on node A", required=true)
    private Integer aIfIndex;

    @Option(name="--node-a-ifdescr", usage="A valid ifDescr (that matches the ifIndex) on node A", required=true)
    private String aIfDescr;

    @Option(name="--node-z-label", usage="Node label for A", required=true)
    private String zNodeLabel;

    @Option(name="--node-z-id", usage="Node ID for Z (that matched the node label)", required=true)
    private Integer zNodeId;

    @Option(name="--node-z-ifindex", usage="A valid ifIndex on node Z", required=true)
    private Integer zIfIndex;

    @Option(name="--node-z-ifdescr", usage="A valid ifDescr (that matches the ifIndex) on node Z", required=true)
    private String zIfDescr;

    @Option(name="--opennms-host", usage="OpenNMS hostname")
    private String opennmsHost = "localhost";

    public CpnOnmsEventHandlingAuditCommand() {
        super(NAME);
    }

    @Override
    public void doExec(Context context) throws Exception {
        final MockNetwork network = MockNetwork.builder()
                .withNodeA(aNodeLabel, aNodeId)
                .withInterfaceOnNodeA(aIfIndex, aIfDescr, "")
                .withNodeZ(zNodeLabel, zNodeId)
                .withInterfaceOnNodeZ(zIfIndex, zIfDescr, "")
                .build();
        final OpennmsRestClient client = new OpennmsRestClient(String.format("http://%s:8980/opennms", opennmsHost),
                "admin", "admin");
        AlarmHandlingAudit audit = new AlarmHandlingAudit(client, InetAddress.getByName(opennmsHost), network);
        audit.verifyNodes();
        audit.verifyAlarms();
    }
}
