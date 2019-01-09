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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.opennms.oce.tools.cpn.EventUtils;
import org.opennms.oce.tools.cpn.events.MatchingSyslogEventRecord;
import org.opennms.oce.tools.cpn.events.MatchingTrapEventRecord;
import org.opennms.oce.tools.cpn2oce.EventMapper;
import org.opennms.oce.tools.cpn2oce.model.ModelObject;
import org.opennms.oce.tools.onms.client.ESEventDTO;
import org.opennms.oce.tools.onms.model.v1.OnmsSnmpConstants;
import org.snmp4j.smi.OID;

public class EventMatcher {
    private static final long timeDeltaAllowedMs = TimeUnit.MILLISECONDS.convert(120, TimeUnit.SECONDS);

    public static Map<String, Integer> matchSyslogEventsScopedByTimeAndHost(List<? extends MatchingSyslogEventRecord> cpnSyslogs, List<ESEventDTO> onmsSyslogs)
            throws ExecutionException, InterruptedException {
        // Group the syslogs by node
        List<GenericSyslogMessage> genericCpnSyslogs = mapSyslogMessagesFromCpn(cpnSyslogs);
        List<GenericSyslogMessage> genericOnmsSyslogs = mapSyslogMessagesFromOnms(onmsSyslogs);

        Map<String, Integer> cpnEventIdToOnmsEventId = new HashMap<>();

        // Iterate through each list of syslogs
        for (GenericSyslogMessage cpnSyslog : genericCpnSyslogs) {
            genericOnmsSyslogs.stream()
                    .filter(cpnSyslog::equalsIgnoringHost)
                    .findAny()
                    // If we found a match then record a mapping between the event Ids
                    .ifPresent(onmsSyslog -> cpnEventIdToOnmsEventId.put(cpnSyslog.getId(),
                            Integer.parseInt(onmsSyslog.getId())));
        }

        return Collections.unmodifiableMap(cpnEventIdToOnmsEventId);
    }

    private static List<ESEventDTO> filterMatchesForTrap(MatchingTrapEventRecord cpnTrap, List<ESEventDTO> potentialMatches) {
        if (oid2str(OnmsSnmpConstants.linkDown).equals(cpnTrap.getTrapTypeOid()) ||
                oid2str(OnmsSnmpConstants.linkUp).equals(cpnTrap.getTrapTypeOid())) {
            // We know we a link up/down trap, extract the port from the location
            final ModelObject mo = EventMapper.createPortObject(cpnTrap);
            String ifDescr = mo.getSpecificId(); // the *specific* id of the interface object is the ifDescr!

            return potentialMatches.stream()
                    .filter(e -> e.getP_oids().stream()
                            .anyMatch(oidMap -> oidMap.get("oid").startsWith(oid2str(OnmsSnmpConstants.ifDescr)) && oidMap.get("value").equals(ifDescr)))
                    .collect(Collectors.toList());
        }
        return potentialMatches;
    }

    public static Map<String, Integer> matchTrapEventsScopedByTimeAndHost(List<? extends MatchingTrapEventRecord> cpnTraps,
                                                                          List<ESEventDTO> onmsTraps) {
        // Group the traps by node and sort them by time
        Map<String, List<MatchingTrapEventRecord>> cpnTrapsByType = groupCpnTrapsByType(cpnTraps);
        Map<String, List<ESEventDTO>> onmsTrapsByType = groupOnmsTrapsByType(onmsTraps);

        Map<String, Integer> cpnEventIdToOnmsEventId = new HashMap<>();

        // Iterate through each list of traps by host
        for (Map.Entry<String, List<MatchingTrapEventRecord>> cpnEntry : cpnTrapsByType.entrySet()) {

            String type = cpnEntry.getKey();
            List<MatchingTrapEventRecord> cpnTrapsForOid = cpnEntry.getValue();
            // Find the potential matches by looking up all the Onms traps for the same type
            List<ESEventDTO> potentialMatches = onmsTrapsByType.get(type);

            if (potentialMatches != null) {
                for (MatchingTrapEventRecord cpnTrap : cpnTrapsForOid) {
                    // Refine the potential matches for this specific instance of the trap
                    List<ESEventDTO> refinedPotentialMatches = filterMatchesForTrap(cpnTrap, potentialMatches);

                    // Find the closest matching event (by time delta) within the allowed time range
                    timeWindowSearch(cpnTrap.getTime().getTime(), refinedPotentialMatches)
                            .ifPresent(id -> cpnEventIdToOnmsEventId.put(cpnTrap.getEventId(), id));
                }
            }
        }

        return Collections.unmodifiableMap(cpnEventIdToOnmsEventId);
    }

