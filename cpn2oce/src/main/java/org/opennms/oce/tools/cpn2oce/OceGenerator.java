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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
import org.opennms.oce.tools.cpn.CpnDataset;
import org.opennms.oce.tools.cpn.model.EventRecord;
import org.opennms.oce.tools.cpn.model.EventSeverity;
import org.opennms.oce.tools.cpn.model.TicketRecord;
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

    private final CpnDataset dataset;
    private final boolean includeAllSituations;
    private final boolean disableModel;
    private final File targetFolder;

    public static class Builder {
        private CpnDataset dataset;
        private boolean includeAllSituations = false;
        private boolean disableModel = false;
        private File targetFolder;

        public Builder withDataset(CpnDataset dataset) {
            this.dataset = dataset;
            return this;
        }

        public Builder withIncludeAllSituations(boolean includeAllSituations) {
            this.includeAllSituations = includeAllSituations;
            return this;
        }

        public Builder withDisableModel(boolean disableModel) {
            this.disableModel = disableModel;
            return this;
        }

        public Builder withTargetFolder(File targetFolder) {
            this.targetFolder = targetFolder;
            return this;
        }

        public OceGenerator build() {
            Objects.requireNonNull(dataset, "dataset is required");
            Objects.requireNonNull(targetFolder, "target folder is required");
            return new OceGenerator(this);
        }
    }

    private OceGenerator(Builder builder) {
        this.dataset = builder.dataset;
        this.includeAllSituations = builder.includeAllSituations;
        this.disableModel = builder.disableModel;
        this.targetFolder = builder.targetFolder;
    }

    public void generate() {
        final MetaModel metaModel;
        final Inventory inventory;
        if (!disableModel) {
            LOG.info("Generating inventory and meta-model...");
            final EventMapper mapper = new EventMapper();
            final ModelGenerator generator = new ModelGenerator(mapper, dataset);
            generator.generate();

            metaModel = generator.getMetaModel();
            inventory = generator.getInventory();
        } else {
            metaModel = null;
            inventory = null;
        }

        final Set<String> ignoredAlarmIds = new HashSet<>();

        LOG.info("Generating the list of alarms...");
        final Alarms alarms = new Alarms();
        final Map<String, List<EventRecord>> eventsByAlarmId = dataset.getEvents().stream()
                .sorted(Comparator.comparing(EventRecord::getTime))
                .filter(e -> {
                    if (Strings.isBlank(e.getAlarmId())) {
                        // No alarm id!
                        return false;
                    }

                    final EventDefinition matchingDef = getMachingEvenfDef(e);
                    if (matchingDef == null || matchingDef.isIgnored()) {
                        // No matching event definition, or the events should be ignored skip for now
                        ignoredAlarmIds.add(e.getAlarmId());
                        return false;
                    }

                    return true;
                })
                .collect(Collectors.groupingBy(EventRecord::getAlarmId));

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
                    final List<ModelObjectKey> modelObjectKeys = OceModelObjetKeyMapper.getRelatedObjectIds(modelObject);
                    final ModelObjectKey firstKey = modelObjectKeys.get(0);
                    alarm.setInventoryObjectType("node");
                    alarm.setInventoryObjectId(firstKey.getTokens().get(0));
                }
            }
        });

        LOG.info("Generating the situations..");
        final Situations situations = new Situations();
        dataset.getTickets().stream()
                .sorted(Comparator.comparing(TicketRecord::getCreationTime))
                .forEach(t -> {
                    final Situation situation = new Situation();
                    situation.setId(t.getTicketId());
                    situation.setCreationTime(t.getCreationTime().getTime());
                    situation.setSeverity(toSeverity(t.getSeverity()));
                    situation.setSummary(t.getDescription());
                    situation.setDescription(t.getDescription());
                    situation.getAlarmRef().addAll(getCausalityTree(dataset, t, ignoredAlarmIds));
                    situations.getSituation().add(situation);
                });

        if (!includeAllSituations) {
            // Remove incidents with less than 2 alarms
            situations.getSituation().removeIf(s -> s.getAlarmRef().size() < 2);
        }

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

    private static List<AlarmRef> getCausalityTree(CpnDataset dataset, TicketRecord ticket, Set<String> ignoredAlarmIds) {
        final List<EventRecord> eventsInTicket = dataset.getEventsByTicketId(ticket.getTicketId());
        final Map<String, List<EventRecord>> eventsInTicketByAlarmId = eventsInTicket.stream()
                .collect(Collectors.groupingBy(EventRecord::getAlarmId));
        return eventsInTicketByAlarmId.keySet().stream()
                .filter(alarmId -> !ignoredAlarmIds.contains(alarmId))
                .map(alarmId -> {
                    final AlarmRef cause = new AlarmRef();
                    cause.setId(alarmId);
                    return cause;
                })
                .collect(Collectors.toList());
    }

    private static List<AlarmRef> getCausalityTree(CpnDataset dataset, EventRecord event) {
        final String causingEventId = event.getCausingEventId();
        if (Strings.isBlank(causingEventId)) {
            return Collections.emptyList();
        }
        final EventRecord causedBy = dataset.getEventById(causingEventId);
        if (causedBy == null) {
            return Collections.emptyList();
        }
        AlarmRef cause = new AlarmRef();
        cause.setId(causedBy.getEventId());
        cause.getAlarmRef().addAll(getCausalityTree(dataset, causedBy));
        return Collections.singletonList(cause);
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
