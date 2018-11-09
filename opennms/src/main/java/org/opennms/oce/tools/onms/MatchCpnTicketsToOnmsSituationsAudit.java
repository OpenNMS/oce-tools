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

package org.opennms.oce.tools.onms;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.util.Strings;
import org.opennms.oce.tools.cpn.ESDataProvider;
import org.opennms.oce.tools.cpn.model.EventRecord;
import org.opennms.oce.tools.cpn.model.TicketRecord;
import org.opennms.oce.tools.cpn.model.TrapRecord;
import org.opennms.oce.tools.onms.alarmdto.AlarmDocumentDTO;
import org.opennms.oce.tools.onms.client.ESEventDTO;
import org.opennms.oce.tools.onms.match.CpnToOnmsEventMatcher;

import com.google.common.collect.Sets;

public class MatchCpnTicketsToOnmsSituationsAudit {

    private final CpnToOnmsEventMatcher cpnToOnmsEventMatcher;

    private final ZonedDateTime start;
    private final ZonedDateTime end;
    private final String ticketId;
    private final ESDataProvider esDataProvider;

    public static class Builder {
        private ZonedDateTime start;
        private ZonedDateTime end;
        private String ticketId;
        private ESDataProvider esDataProvider;

        public Builder withStart(ZonedDateTime start) {
            this.start = start;
            return this;
        }

        public Builder withEnd(ZonedDateTime end) {
            this.end = end;
            return this;
        }

        public Builder withTicketId(String ticketId) {
            this.ticketId = ticketId;
            return this;
        }

        public Builder withDataProvider(ESDataProvider esDataProvider) {
            this.esDataProvider = esDataProvider;
            return this;
        }

        public MatchCpnTicketsToOnmsSituationsAudit build() {
            if (!(this.ticketId != null || (start != null && end != null))) {
                throw new IllegalArgumentException("Either a ticket id or a range is required.");
            }
            Objects.requireNonNull(esDataProvider, "data provider is required");
            return new MatchCpnTicketsToOnmsSituationsAudit(this);
        }
    }

    public MatchCpnTicketsToOnmsSituationsAudit(Builder builder) {
        this.esDataProvider = builder.esDataProvider;
        this.start = builder.start;
        this.end = builder.end;
        this.ticketId = builder.ticketId;
        this.cpnToOnmsEventMatcher = new CpnToOnmsEventMatcher(esDataProvider.getEsClient().getJestClient());
    }

    public void audit() throws IOException {
        final AuditSummary auditSummary;
        if (ticketId != null) {
            auditSummary = auditTicket(Integer.parseInt(ticketId));
        } else {
            auditSummary = auditRange();
            auditSummary.getTicketsMatched().removeIf(t -> t.getCpnAlarmIds().size() < 2);
            auditSummary.getTicketsNotMatched().removeIf(t -> t.getCpnAlarmIds().size() < 2);
        }
        System.out.println();
        System.out.println("Tickets matched: " + auditSummary.getTicketsMatched().size());
        System.out.println("Tickets not matched: " + auditSummary.getTicketsNotMatched().size());
        System.out.println("Tickets not matched: ");
        for (TicketMatchSummary ticketMatchSummary : auditSummary.getTicketsNotMatched()) {
            System.out.println(ticketMatchSummary);
        }
    }

    private AuditSummary auditRange() throws IOException {
        AuditSummary auditSummary = new AuditSummary();
        esDataProvider.getTicketRecordsInRange(start, end, (tickets) -> {
            for (TicketRecord ticket : tickets) {
                TicketMatchSummary summary = null;
                try {
                    summary = auditTicket(ticket);
                    if (summary.isDidMatchTicketToSituation()) {
                        System.out.println("\n\n\nMATCH TO TICKET: " + ticket.getTicketId() + " - " + summary);
                        auditSummary.getTicketsMatched().add(summary);
                    } else {
                        System.out.println("\n\n\nMISMATCH TO TICKET: " + ticket.getTicketId() + " - " + summary);
                        auditSummary.getTicketsNotMatched().add(summary);
                    }
                } catch (Exception e) {
                    System.out.println("ERROR AUDITING TICKET: " + ticket.getTicketId());
                    e.printStackTrace();
                }
            }
        });
        return auditSummary;
    }

    private AuditSummary auditTicket(int ticketId) throws IOException {
        AuditSummary auditSummary = new AuditSummary();
        TicketRecord ticket = esDataProvider.getTicketRecord(ticketId);
        TicketMatchSummary summary =  auditTicket(ticket);
        if (summary.isDidMatchTicketToSituation()) {
            System.out.println("\n\n\nMATCH TO TICKET: " + ticket.getTicketId() + " - " + summary);
            auditSummary.getTicketsMatched().add(summary);
        } else {
            System.out.println("\n\n\nMISMATCH TO TICKET: " + ticket.getTicketId() + " - " + summary);
            auditSummary.getTicketsNotMatched().add(summary);
        }
        return auditSummary;
    }