    private static List<GenericSyslogMessage> mapSyslogMessagesFromCpn(List<? extends MatchingSyslogEventRecord> syslogEvents) throws ExecutionException, InterruptedException {
        List<GenericSyslogMessage> genericSyslogMessages = new ArrayList<>();

        for (MatchingSyslogEventRecord syslogEvent : syslogEvents) {
            genericSyslogMessages.add(GenericSyslogMessage.fromCpn(syslogEvent.getEventId(),
                    EventUtils.getNodeLabelFromLocation(syslogEvent.getLocation()),
                    syslogEvent.getDetailedDescription()));
        }

        return genericSyslogMessages;
    }

    private static List<GenericSyslogMessage> mapSyslogMessagesFromOnms(List<ESEventDTO> syslogEvents) {
        List<GenericSyslogMessage> genericSyslogMessages = new ArrayList<>();

        syslogEvents.forEach(syslogEvent -> genericSyslogMessages.add(GenericSyslogMessage.fromOnms(syslogEvent.getId(),
                syslogEvent.getNodeLabel(),
                syslogEvent.getSyslogMessage(), syslogEvent.getTimestamp())));

        return genericSyslogMessages;
    }

    private static Map<String, List<MatchingTrapEventRecord>> groupCpnTrapsByType(List<? extends MatchingTrapEventRecord> cpnTraps) {
        Map<String, List<MatchingTrapEventRecord>> cpnTrapsByType = new HashMap<>();

        for (MatchingTrapEventRecord cpnTrap : cpnTraps) {
            cpnTrapsByType.compute(cpnTrap.getTrapTypeOid(), (trapType, currentList) -> {
                if (currentList == null) {
                    List<MatchingTrapEventRecord> trapsForOid = new ArrayList<>();
                    trapsForOid.add(cpnTrap);

                    return trapsForOid;
                }

                currentList.add(cpnTrap);

                return currentList;
            });
        }

        return cpnTrapsByType;
    }

    private static Map<String, List<ESEventDTO>> groupOnmsTrapsByType(List<ESEventDTO> onmsTraps) {
        Map<String, List<ESEventDTO>> onmsTrapsByType = new HashMap<>();

        for (ESEventDTO onmsTrap : onmsTraps) {
            String trapTypeOidForTrap;

            if (!onmsTrap.getTrapTypeOid().isPresent()) {
                // If this trap does not have a trap type then we will ignore it
                continue;
            }

            trapTypeOidForTrap = onmsTrap.getTrapTypeOid().get();

            onmsTrapsByType.compute(trapTypeOidForTrap, (oid, currentTraps) -> {
                if (currentTraps == null) {
                    List<ESEventDTO> trapsForOid = new ArrayList<>();
                    trapsForOid.add(onmsTrap);

                    return trapsForOid;
                }

                currentTraps.add(onmsTrap);

                return currentTraps;
            });
        }

        return onmsTrapsByType;
    }

    private static Optional<Integer> timeWindowSearch(long timestamp, List<ESEventDTO> traps) {
        long previousDelta = Long.MAX_VALUE;
        Optional<Integer> match = Optional.empty();

        for (ESEventDTO trap : traps) {
            long trapTime = trap.getTimestamp().getTime();

            if (trapTime > (timestamp + timeDeltaAllowedMs)) {
                // We've gone past the window, time to stop looking
                break;
            }

            if (trapTime > (timestamp - timeDeltaAllowedMs)) {
                // We are in the window
                long newDelta = Math.abs(timestamp - trapTime);

                if (newDelta < previousDelta) {
                    // This one matches better than the previous one
                    previousDelta = newDelta;
                    match = Optional.of(trap.getId());
                } else {
                    // This one was worse which indicates we already found the best match so we can stop
                    break;
                }
            }
        }

        return match;
    }

    // TODO: The below code is for handling the case of lists of events that haven't been scoped by host yet
    // Not sure if this is still needed, if not the below can be deleted
    
