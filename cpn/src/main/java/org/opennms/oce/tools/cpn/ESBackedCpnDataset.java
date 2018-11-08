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

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import org.opennms.oce.tools.cpn.model.EventRecord;
import org.opennms.oce.tools.cpn.model.TicketRecord;
import org.opennms.oce.tools.cpn.model.TrapRecord;

public class ESBackedCpnDataset implements CpnDataset {

    private final ESDataProvider esDataProvider;
    private final ZonedDateTime start;
    private final ZonedDateTime end;

    public ESBackedCpnDataset(ESDataProvider esDataProvider, ZonedDateTime start, ZonedDateTime end) {
        this.esDataProvider = Objects.requireNonNull(esDataProvider);
        this.start = Objects.requireNonNull(start);
        this.end = Objects.requireNonNull(end);
    }

    @Override
    public List<TicketRecord> getTickets() {
        final List<TicketRecord> tickets = new LinkedList<>();
        try {
            esDataProvider.getTicketRecordsInRange(start, end, tickets::addAll);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return tickets;
    }

    @Override
    public List<EventRecord> getServiceEvents() {
        final List<EventRecord> events = new LinkedList<>();
        try {
            esDataProvider.getServiceEventsInRange(start, end, events::addAll);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return events;
    }

    @Override
    public List<EventRecord> getSyslogEvents() {
        final List<EventRecord> syslogs = new LinkedList<>();
        try {
            esDataProvider.getSyslogRecordsInRange(start, end, syslogs::addAll);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return syslogs;
    }

    @Override
    public List<TrapRecord> getTraps() {
        final List<TrapRecord> traps = new LinkedList<>();
        try {
            esDataProvider.getTrapRecordsInRange(start, end, traps::addAll);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return traps;
    }

    @Override
    public Collection<EventRecord> getEvents() {
        final List<EventRecord> events = new LinkedList<>();
        events.addAll(getServiceEvents());
        events.addAll(getSyslogEvents());
        events.addAll(getTraps());
        return events;
    }

    @Override
    public List<EventRecord> getEventsByTicketId(String ticketId) {
        try {
            return esDataProvider.getEventsByTicketId(ticketId);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public EventRecord getEventById(String id) {
        throw new UnsupportedOperationException("TODO");
    }

}
