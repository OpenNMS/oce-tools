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

package org.opennms.oce.tools.onms.match;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opennms.features.es.alarms.dto.AlarmDocumentDTO;
import org.opennms.oce.tools.cpn.model.EventRecord;
import org.opennms.oce.tools.cpn.model.TrapRecord;
import org.opennms.oce.tools.onms.client.ESEventDTO;
import org.opennms.oce.tools.onms.client.EventClient;

import io.searchbox.client.JestClient;

public class CpnToOnmsEventMatcher {

    private final EventClient eventClient;

    public CpnToOnmsEventMatcher(JestClient client) {
        this.eventClient = new EventClient(client);
    }

    public Optional<ESEventDTO> matchCpnSyslogToOnmsSyslog(EventRecord syslog) throws IOException {
        final String hostname = getHostnameFromLocation(syslog.getLocation());
        final Pattern p = Pattern.compile(".*?%.*?\\s*:\\s*(.*)$");
        final Matcher m = p.matcher(syslog.getDetailedDescription());
        if (!m.matches()) {
            throw new IllegalStateException("ABC: " + syslog.getDetailedDescription());
        }
        final String substringToMatch = m.group(1);

        System.out.printf("Trying to match syslog at %s (%d) for: '%s' (hostname='%s') with substring: %s\n",
                syslog.getTime(), syslog.getTime().getTime(),
                syslog.getLocation(),
                hostname,
                substringToMatch);
        Optional<ESEventDTO> event =  eventClient.findBestSyslogMessageMatching(syslog.getTime().getTime(), hostname, substringToMatch);
        if (event.isPresent()) {
            System.out.printf("SUCCESS: Found match: %s\n", event.get());
        } else {
            System.out.println("FAILED");
        }
        return event;
    }

    public Optional<ESEventDTO> matchCpnTrapToOnmsTrap(TrapRecord trap) throws IOException {
        final String hostname = getHostnameFromLocation(trap.getLocation());
        System.out.printf("Trying to match trap at %s (%d) for: '%s' (hostname='%s') with trap type OID: %s\n",
                trap.getTime(), trap.getTime().getTime(),
                trap.getLocation(),
                hostname,
                trap.getTrapTypeOid());
        Optional<ESEventDTO> event =  eventClient.findBestTrapMatching(trap.getTime().getTime(), hostname, trap.getTrapTypeOid());
        if (event.isPresent()) {
            System.out.printf("SUCCESS: Found match: %s\n", event.get());
        } else {
            System.out.println("FAILED");
        }
        return event;
    }

    public Optional<AlarmDocumentDTO> getAlarmForEvent(ESEventDTO event) {
        System.out.printf("Trying to match alarm for event with id: %d\n", event.getId());
        Optional<AlarmDocumentDTO> alarm =  eventClient.findAlarmForEventWithId(event.getId());
        if (alarm.isPresent()) {
            AlarmDocumentDTO a = alarm.get();
            System.out.printf("SUCCESS: Found match (id=%d): %s\n",a.getId(),a);
        } else {
            System.out.println("FAILED");
        }
        return alarm;
    }

    public Optional<AlarmDocumentDTO> getSituationForAlarm(AlarmDocumentDTO alarm) {
        System.out.printf("Trying to find situation for alarm with id: %d\n", alarm.getId());
        Optional<AlarmDocumentDTO> situation =  eventClient.findSituationForAlarmWithId(alarm.getId());
        if (situation.isPresent()) {
            AlarmDocumentDTO s = situation.get();
            System.out.printf("SUCCESS: Found match (id=%d): %s\n",s.getId(),s);
        } else {
            System.out.println("FAILED");
        }
        return situation;
    }

    public static String getHostnameFromLocation(String location) {
        Pattern p = Pattern.compile("^(.*?)(:.*)?$");
        Matcher m = p.matcher(location);
        if (m.matches()) {
            return m.group(1).toLowerCase();
        }
        throw new IllegalArgumentException("Failed to parse: " + location);
    }

}
