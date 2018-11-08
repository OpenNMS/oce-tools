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

package org.opennms.oce.tools.cpn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.opennms.oce.tools.cpn.model.EventRecord;
import org.opennms.oce.tools.cpn.model.TicketRecord;
import org.opennms.oce.tools.cpn.model.TrapRecord;

/**
 * Container for the dataset obtained from one or more .csv files.
 *
 * Used to help aggregate and index the data.
 */
public class CsvBackedCpnDataset implements CpnDataset {
    private final Map<String, TicketRecord> ticketsById = new LinkedHashMap<>();
    private final Map<String, EventRecord> eventsById = new LinkedHashMap<>();
    private final Map<String, List<EventRecord>> eventsByTicketId = new LinkedHashMap<>();

    private final List<TicketRecord> tickets;
    private final List<EventRecord> serviceEvents;
    private final List<EventRecord> syslogEvents;
    private final List<TrapRecord> traps;

    public CsvBackedCpnDataset(List<TicketRecord> tickets, List<EventRecord> serviceEvents, List<EventRecord> syslogEvents, List<TrapRecord> traps) {
        this.tickets = Objects.requireNonNull(tickets);
        this.serviceEvents = Objects.requireNonNull(serviceEvents);
        this.syslogEvents = Objects.requireNonNull(syslogEvents);
        this.traps = Objects.requireNonNull(traps);

        tickets.sort(Comparator.comparing(TicketRecord::getCreationTime));
        syslogEvents.sort(Comparator.comparing(EventRecord::getTime));
        serviceEvents.sort(Comparator.comparing(EventRecord::getTime));
        traps.sort(Comparator.comparing(EventRecord::getTime));
        index();
    }

    private void index() {
        ticketsById.clear();
        tickets.forEach(t -> ticketsById.put(t.getTicketId(), t));
        eventsById.clear();
        getEventLists().forEach(l -> l.forEach(e -> eventsById.put(e.getEventId(), e)));
        eventsByTicketId.clear();
        getEventLists().forEach(l -> l.forEach(e -> {
            if (!hasTicket(e)) {
                return;
            }
            eventsByTicketId.computeIfAbsent(e.getTicketId(), k -> new ArrayList<>()).add(e);
        }));
        // Sort the events by time
        eventsByTicketId.values().forEach(e -> e.sort(Comparator.comparing(EventRecord::getTime)));
    }

    private static boolean hasTicket(EventRecord e) {
        return e.getTicketId() != null &&  !"".equalsIgnoreCase(e.getTicketId().trim());
    }

    private List<List<? extends EventRecord>> getEventLists() {
        return Arrays.asList(serviceEvents, syslogEvents, traps);
    }

    public void removeEventsWithoutTicketIds() {
        for (List<? extends EventRecord> l : getEventLists()) {
            l.removeIf(e -> !hasTicket(e));
        }
        index();
    }

    @Override
    public List<TicketRecord> getTickets() {
        return tickets;
    }

    @Override
    public List<EventRecord> getServiceEvents() {
        return serviceEvents;
    }

    @Override
    public List<EventRecord> getSyslogEvents() {
        return syslogEvents;
    }

    @Override
    public List<TrapRecord> getTraps() {
        return traps;
    }

    public void printSummary() {
        System.out.printf("Got %d tickets.\n", tickets.size());
        System.out.printf("Got %d service events.\n", serviceEvents.size());
        System.out.printf("Got %d syslog events.\n", syslogEvents.size());
        System.out.printf("Got %d traps.\n", traps.size());

        System.out.printf("First ticket: %s, Last Ticket: %s\n", tickets.get(0).getCreationTime(), tickets.get(tickets.size()-1).getCreationTime());
        printFirstAndLast(syslogEvents, "syslog");
        printFirstAndLast(serviceEvents, "service");
        printFirstAndLast(traps, "trap");
    }

    private static void printFirstAndLast(List<? extends EventRecord> events, String type) {
        System.out.printf("First %s: %s, Last %s: %s\n", type, events.get(0).getTime(),
                type, events.get(events.size()-1).getTime());
    }

    public void printTicket(String ticketId) {
        TicketRecord ticket = ticketsById.get(ticketId);
        System.out.printf("Ticket %s: %s\n", ticketId, ticket.getDescription());
        for (EventRecord e : eventsByTicketId.getOrDefault(ticketId, Collections.emptyList())) {
            System.out.printf("\tEvent (@%s, from:%s, id=%s): %s\n", e.getTime(), e.getSource(), e.getEventId(), e.getDescription());
        }
    }

    @Override
    public EventRecord getEventById(String id) {
        return eventsById.get(id);
    }

    @Override
    public List<EventRecord> getEventsByTicketId(String ticketId) {
        return eventsByTicketId.getOrDefault(ticketId, Collections.emptyList());
    }

    @Override
    public Collection<EventRecord> getEvents() {
        return eventsById.values();
    }
}
