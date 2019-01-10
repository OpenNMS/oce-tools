package org.opennms.oce.tools.onms.bucket;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.opennms.oce.tools.onms.client.ESEventDTO;

public class OnmsTrap {

    private Date date;

    private String trapType;

    private List<Map<String, String>> oids;

    public OnmsTrap(ESEventDTO dto) {
        date = dto.getTimestamp();
        trapType = dto.getTrapTypeOid().get();
        oids = dto.getP_oids();
    }

    public Date getDate() {
        return date;
    }

    public String getTrapType() {
        return trapType;
    }

    public List<Map<String, String>> getOids() {
        return oids;
    }

    @Override
    public String toString() {
        return "Trap[" + trapType + "|" + date + "]";
    }
}
