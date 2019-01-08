/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2019 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2019 The OpenNMS Group, Inc.
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

package org.opennms.oce.tools.tsaudit;

import java.util.Date;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.opennms.netmgt.syslogd.SyslogMessage;

public class GenericSyslogMessage {
    private final String id;
    private final String host;
    private final String message;
    private final Date date;

    private GenericSyslogMessage(String id, String host, String detailedDescription) throws ExecutionException,
            InterruptedException {
        this.id = id;
        this.host = host;
        SyslogMessage syslogMessage = SyslogParser.parse(detailedDescription);
        this.message = Objects.requireNonNull(syslogMessage.getMessage());
        this.date = Objects.requireNonNull(syslogMessage.getDate());
    }

    private GenericSyslogMessage(String id, String nodeLabel, String message, Date date) {
        this.id = id;
        this.host = nodeLabel;
        this.message = message;
        this.date = date;
    }

    public static GenericSyslogMessage fromCpn(String id, String host, String detailedDescription) throws ExecutionException,
            InterruptedException {
        return new GenericSyslogMessage(Objects.requireNonNull(id), Objects.requireNonNull(host),
                Objects.requireNonNull(detailedDescription));
    }

    public static GenericSyslogMessage fromOnms(Integer id, String host, String message, Date date) {
        // Assumes the host passed in was already converted from a node label if applicable
        return new GenericSyslogMessage(Objects.requireNonNull(id).toString(), Objects.requireNonNull(host),
                Objects.requireNonNull(message), Objects.requireNonNull(date));
    }

    public boolean anyMatch(Set<GenericSyslogMessage> syslogs) {
        return syslogs.stream().anyMatch(syslog -> syslog.equals(this));
    }

    public String getId() {
        return id;
    }

    public String getHost() {
        return host;
    }

    public String getMessage() {
        return message;
    }

    public Date getDate() {
        return date;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GenericSyslogMessage that = (GenericSyslogMessage) o;
        return Objects.equals(host, that.host) &&
                Objects.equals(message, that.message) &&
                Objects.equals(date, that.date);
    }

    public boolean equalsIgnoringHost(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GenericSyslogMessage that = (GenericSyslogMessage) o;
        return Objects.equals(message, that.message) &&
                Objects.equals(date, that.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, message, date);
    }

    @Override
    public String toString() {
        return "GenericSyslogMessage{" +
                "id='" + id + '\'' +
                ", host='" + host + '\'' +
                ", message='" + message + '\'' +
                ", date=" + date +
                '}';
    }
}
