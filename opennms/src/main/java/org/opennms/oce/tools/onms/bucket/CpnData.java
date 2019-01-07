package org.opennms.oce.tools.onms.bucket;

import java.util.Set;

public class CpnData {

    Set<Node> nodes;

    public CpnData(Set<Node> nodes, Set<Ticket> tickets, Set<CpnSyslog> syslogs, Set<CpnTrap> traps) {
        super();
        this.nodes = nodes;
        // TODO - set data on the Nodes
    }

    public Set<Node> getNodes() {
        return nodes;
    }

}