    // TODO: Untested, we can probably remove this if we always plan on scoping by host
    public static Map<String, Integer> matchSyslogEventsScopedByTime(List<MatchingSyslogEventRecord> cpnSyslogs,
                                                                     List<ESEventDTO> onmsSyslogs)
            throws ExecutionException, InterruptedException {
        // Group the syslogs by node
        Map<String, List<GenericSyslogMessage>> cpnSyslogsByHost = groupCpnSyslogsByHostAndType(cpnSyslogs);
        Map<String, List<GenericSyslogMessage>> onmsSyslogsByHost = groupOnmsSyslogsByHostAndType(onmsSyslogs);

        Map<String, Integer> cpnEventIdToOnmsEventId = new HashMap<>();

        // Iterate through each list of syslogs by host
        for (Map.Entry<String, List<GenericSyslogMessage>> cpnEntry : cpnSyslogsByHost.entrySet()) {
            String host = cpnEntry.getKey();
            List<GenericSyslogMessage> cpnSyslogsForhost = cpnEntry.getValue();
            // Find the potential matches by looking up all the Onms syslogs for the same host
            List<GenericSyslogMessage> potentialOnmsMatches = onmsSyslogsByHost.get(host);

            if (potentialOnmsMatches != null) {
                // Iterate through the syslogs for this host
                for (GenericSyslogMessage cpnSyslog : cpnSyslogsForhost) {
                    potentialOnmsMatches.stream()
                            .filter(cpnSyslog::equals)
                            .findAny()
                            // If we found a match then record a mapping between the event Ids
                            .ifPresent(onmsSyslog -> cpnEventIdToOnmsEventId.put(cpnSyslog.getId(),
                                    Integer.parseInt(onmsSyslog.getId())));
                }
            }
        }

        return Collections.unmodifiableMap(cpnEventIdToOnmsEventId);
    }

    // TODO: Untested, we can probably remove this if we always plan on scoping by host
    public static Map<String, Integer> matchTrapEventsScopedByTime(List<MatchingTrapEventRecord> cpnTraps,
                                                                   List<ESEventDTO> onmsTraps) {
        // Group the traps by node and sort them by time
        Map<String, Map<String, List<MatchingTrapEventRecord>>> cpnTrapsByHost = groupCpnTrapsByHostAndType(cpnTraps);
        Map<String, Map<String, List<ESEventDTO>>> onmsTrapsByHost = gorupOnmsTrapsByHostAndType(onmsTraps);

        Map<String, Integer> cpnEventIdToOnmsEventId = new HashMap<>();

        // Iterate through each list of traps by host
        for (Map.Entry<String, Map<String, List<MatchingTrapEventRecord>>> cpnEntry : cpnTrapsByHost.entrySet()) {
            String host = cpnEntry.getKey();

            for (Map.Entry<String, List<MatchingTrapEventRecord>> cpnTrapsForHostByOid :
                    cpnEntry.getValue().entrySet()) {
                List<MatchingTrapEventRecord> cpnSyslogsForHostAndOid = cpnTrapsForHostByOid.getValue();
                // Find the potential matches by looking up all the Onms traps for the same host
                Map<String, List<ESEventDTO>> hostMap = onmsTrapsByHost.get(host);

                if (hostMap != null) {
                    for (MatchingTrapEventRecord cpnTrap : cpnSyslogsForHostAndOid) {
                        List<ESEventDTO> potentialOnmsMatches = hostMap.get(cpnTrap.getTrapTypeOid());

                        if (potentialOnmsMatches != null) {
                            // Iterate through the traps for this host

                            // Find the closest matching event (by time delta) within the allowed time range
                            timeWindowSearch(cpnTrap.getTime().getTime(), potentialOnmsMatches)
                                    .ifPresent(id -> cpnEventIdToOnmsEventId.put(cpnTrap.getEventId(), id));
                        }
                    }
                }
            }
        }

        return Collections.unmodifiableMap(cpnEventIdToOnmsEventId);
    }

    // TODO: Untested, we can probably remove this if we always plan on scoping by host
    private static void mapSyslog(GenericSyslogMessage syslogMessage, Map<String, List<GenericSyslogMessage>> map) {
        map.compute(syslogMessage.getHost(), (host, current) -> {
            if (current == null) {
                List<GenericSyslogMessage> syslogsForHost = new ArrayList<>();
                syslogsForHost.add(syslogMessage);

                return syslogsForHost;
            }

            current.add(syslogMessage);

            return current;
        });
    }