    private void filterSyslogs(List<EventRecord> syslogs) {
        syslogs.removeIf(s -> {
            if (s.getDetailedDescription().contains("Cleared due to ")) {
                //System.out.println("Skipping clear.");
                return true;
            }
            return false;
        });
    }

    private void filterTraps(List<TrapRecord> traps) {
        traps.removeIf(t -> {
            if (t.getTrapTypeOid() == null || "N/A".equals(t.getTrapTypeOid())) {
                //System.out.println("Skipping trap without type oid");
                return true;
            }
            if (".1.3.6.1.6.3.1.1.5.5".equals(t.getTrapTypeOid())) {
                System.out.println("Skipping authenticationFailure.");
                return true;
            }
            return false;
        });
    }

    private TicketMatchSummary auditTicket(TicketRecord ticket) throws IOException {
        List<TrapRecord> traps = esDataProvider.getTrapsInTicket(ticket.getTicketId());
        filterTraps(traps);

        List<EventRecord> syslogs = esDataProvider.getSyslogsInTicket(ticket.getTicketId());
        filterSyslogs(syslogs);

        List<EventRecord> cpn_events = new ArrayList<>();
        cpn_events.addAll(traps);
        cpn_events.addAll(syslogs);
        Map<String, ESEventDTO> cpnEventIdToOnmsEvents = new LinkedHashMap<>();

        System.out.printf("Found ticket #%s - %s with %d traps and %d syslogs.\n",
                ticket.getTicketId(),
                ticket.getDescription(),
                traps.size(), syslogs.size());

        boolean didMatchAllEvents = true;
        for (TrapRecord trap : traps) {
            final Optional<ESEventDTO> event = cpnToOnmsEventMatcher.matchCpnTrapToOnmsTrap(trap);
            if (!event.isPresent()) {
                didMatchAllEvents = false;
                break;
            } else {
                cpnEventIdToOnmsEvents.put(trap.getEventId(), event.get());
            }
        }
        for (EventRecord syslog : syslogs) {
            final Optional<ESEventDTO> event = cpnToOnmsEventMatcher.matchCpnSyslogToOnmsSyslog(syslog);
            if (!event.isPresent()) {
                didMatchAllEvents = false;
                break;
            }  else {
                cpnEventIdToOnmsEvents.put(syslog.getEventId(), event.get());
            }
        }

        if (didMatchAllEvents) {
            System.out.println("MATCHED!");
        } else {
            System.out.println("NOT MATCHED!");
        }

        // Group the events by alarm id
        final Map<String, List<EventRecord>> cpnEventsByAlarmId = cpn_events.stream()
                .sorted(Comparator.comparing(EventRecord::getTime))
                .filter(e -> {
                    if (Strings.isBlank(e.getAlarmId())) {
                        // No alarm id!
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.groupingBy(EventRecord::getAlarmId));
        System.out.println("CPN Alarm IDs: " + cpnEventsByAlarmId.keySet());

        // Now, grab the first event for every alarm, and attempt to find the corresponding alarm in OpenNMS
        Map<String,AlarmDocumentDTO> cpnAlarmIdToOnmsAlarm = new LinkedHashMap<>();
        for (String cpnAlarmId : cpnEventsByAlarmId.keySet()) {

            final EventRecord firstCpnEvent = cpnEventsByAlarmId.get(cpnAlarmId).get(0);
            final ESEventDTO matchingOnmsEvent = cpnEventIdToOnmsEvents.get(firstCpnEvent.getEventId());
            final Optional<AlarmDocumentDTO> alarm;
            if (matchingOnmsEvent == null) {
                alarm = Optional.empty();
                System.out.printf("NO MATCH FOR ALARM WITH CPN ID: %s - no matching event\n", cpnAlarmId);
            } else {
                alarm = cpnToOnmsEventMatcher.getAlarmForEvent(matchingOnmsEvent);
                if (!alarm.isPresent()) {
                    System.out.printf("NO MATCH FOR ALARM WITH CPN ID: %s and ONMS event with id: %d\n", cpnAlarmId, matchingOnmsEvent.getId());
                } else {
                    System.out.printf("CPN alarm id: %s maps to ONMS alarm id: %d\n", cpnAlarmId, alarm.get().getId());
                    cpnAlarmIdToOnmsAlarm.put(cpnAlarmId, alarm.get());
                }
            }

        }

        Map<Integer,AlarmDocumentDTO> onmsAlarmIdToSituation = new LinkedHashMap<>();
        for (AlarmDocumentDTO onmsAlarm : cpnAlarmIdToOnmsAlarm.values()) {
            if (onmsAlarmIdToSituation.containsKey(onmsAlarm.getId())) {
                continue;
            }

            final Optional<AlarmDocumentDTO> situation = cpnToOnmsEventMatcher.getSituationForAlarm(onmsAlarm);
            if (!situation.isPresent()) {
                System.out.printf("NO SITUATION FOUND FOR ONMS ALARM WITH ID: %d\n", onmsAlarm.getId());
            } else {
                onmsAlarmIdToSituation.put(onmsAlarm.getId(), situation.get());
            }
        }

        final TicketMatchSummary summary = new TicketMatchSummary();
        summary.setCpnTicketId(ticket.getTicketId());
        summary.getCpnAlarmIds().addAll(cpnEventsByAlarmId.keySet());
        summary.getOnmsAlarmIds().addAll(cpnAlarmIdToOnmsAlarm.values().stream()
                .map(AlarmDocumentDTO::getId).collect(Collectors.toList()));
        summary.getOnmsSituationIds().addAll(onmsAlarmIdToSituation.values().stream()
                .map(AlarmDocumentDTO::getId).collect(Collectors.toList()));

        summary.setDidFindAllAlarms(summary.getCpnAlarmIds().size() == summary.getOnmsAlarmIds().size());

        boolean isMatch = summary.didFindAllAlarms && summary.getOnmsSituationIds().size() == 1;
        if (isMatch) {
            AlarmDocumentDTO theSituation = onmsAlarmIdToSituation.values().stream().findFirst().get();
            Set<Integer> allRelatedAlarmIds =  new HashSet<>(theSituation.getRelatedAlarmIds());
            isMatch = Sets.difference(allRelatedAlarmIds, summary.getOnmsAlarmIds()).size() == 0;
        }
        summary.setDidMatchTicketToSituation(isMatch);
        System.out.printf("Did match: %s\n", isMatch);

        return summary;
    }

    private static class AuditSummary {
        private List<TicketMatchSummary> ticketsMatched = new LinkedList<>();
        private List<TicketMatchSummary> ticketsNotMatched = new LinkedList<>();

        public List<TicketMatchSummary> getTicketsMatched() {
            return ticketsMatched;
        }

        public void setTicketsMatched(List<TicketMatchSummary> ticketsMatched) {
            this.ticketsMatched = ticketsMatched;
        }

        public List<TicketMatchSummary> getTicketsNotMatched() {
            return ticketsNotMatched;
        }

        public void setTicketsNotMatched(List<TicketMatchSummary> ticketsNotMatched) {
            this.ticketsNotMatched = ticketsNotMatched;
        }

        @Override
        public String toString() {
            return "AuditSummary{" +
                    "ticketsMatched=" + ticketsMatched +
                    ", ticketsNotMatched=" + ticketsNotMatched +
                    '}';
        }
    }

    private static class TicketMatchSummary {
        private String cpnTicketId;
        private Set<String> cpnAlarmIds = new LinkedHashSet<>();
        private Set<Integer> onmsAlarmIds = new LinkedHashSet<>();
        private Set<Integer> onmsSituationIds = new LinkedHashSet<>();

        boolean didFindAllAlarms = false;
        boolean didMatchTicketToSituation = false;

        public String getCpnTicketId() {
            return cpnTicketId;
        }

        public void setCpnTicketId(String cpnTicketId) {
            this.cpnTicketId = cpnTicketId;
        }

        public Set<String> getCpnAlarmIds() {
            return cpnAlarmIds;
        }

        public void setCpnAlarmIds(Set<String> cpnAlarmIds) {
            this.cpnAlarmIds = cpnAlarmIds;
        }

        public Set<Integer> getOnmsAlarmIds() {
            return onmsAlarmIds;
        }

        public void setOnmsAlarmIds(Set<Integer> onmsAlarmIds) {
            this.onmsAlarmIds = onmsAlarmIds;
        }

        public Set<Integer> getOnmsSituationIds() {
            return onmsSituationIds;
        }

        public void setOnmsSituationIds(Set<Integer> onmsSituationIds) {
            this.onmsSituationIds = onmsSituationIds;
        }

        public boolean isDidFindAllAlarms() {
            return didFindAllAlarms;
        }

        public void setDidFindAllAlarms(boolean didFindAllAlarms) {
            this.didFindAllAlarms = didFindAllAlarms;
        }

        public boolean isDidMatchTicketToSituation() {
            return didMatchTicketToSituation;
        }

        public void setDidMatchTicketToSituation(boolean didMatchTicketToSituation) {
            this.didMatchTicketToSituation = didMatchTicketToSituation;
        }

        @Override
        public String toString() {
            return "TicketMatchSummary{" +
                    "cpnTicketId=" + cpnTicketId +
                    ", cpnAlarmIds=" + cpnAlarmIds +
                    ", onmsAlarmIds=" + onmsAlarmIds +
                    ", onmsSituationIds=" + onmsSituationIds +
                    ", didFindAllAlarms=" + didFindAllAlarms +
                    ", didMatchTicketToSituation=" + didMatchTicketToSituation +
                    '}';
        }
    }

}
