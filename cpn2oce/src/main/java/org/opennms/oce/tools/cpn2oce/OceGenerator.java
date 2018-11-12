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

package org.opennms.oce.tools.cpn2oce;

import java.io.File;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;

import org.apache.logging.log4j.util.Strings;
import org.opennms.oce.datasource.v1.schema.Alarm;
import org.opennms.oce.datasource.v1.schema.AlarmRef;
import org.opennms.oce.datasource.v1.schema.Alarms;
import org.opennms.oce.datasource.v1.schema.Event;
import org.opennms.oce.datasource.v1.schema.Inventory;
import org.opennms.oce.datasource.v1.schema.MetaModel;
import org.opennms.oce.datasource.v1.schema.Severity;
import org.opennms.oce.datasource.v1.schema.Situation;
import org.opennms.oce.datasource.v1.schema.Situations;
import org.opennms.oce.tools.cpn.EventUtils;
import org.opennms.oce.tools.cpn.model.EventRecord;
import org.opennms.oce.tools.cpn.model.EventSeverity;
import org.opennms.oce.tools.cpn.model.TicketRecord;
import org.opennms.oce.tools.cpn.view.CpnDatasetViewer;
import org.opennms.oce.tools.cpn2oce.model.EventDefinition;
import org.opennms.oce.tools.cpn2oce.model.ModelObject;
import org.opennms.oce.tools.cpn2oce.model.ModelObjectKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used to generate data that can be consumed by OCE.
 */
public class OceGenerator  {

    private static final Logger LOG = LoggerFactory.getLogger(OceGenerator.class);

    private final CpnDatasetViewer viewer;
    private final boolean modelGenerationDisabled;
    private final File targetFolder;
    private String ticketId;

    private Situations situations;
    private MetaModel metaModel;
    private Inventory inventory;
    private Alarms alarms;

    public static class Builder {
        private CpnDatasetViewer viewer;
        private boolean modelGenerationDisabled = false;
        private File targetFolder;
        private String ticketId;

        public Builder withViewer(CpnDatasetViewer viewer) {
            this.viewer = viewer;
            return this;
        }

        public Builder withModelGenerationDisabled(boolean modelGenerationDisabled) {
            this.modelGenerationDisabled = modelGenerationDisabled;
            return this;
        }

        public Builder withTargetFolder(File targetFolder) {
            this.targetFolder = targetFolder;
            return this;
        }

        public Builder withTicketId(String ticketId) {
            this.ticketId = ticketId;
            return this;
        }

        public OceGenerator build() {
            Objects.requireNonNull(viewer, "viewer is required");
            return new OceGenerator(this);
        }
    }

    private OceGenerator(Builder builder) {
        this.viewer = builder.viewer;
        this.modelGenerationDisabled = builder.modelGenerationDisabled;
        this.targetFolder = builder.targetFolder;
        this.ticketId = builder.ticketId;
    }

