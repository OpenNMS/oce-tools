package org.opennms.oce.tools.onms.bucket;

import java.util.Set;

public class Situation {

    private Set<OnmsSyslog> syslogs;

    private Set<OnmsTrap> traps;

    public Set<OnmsSyslog> getSyslogs() {
        return syslogs;
    }

    public void setSyslogs(Set<OnmsSyslog> onmsSyslogs) {
        this.syslogs = onmsSyslogs;
    }

    public Set<OnmsTrap> getTraps() {
        return traps;
    }

    public void setTraps(Set<OnmsTrap> onmsTtraps) {
        this.traps = onmsTtraps;
    }

}
