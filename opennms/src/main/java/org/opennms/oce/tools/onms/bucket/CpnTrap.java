package org.opennms.oce.tools.onms.bucket;

import java.util.List;

import org.opennms.oce.tools.cpn.model.TrapRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CpnTrap {

    private static final Logger LOG = LoggerFactory.getLogger(CpnTrap.class);

    private String trapTypeOid;

    private String location;

    private long time;

    public CpnTrap(TrapRecord r) {
        trapTypeOid = r.getTrapTypeOid();
        location = r.getLocation();
        time = r.getTime().getTime();
    }

    public boolean matches(OnmsTrap t) {
        if (trapTypeOid.equals(t.getTrapType())) {
            LOG.debug("Trap Type Match: {} - {}", trapTypeOid, t.getTrapType());
        }
        // FIXME from matching code
        return true;
    }

    public boolean matchesAny(List<OnmsTrap> traps) {
        return traps.stream().anyMatch(t -> matches(t));
    }

    @Override
    public String toString() {
        return "trap[" + time + "|" + location + "|" + trapTypeOid + "]";
    }
}
