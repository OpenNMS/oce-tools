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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.opennms.oce.tools.cpn.model.EventRecord;
import org.opennms.oce.tools.cpn.model.TrapRecord;
import org.opennms.oce.tools.onms.client.ESEventDTO;

public class NodeAndEvents {
    final NodeAndFacts nodeAndFacts;
    final List<EventRecord> cpnSyslogEvents;
    final List<ESEventDTO> onmsSyslogEvents;
    final Map<String, Integer> matchedSyslogs;

    final List<TrapRecord> cpnTrapEvents;
    final List<ESEventDTO> onmsTrapEvents;
    final Map<String, Integer> matchedTraps;

    public NodeAndEvents(NodeAndFacts nodeAndFacts,
                         List<EventRecord> cpnSyslogEvents, List<ESEventDTO> onmsSyslogEvents, Map<String, Integer> matchedSyslogs,
                         List<TrapRecord> cpnTrapEvents, List<ESEventDTO> onmsTrapEvents, Map<String, Integer> matchedTraps) {

        this.nodeAndFacts = nodeAndFacts;
        this.cpnSyslogEvents = Objects.requireNonNull(cpnSyslogEvents);
        this.onmsSyslogEvents = Objects.requireNonNull(onmsSyslogEvents);
        this.matchedSyslogs = Objects.requireNonNull(matchedSyslogs);

        this.cpnTrapEvents = Objects.requireNonNull(cpnTrapEvents);
        this.onmsTrapEvents = Objects.requireNonNull(onmsTrapEvents);
        this.matchedTraps = Objects.requireNonNull(matchedTraps);
    }

    public NodeAndFacts getNodeAndFacts() {
        return nodeAndFacts;
    }

    public List<EventRecord> getCpnSyslogEvents() {
        return cpnSyslogEvents;
    }

    public List<ESEventDTO> getOnmsSyslogEvents() {
        return onmsSyslogEvents;
    }

    public Map<String, Integer> getMatchedSyslogs() {
        return matchedSyslogs;
    }

    public List<TrapRecord> getCpnTrapEvents() {
        return cpnTrapEvents;
    }

    public List<ESEventDTO> getOnmsTrapEvents() {
        return onmsTrapEvents;
    }

    public Map<String, Integer> getMatchedTraps() {
        return matchedTraps;
    }

    public List<EventRecord> getCpnEvents() {
        final List<EventRecord> allEvents = new LinkedList<>();
        allEvents.addAll(cpnSyslogEvents);
        allEvents.addAll(cpnTrapEvents);
        return allEvents;
    }

    public List<ESEventDTO> getOnmsEvents() {
        final List<ESEventDTO> allEvents = new LinkedList<>();
        allEvents.addAll(onmsSyslogEvents);
        allEvents.addAll(onmsTrapEvents);
        return allEvents;
    }

    public Map<String, Integer> getMatchedEvents() {
        final Map<String, Integer> matchedEvents = new HashMap<>();
        matchedEvents.putAll(matchedSyslogs);
        matchedEvents.putAll(matchedTraps);
        return matchedEvents;
    }
}