    public void generate() {
        LOG.info("Generating the situations..");
        situations = new Situations();

        final List<TicketRecord> filteredTickets = new LinkedList<>();
        if (ticketId == null) {
            viewer.getTicketRecordsWithRootEventTimeInRange(filteredTickets::addAll);
        } else {
            TicketRecord t = viewer.getTicketWithId(ticketId);
            if (t == null) {
                throw new IllegalStateException("No ticket found with id: " + ticketId);
            }
            filteredTickets.add(t);
        }

        final List<EventRecord> allEventsInTickets = new LinkedList<>();
        for (TicketRecord t : filteredTickets) {
            final Situation situation = new Situation();
            situation.setId(t.getTicketId());
            situation.setCreationTime(t.getCreationTime().getTime());
            situation.setSeverity(toSeverity(t.getSeverity()));
            situation.setSummary(t.getDescription());
            situation.setDescription(t.getDescription());

            final List<EventRecord> eventsInTicket = new LinkedList<>();
            viewer.getEventsInTicket(t, events -> {
                for (EventRecord e : events) {
                    if (Strings.isBlank(e.getAlarmId())) {
                        // No alarm id!
                        LOG.warn("Got event in ticket without an alarm id: {}", e);
                        continue;
                    }
                    if (EventUtils.isClear(e)) {
                        // Ignore clears
                        continue;
                    }
                    final EventDefinition matchingDef = getMachingEvenfDef(e);
                    if (matchingDef == null) {
                        LOG.warn("No matching event definition for: {}. Skipping.", e);
                        continue;
                    }
                    if (matchingDef.isIgnored()) {
                        // Ignore
                        continue;
                    }
                    eventsInTicket.add(e);
                }
            });
            if (eventsInTicket.size() < 1) {
                LOG.info("No events for ticket: {}. Ignoring.", t);
                continue;
            }
            allEventsInTickets.addAll(eventsInTicket);
            situation.getAlarmRef().addAll(getCausalityTree(t, eventsInTicket));
            situations.getSituation().add(situation);
        }

        if (!modelGenerationDisabled) {
            LOG.info("Generating inventory and meta-model...");
            final EventMapper mapper = new EventMapper();
            final ModelGenerator generator = new ModelGenerator(mapper, allEventsInTickets);
            generator.generate();

            metaModel = generator.getMetaModel();
            inventory = generator.getInventory();
        } else {
            metaModel = null;
            inventory = null;
        }

        LOG.info("Generating the list of alarms...");
        alarms = new Alarms();

        // Index the events by alarm id
        final Map<String, List<EventRecord>> eventsByAlarmId = allEventsInTickets.stream()
                .collect(Collectors.groupingBy(EventRecord::getAlarmId));
        // Process each alarm
        eventsByAlarmId.forEach((alarmId, events) -> {
            events.sort(Comparator.comparing(EventRecord::getTime));

            final EventRecord firstEvent = events.get(0);
            final EventRecord lastEvent = events.get(events.size() - 1);

            final Alarm alarm = new Alarm();
            alarm.setId(alarmId);
            alarm.setSummary(lastEvent.getDescription());
            alarm.setDescription(lastEvent.getDetailedDescription());
            alarm.setLastSeverity(toSeverity(lastEvent.getSeverity()));
            alarm.setFirstEventTime(firstEvent.getTime().getTime());
            alarm.setLastEventTime(lastEvent.getTime().getTime());
            alarms.getAlarm().add(alarm);

            for (EventRecord e : events) {
                final Event event = new Event();
                event.setId(e.getEventId());
                event.setSummary(e.getDescription());
                event.setDescription(e.getDetailedDescription());
                event.setSeverity(toSeverity(e.getSeverity()));
                event.setSource(e.getSource());
                event.setTime(e.getTime().getTime());
                alarm.getEvent().add(event);

                if (alarm.getInventoryObjectType() == null && alarm.getInventoryObjectId() == null) {
                    final EventDefinition matchingDef = getMachingEvenfDef(e);
                    if (matchingDef == null) {
                        throw new IllegalStateException("Should not happen!");
                    }
                    final ModelObject modelObject = matchingDef.getModelObjectTree(e);
                    alarm.setInventoryObjectType(modelObject.getType().toString());
                    alarm.setInventoryObjectId(modelObject.getId());
                }
            }
        });
    }

    public void writeResultsToDisk() {
        Objects.requireNonNull(targetFolder, "target folder must be set");
        LOG.info("Marshalling results to disk...");
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(MetaModel.class, Inventory.class, Alarms.class, Situations.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

            if (metaModel != null && inventory != null) {
                LOG.info("Writing {} model object definitions (meta-model)...", metaModel.getModelObjectDef().size());
                marshaller.marshal(metaModel, new File(targetFolder, "cpn.metamodel.xml"));
                LOG.info("Writing {} model object entries (inventory)...", inventory.getModelObjectEntry().size());
                marshaller.marshal(inventory, new File(targetFolder, "cpn.inventory.xml"));
            }
            LOG.info("Writing {} alarms...", alarms.getAlarm().size());
            marshaller.marshal(alarms, new File(targetFolder, "cpn.alarms.xml"));
            LOG.info("Writing {} situations...", situations.getSituation().size());
            marshaller.marshal(situations, new File(targetFolder, "cpn.situations.xml"));
        } catch (Exception e) {
            LOG.error("Oops", e);
        }
        LOG.info("Done.");
    }

    public Situations getSituations() {
        return situations;
    }

    public MetaModel getMetaModel() {
        return metaModel;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public Alarms getAlarms() {
        return alarms;
    }

    private static List<AlarmRef> getCausalityTree(TicketRecord ticket, List<EventRecord> eventsInTicket) {
        final Map<String, List<EventRecord>> eventsInTicketByAlarmId = eventsInTicket.stream()
                .collect(Collectors.groupingBy(EventRecord::getAlarmId));
        return eventsInTicketByAlarmId.keySet().stream()
                .map(alarmId -> {
                    final AlarmRef cause = new AlarmRef();
                    cause.setId(alarmId);
                    return cause;
                })
                .collect(Collectors.toList());
    }

    private static Severity toSeverity(EventSeverity severity) {
        switch(severity) {
            case Critical:
                return Severity.CRITICAL;
            case Major:
                return Severity.MAJOR;
            case Minor:
                return Severity.MINOR;
            case Warning:
                return Severity.WARNING;
            case Information:
                return Severity.NORMAL;
            case Cleared:
                return Severity.CLEARED;
        }
        return Severity.INDETERMINATE;
    }

    private static EventDefinition getMachingEvenfDef(EventRecord e) {
        for (EventDefinition def : EventMapper.EVENT_DEFS) {
            if (def.matches(e)) {
                return def;
            }
        }
        return null;
    }
}
