package org.opennms.oce.tools.onms.bucket;

import java.util.List;

import org.opennms.oce.tools.cpn.model.EventRecord;

public class CpnSyslog {

    private String detailedDescription;

    private long time;

    public CpnSyslog(EventRecord r) {
        detailedDescription = r.getDetailedDescription();
        time = r.getTime().getTime();

    }
    public boolean matches(OnmsSyslog s) {
        // FIXME - from matching code
        return false;
    }

    public boolean matchesAny(List<OnmsSyslog> syslogs) {
        return syslogs.stream().anyMatch(s -> matches(s));
    }

    @Override
    public String toString() {
        return "Syslog[" + time + "|" + detailedDescription + "]";
    }
}
