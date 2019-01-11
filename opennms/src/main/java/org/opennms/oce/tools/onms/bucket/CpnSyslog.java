package org.opennms.oce.tools.onms.bucket;

import java.util.Date;
import java.util.List;
import org.opennms.netmgt.syslogd.SyslogMessage;
import org.opennms.oce.tools.cpn.model.EventRecord;
import org.opennms.oce.tools.tsaudit.SyslogParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CpnSyslog {

    private static final Logger LOG = LoggerFactory.getLogger(CpnSyslog.class);

    private final String detailedDescription;

    private String message;

    private Date date;

    public CpnSyslog(EventRecord r) {
        detailedDescription = r.getDetailedDescription();
        SyslogMessage syslogMessage;
        try {
            syslogMessage = SyslogParser.parse(detailedDescription);
            message = syslogMessage.getMessage();
            date = syslogMessage.getDate();
        } catch (Exception e) {
            LOG.warn("Error while processsing CPN Syslog {} : {} ", detailedDescription, e.getMessage());
        }
    }

    public boolean matches(OnmsSyslog s) {
        LOG.trace("CPN : {} | {}.", date, message);
        LOG.trace("ONMS: {} | {}.", s.getDate(), s.getMessage());
        boolean msgMatched = message != null && message.equals(s.getMessage());
        boolean dateMatched = date != null && date.equals(s.getDate());
        if (msgMatched && dateMatched) {
            LOG.info("Matched: \n CPN: {}\n ONMS: {}", detailedDescription, s);
        }
        return msgMatched && dateMatched;
    }

    public boolean matchesAny(List<OnmsSyslog> syslogs) {
        return syslogs.stream().anyMatch(s -> matches(s));
    }

    public String getMessage() {
        return message;
    }

    public Date getDate() {
        return date;
    }

    @Override
    public String toString() {
        return "Syslog[" + date + "|" + message + "]";
    }

}
