package org.opennms.oce.tools.onms.bucket;

import java.util.Set;

public class OnmsData {

    Set<Node> nodes;

    Set<OnmsSyslog> syslogs;

    Set<OnmsTrap> traps;

    public Set<Node> getNodes() {
        return nodes;
    }

}
