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

package org.opennms.oce.tools.onms.onms2oce;

import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;

import org.opennms.oce.datasource.v1.schema.Alarm;
import org.opennms.oce.datasource.v1.schema.AlarmRef;
import org.opennms.oce.datasource.v1.schema.Alarms;
import org.opennms.oce.datasource.v1.schema.Event;
import org.opennms.oce.datasource.v1.schema.Inventory;
import org.opennms.oce.datasource.v1.schema.MetaModel;
import org.opennms.oce.datasource.v1.schema.Severity;
import org.opennms.oce.datasource.v1.schema.Situation;
import org.opennms.oce.datasource.v1.schema.Situations;
import org.opennms.oce.tools.cpn.model.EventRecord;
import org.opennms.oce.tools.cpn2oce.EventMapper;
import org.opennms.oce.tools.cpn2oce.model.EventDefinition;
import org.opennms.oce.tools.es.ESClient;
import org.opennms.oce.tools.es.ESClusterConfiguration;
import org.opennms.oce.tools.onms.alarmdto.AlarmDocumentDTO;
import org.opennms.oce.tools.onms.alarmdto.EventDocumentDTO;
import org.opennms.oce.tools.onms.view.ESBackedOnmsDatasetViewer;
import org.opennms.oce.tools.onms.view.OnmsDatasetView;
import org.opennms.oce.tools.onms.view.OnmsDatasetViewer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used to generate data that can be consumed by OCE.
 */
public class OnmsOceGenerator  {

    private static final Logger LOG = LoggerFactory.getLogger(OnmsOceGenerator.class);

    private final OnmsDatasetViewer viewer;
    private final boolean modelGenerationDisabled;
    private final File targetFolder;
    private Integer situationId;

    private Situations situations;
    private MetaModel metaModel;
    private Inventory inventory;
    private Alarms alarms;


    public static class Builder {
        private OnmsDatasetViewer viewer;
        private boolean modelGenerationDisabled = false;
        private File targetFolder;
        private Integer situationId;

        public Builder withViewer(OnmsDatasetViewer viewer) {
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

        public Builder withSituationId(Integer situationId) {
            this.situationId = situationId;
            return this;
        }

        public OnmsOceGenerator build() {
            Objects.requireNonNull(viewer, "viewer is required");
            return new OnmsOceGenerator(this);
        }
    }

    private OnmsOceGenerator(Builder builder) {
        this.viewer = builder.viewer;
        this.modelGenerationDisabled = builder.modelGenerationDisabled;
        this.targetFolder = builder.targetFolder;
        this.situationId = builder.situationId;
    }

    private List<AlarmDocumentDTO> reduceSituations(List<AlarmDocumentDTO> situations) {
            Map<Integer, AlarmDocumentDTO> filtered = new HashMap<>();
            situations.forEach(s -> {
                filtered.computeIfAbsent(s.getId(), k -> s);
                AlarmDocumentDTO sit = filtered.get(s.getId());
                // Add any missing alarm IDs
                // update earliest and latest times
                if (s.getFirstEventTime() < sit.getFirstEventTime()) {
                    sit.setFirstEventTime(s.getFirstEventTime());
                }
                if (s.getLastEventTime() > sit.getLastEventTime()) {
                    sit.setLastEventTime(s.getLastEventTime());
                }
                if (!sit.getRelatedAlarmIds().containsAll(s.getRelatedAlarmIds())) {
                    s.getRelatedAlarms().stream().filter(a -> !sit.getRelatedAlarmIds().contains(a.getId())).forEach(sit::addRelatedAlarm);
                }
            });
            return filtered.values().stream().collect(Collectors.toList());
    }

