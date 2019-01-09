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

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.opennms.oce.tools.cpn.model.EventRecord;
import org.opennms.oce.tools.onms.client.ESEventDTO;

import com.google.common.collect.Sets;

public class CanonicalSituation {
    private final String sourceId;
    private final Set<Integer> onmsEventIds;
    private final Set<String> unmatchedCpnEvents;

    public CanonicalSituation(TicketAndEvents ticketAndEvents, Map<String,Integer> cpnEventIdToOnmsEventIdMap) {
        this.sourceId = ticketAndEvents.getTicket().getTicketId();
        onmsEventIds = new HashSet<>();
        unmatchedCpnEvents = new HashSet<>();
        for (EventRecord e : ticketAndEvents.getEvents()) {
            final Integer onmsEventId = cpnEventIdToOnmsEventIdMap.get(e.getEventId());
            if (onmsEventId == null) {
                unmatchedCpnEvents.add(e.getEventId());
            } else {
                onmsEventIds.add(onmsEventId);
            }
        }
    }

    public CanonicalSituation(SituationAndEvents situationAndEvents) {
        this.sourceId = situationAndEvents.getReductionKey();
        this.onmsEventIds = situationAndEvents.getEventsInSituation().stream()
                .map(ESEventDTO::getId)
                .collect(Collectors.toSet());
        unmatchedCpnEvents = Collections.emptySet();
    }

    public String getSourceId() {
        return sourceId;
    }

    public boolean contains(CanonicalSituation other) {
        // never contain if there are unmatched events
        if (!this.unmatchedCpnEvents.isEmpty() || !other.unmatchedCpnEvents.isEmpty()) {
            return false;
        }
        return Sets.difference(other.onmsEventIds, this.onmsEventIds).isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CanonicalSituation that = (CanonicalSituation) o;
        return Objects.equals(onmsEventIds, that.onmsEventIds) &&
                Objects.equals(unmatchedCpnEvents, that.unmatchedCpnEvents);
    }

    @Override
    public int hashCode() {
        return Objects.hash(onmsEventIds, unmatchedCpnEvents);
    }

    @Override
    public String toString() {
        return "CanonicalSituation{" +
                "sourceId='" + sourceId + '\'' +
                ", onmsEventIds=" + onmsEventIds +
                ", unmatchedCpnEvents=" + unmatchedCpnEvents +
                '}';
    }


}
