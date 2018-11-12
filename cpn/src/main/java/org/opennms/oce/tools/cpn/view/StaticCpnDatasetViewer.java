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

package org.opennms.oce.tools.cpn.view;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.opennms.oce.tools.cpn.model.EventRecord;
import org.opennms.oce.tools.cpn.model.TicketRecord;
import org.opennms.oce.tools.cpn.model.TrapRecord;

public class StaticCpnDatasetViewer implements CpnDatasetViewer {

    private final CpnDatasetView view;
    private final List<TicketRecord> tickets;
    private final List<EventRecord> serviceEvents;
    private final List<EventRecord> syslogEvents;
    private final List<TrapRecord> traps;

    public StaticCpnDatasetViewer(CpnDatasetView view, List<TicketRecord> tickets, List<EventRecord> serviceEvents, List<EventRecord> syslogEvents, List<TrapRecord> traps) {
        this.view = Objects.requireNonNull(view);
        this.tickets = Objects.requireNonNull(tickets);
        this.serviceEvents = Objects.requireNonNull(serviceEvents);
        this.syslogEvents = Objects.requireNonNull(syslogEvents);
        this.traps = Objects.requireNonNull(traps);
    }

    @Override
    public TicketRecord getTicketWithId(String ticketId) {
        return tickets.stream().filter(t -> Objects.equals(ticketId, t.getTicketId())).findFirst().orElse(null);
    }

    @Override
    public void getTicketRecordsWithRootEventTimeInRange(Consumer<List<TicketRecord>> callback) {
        // TODO: Filtering!
        callback.accept(tickets);
    }

    @Override
    public void getEventsInTicket(TicketRecord ticket, Consumer<List<EventRecord>> callback) {
        getEventsInTicket(ticket.getTicketId(), callback);
    }

    @Override
    public void getEventsInTicket(String ticketId, Consumer<List<EventRecord>> callback) {
        getFilteredEvents().stream()
                .filter(e -> Objects.equals(ticketId, e.getTicketId()))
                .forEach(e -> callback.accept(Collections.singletonList(e)));
    }

    private List<EventRecord> getFilteredEvents() {
        List<EventRecord> events = new LinkedList<>();
        if (view.getEventTypes().contains(CpnDatasetView.EventType.SERVICE)) {
            events.addAll(serviceEvents);
        }
        if (view.getEventTypes().contains(CpnDatasetView.EventType.SYSLOG)) {
            events.addAll(syslogEvents);
        }
        if (view.getEventTypes().contains(CpnDatasetView.EventType.TRAP)) {
            events.addAll(traps);
        }
        return events;
    }
}
