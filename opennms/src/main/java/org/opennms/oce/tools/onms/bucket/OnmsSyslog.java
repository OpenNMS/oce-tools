package org.opennms.oce.tools.onms.bucket;

import java.util.Date;

import org.opennms.oce.tools.onms.client.ESEventDTO;

public class OnmsSyslog {

    private final Date date;

    private final String message;

    public OnmsSyslog(ESEventDTO dto) {
        date = dto.getTimestamp();
        message = dto.getSyslogMessage();
    }

    public Date getDate() {
        return date;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "Syslog[" + date + "|" + message + "]";
    }
}