    public void generate() {
        LOG.info("Generating the situations..");
        situations = new Situations();

        final List<EventDocumentDTO> allEvents = new LinkedList<>();
        final List<AlarmDocumentDTO> allAlarms = new LinkedList<>();

        List<AlarmDocumentDTO> filteredSituations = new LinkedList<>();
        if (situationId == null) {
            viewer.getSituationsForTimeRange(filteredSituations::addAll);
            filteredSituations = reduceSituations(filteredSituations);

            // Get all events and Alarms for the same period
            viewer.getEventsForTimeRange(allEvents::addAll);
            viewer.getAlarmsForTimeRange(allAlarms::addAll);

        } else {
            AlarmDocumentDTO situation = viewer.getSituationWithId(situationId);
            if (situation == null) {
                throw new IllegalStateException("No situation found with id: " + situationId);
            }
            filteredSituations.add(situation);
            // FIXME - populate allEvents and allAlarms
        }

        LOG.info("THere are {} situations", filteredSituations.size());

        int counter = 1;
        for (AlarmDocumentDTO s : filteredSituations) {
            final Situation situation = new Situation();
            situation.setId(Integer.toString(s.getId()));
            situation.setCreationTime(s.getFirstEventTime());
            situation.setSeverity(toSeverity(s.getSeverityId()));
            situation.setSummary(s.getDescription());
            situation.setDescription(s.getDescription());

            final List<AlarmDocumentDTO> alarmsInSituation = new LinkedList<>();
            // FIXME - use callback
            // alarmsInSituation.addAll(viewer.getAlarmsInSituation(s, alarmsInSituation::addAll));
            alarmsInSituation.addAll(allAlarms.stream().filter(a -> s.getRelatedAlarmIds().contains(a.getId())).collect(Collectors.toList()));

            LOG.info("Situation {} of {}", counter++, filteredSituations.size());
            LOG.info("There are {} alarms in Situation {}", alarmsInSituation.size(), s.getId());
            LOG.info("There are {} _distinct_ alarms in Situation {}", alarmsInSituation.stream().map(AlarmDocumentDTO::getId).distinct().collect(Collectors.toList()).size());

            final List<EventDocumentDTO> eventsInSituation = new LinkedList<>();
            // alarmsInSituation.stream().map(AlarmDocumentDTO::getLastEvent).forEach(eventsInSituation::add);
            eventsInSituation.addAll(alarmsInSituation.stream().map(AlarmDocumentDTO::getLastEvent).collect(Collectors.toList()));

            LOG.info("THere are {} events in Situation {}", eventsInSituation.size(), s.getId());
            // LOG.info("THere are {} _distinct_ events in Situation {}", eventsInSituation.stream().map(EventDocumentDTO::getId).distinct().collect(Collectors.toList()).size());

            if (eventsInSituation.size() < 1) {
                LOG.info("No events for ticket: {}. Ignoring.", s);
                continue;
            }
            // allEvents.addAll(eventsInSituation);
            // allAlarms.addAll(alarmsInSituation);
            situation.getAlarmRef().addAll(getCausalityTree(s, alarmsInSituation, eventsInSituation));
            situations.getSituation().add(situation);
        }

        if (!modelGenerationDisabled) {
            LOG.info("Generating inventory and meta-model...");
            final OnmsOceModelGenerator generator = new OnmsOceModelGenerator(allAlarms);
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
        final Map<String, List<EventDocumentDTO>> eventsByUei = allEvents.stream().collect(Collectors.groupingBy(EventDocumentDTO::getAlarmReductionKey));
        // Process each alarm
        eventsByUei.forEach((alarmId, events) -> {
            // TODO - we need time in EventDocumentDTO
            events.sort(Comparator.comparing(EventDocumentDTO::getTime));
            
            final EventDocumentDTO firstEvent = events.get(0);
            final EventDocumentDTO lastEvent = events.get(events.size() - 1);
             
            final Alarm alarm = new Alarm();
            alarm.setId(alarmId);
            alarm.setSummary(lastEvent.getDescription());
            alarm.setDescription(lastEvent.getLogMessage());
            alarm.setLastSeverity(toSeverity(lastEvent.getSeverity()));
            alarm.setFirstEventTime(firstEvent.getTime().getTime());
            alarm.setLastEventTime(lastEvent.getTime().getTime());
            alarms.getAlarm().add(alarm);

            for (EventDocumentDTO e : events) {
                final Event event = new Event();
                event.setId(e.getId().toString());
                event.setSummary(e.getDescription());
                event.setDescription(e.getLogMessage());
                event.setSeverity(toSeverity(e.getSeverity()));
                // FIXME event.setSource(e.getSource());
                event.setTime(e.getTime().getTime());
                alarm.getEvent().add(event);

/*
                 FIXME
                 if (alarm.getInventoryObjectType() == null && alarm.getInventoryObjectId() == null) {
                    final EventDefinition matchingDef = getMachingEvenfDef(e);
                    if (matchingDef == null) {
                        throw new IllegalStateException("Should not happen!");
                    }
                    final ModelObject modelObject = matchingDef.getModelObjectTree(e);
                    alarm.setInventoryObjectType(modelObject.getType().toString());
                    alarm.setInventoryObjectId(modelObject.getId());
                }*/
            }
        });
    }

    public void writeResultsToDisk(String prefix) {
        Objects.requireNonNull(targetFolder, "target folder must be set");
        LOG.info("Marshalling results to disk...");
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(MetaModel.class, Inventory.class, Alarms.class, Situations.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

            if (metaModel != null && inventory != null) {
                LOG.info("Writing {} model object definitions (meta-model)...", metaModel.getModelObjectDef().size());
                marshaller.marshal(metaModel, new File(targetFolder, prefix + ".metamodel.xml"));
                LOG.info("Writing {} model object entries (inventory)...", inventory.getModelObjectEntry().size());
                marshaller.marshal(inventory, new File(targetFolder, prefix + ".inventory.xml"));
            }
            LOG.info("Writing {} alarms...", alarms.getAlarm().size());
            marshaller.marshal(alarms, new File(targetFolder, prefix + ".alarms.xml"));
            LOG.info("Writing {} situations...", situations.getSituation().size());
            marshaller.marshal(situations, new File(targetFolder, prefix + ".situations.xml"));
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

    private static List<AlarmRef> getCausalityTree(AlarmDocumentDTO situation, List<AlarmDocumentDTO> alarmsInSituation, List<EventDocumentDTO> eventsInSituation) {
        final Map<String, List<EventDocumentDTO>> eventsInTicketByAlarmId = eventsInSituation.stream().filter(Objects::nonNull).collect(Collectors.groupingBy(EventDocumentDTO::getUei));
        return eventsInTicketByAlarmId.keySet().stream()
                .map(alarmId -> {
                    final AlarmRef cause = new AlarmRef();
                    cause.setId(alarmId);
                    return cause;
                })
                .collect(Collectors.toList());
    }

    private static Severity toSeverity(int severity) {
        switch (severity) {
        case 7:
            return Severity.CRITICAL;
        case 6:
            return Severity.MAJOR;
        case 5:
            return Severity.MINOR;
        case 4:
            return Severity.WARNING;
        case 3:
            return Severity.NORMAL;
        case 2:
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