    // TODO: Untested, we can probably remove this if we always plan on scoping by host
    private static Map<String, List<GenericSyslogMessage>> groupCpnSyslogsByHostAndType(List<MatchingSyslogEventRecord> cpnSyslogs) throws ExecutionException, InterruptedException {
        Map<String, List<GenericSyslogMessage>> cpnSyslogsByHost = new HashMap<>();

        for (MatchingSyslogEventRecord cpnSyslogEvent : cpnSyslogs) {
            GenericSyslogMessage genericSyslogMessage = GenericSyslogMessage.fromCpn(cpnSyslogEvent.getEventId(),
                    EventUtils.getNodeLabelFromLocation(cpnSyslogEvent.getLocation()),
                    cpnSyslogEvent.getDetailedDescription());
            mapSyslog(genericSyslogMessage, cpnSyslogsByHost);
        }

        return cpnSyslogsByHost;
    }

    // TODO: Untested, we can probably remove this if we always plan on scoping by host
    private static Map<String, List<GenericSyslogMessage>> groupOnmsSyslogsByHostAndType(List<ESEventDTO> onmsSyslogs) {
        Map<String, List<GenericSyslogMessage>> onmsSylsogsByHost = new HashMap<>();

        for (ESEventDTO onmsSyslogEvent : onmsSyslogs) {
            GenericSyslogMessage genericSyslogMessage = GenericSyslogMessage.fromOnms(onmsSyslogEvent.getId(),
                    onmsSyslogEvent.getNodeLabel(),
                    onmsSyslogEvent.getSyslogMessage(), onmsSyslogEvent.getTimestamp());
            mapSyslog(genericSyslogMessage, onmsSylsogsByHost);
        }

        return onmsSylsogsByHost;
    }

    // TODO: Untested, we can probably remove this if we always plan on scoping by host
    private static Map<String, Map<String, List<MatchingTrapEventRecord>>> groupCpnTrapsByHostAndType(List<MatchingTrapEventRecord> cpnTraps) {
        Map<String, Map<String, List<MatchingTrapEventRecord>>> cpnTrapsByHost = new HashMap<>();

        for (MatchingTrapEventRecord cpnTrap : cpnTraps) {
            cpnTrapsByHost.compute(EventUtils.getNodeLabelFromLocation(cpnTrap.getLocation()), (host, currentMap) -> {
                if (currentMap == null) {
                    Map<String, List<MatchingTrapEventRecord>> trapsForhost = new HashMap<>();
                    List<MatchingTrapEventRecord> trapsForOid = new ArrayList<>();
                    trapsForOid.add(cpnTrap);
                    trapsForhost.put(cpnTrap.getTrapTypeOid(), trapsForOid);

                    return trapsForhost;
                }

                currentMap.compute(cpnTrap.getTrapTypeOid(), (oid, currentTraps) -> {
                    if (currentTraps == null) {
                        List<MatchingTrapEventRecord> trapsForOid = new ArrayList<>();
                        trapsForOid.add(cpnTrap);

                        return trapsForOid;
                    }

                    currentTraps.add(cpnTrap);

                    return currentTraps;
                });

                return currentMap;
            });
        }

        return cpnTrapsByHost;
    }

    // TODO: Untested, we can probably remove this if we always plan on scoping by host
    private static Map<String, Map<String, List<ESEventDTO>>> gorupOnmsTrapsByHostAndType(List<ESEventDTO> onmsTraps) {
        Map<String, Map<String, List<ESEventDTO>>> onmsTrapsByHost = new HashMap<>();

        for (ESEventDTO onmsTrap : onmsTraps) {
            String trapTypeOidForTrap;

            if (!onmsTrap.getTrapTypeOid().isPresent()) {
                // If this trap does not have a trap type then we will ignore it
                continue;
            }

            trapTypeOidForTrap = onmsTrap.getTrapTypeOid().get();

            onmsTrapsByHost.compute(onmsTrap.getNodeLabel(), (host, currentMap) -> {
                if (currentMap == null) {
                    Map<String, List<ESEventDTO>> trapsForhost = new HashMap<>();
                    List<ESEventDTO> trapsForOid = new ArrayList<>();
                    trapsForOid.add(onmsTrap);
                    trapsForOid.sort(Comparator.comparingLong(trap -> trap.getTimestamp().getTime()));
                    trapsForhost.put(trapTypeOidForTrap, trapsForOid);

                    return trapsForhost;
                }

                currentMap.compute(trapTypeOidForTrap, (oid, currentTraps) -> {
                    if (currentTraps == null) {
                        List<ESEventDTO> trapsForOid = new ArrayList<>();
                        trapsForOid.add(onmsTrap);

                        return trapsForOid;
                    }

                    currentTraps.add(onmsTrap);

                    return currentTraps;
                });

                return currentMap;
            });
        }

        return onmsTrapsByHost;
    }

    private static String oid2str(OID oid) {
        return "." + oid.toDottedString();
    }
}
