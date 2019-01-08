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

import org.opennms.oce.tools.cpn.EventUtils;
import org.opennms.oce.tools.cpn.model.EventRecord;
import org.opennms.oce.tools.cpn.model.TrapRecord;
import org.opennms.oce.tools.onms.client.ESEventDTO;

public class EventMatcher {
    private static final long timeDeltaAllowedMs = TimeUnit.MILLISECONDS.convert(30, TimeUnit.SECONDS);

    public static Map<String, String> matchSyslogEvents(List<EventRecord> cpnSyslogs, List<ESEventDTO> onmsSyslogs)
            throws ExecutionException, InterruptedException {
        // Group the syslogs by node
        Map<String, List<GenericSyslogMessage>> cpnSyslogsByHost = groupCpnSyslogsByHost(cpnSyslogs);
        Map<String, List<GenericSyslogMessage>> onmsSyslogsByHost = groupOnmsSyslogsByHost(onmsSyslogs);

        Map<String, String> cpnEventIdToOnmsEventId = new HashMap<>();

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
                                    onmsSyslog.getId()));
                }
            }
        }

        return Collections.unmodifiableMap(cpnEventIdToOnmsEventId);
    }

    public static Map<String, String> matchTrapEvents(List<TrapRecord> cpnTraps, List<ESEventDTO> onmsTraps) {
        // Group the traps by node and sort them by time
        Map<String, Map<String, List<TrapRecord>>> cpnTrapsByHost = groupCpnTrapsByHost(cpnTraps);
        Map<String, Map<String, List<ESEventDTO>>> onmsTrapsByHost = gorupOnmsTrapsByHost(onmsTraps);

        Map<String, String> cpnEventIdToOnmsEventId = new HashMap<>();

        // Iterate through each list of traps by host
        for (Map.Entry<String, Map<String, List<TrapRecord>>> cpnEntry : cpnTrapsByHost.entrySet()) {
            String host = cpnEntry.getKey();

            for (Map.Entry<String, List<TrapRecord>> cpnTrapsForHostByOid : cpnEntry.getValue().entrySet()) {
                List<TrapRecord> cpnSyslogsForHostAndOid = cpnTrapsForHostByOid.getValue();
                // Find the potential matches by looking up all the Onms traps for the same host
                Map<String, List<ESEventDTO>> hostMap = onmsTrapsByHost.get(host);

                if (hostMap != null) {
                    for (TrapRecord cpnTrap : cpnSyslogsForHostAndOid) {
                        List<ESEventDTO> potentialOnmsMatches = hostMap.get(cpnTrap.getTrapTypeOid());

                        if (potentialOnmsMatches != null) {
                            // Iterate through the syslogs for this host

                            // Find the closest matching event (by time delta) within the allowed time range
                            timeWindowSearch(cpnTrap.getTime().getTime(), potentialOnmsMatches)
                                    .ifPresent(id -> cpnEventIdToOnmsEventId.put(cpnTrap.getEventId(), id.toString()));
                        }
                    }
                }
            }
        }

        return Collections.unmodifiableMap(cpnEventIdToOnmsEventId);
    }

    private static Map<String, List<GenericSyslogMessage>> groupCpnSyslogsByHost(List<EventRecord> cpnSyslogs) throws ExecutionException, InterruptedException {
        Map<String, List<GenericSyslogMessage>> cpnSyslogsByHost = new HashMap<>();

        for (EventRecord cpnSyslogEvent : cpnSyslogs) {
            GenericSyslogMessage genericSyslogMessage = GenericSyslogMessage.fromCpn(cpnSyslogEvent.getEventId(),
                    EventUtils.getNodeLabelFromLocation(cpnSyslogEvent.getLocation()),
                    cpnSyslogEvent.getDetailedDescription());
            mapSyslog(genericSyslogMessage, cpnSyslogsByHost);
        }

        return cpnSyslogsByHost;
    }

    private static Map<String, List<GenericSyslogMessage>> groupOnmsSyslogsByHost(List<ESEventDTO> onmsSyslogs) {
        Map<String, List<GenericSyslogMessage>> onmsSylsogsByHost = new HashMap<>();

        for (ESEventDTO onmsSyslogEvent : onmsSyslogs) {
            GenericSyslogMessage genericSyslogMessage = GenericSyslogMessage.fromOnms(onmsSyslogEvent.getId(),
                    onmsSyslogEvent.getNodeLabel(),
                    onmsSyslogEvent.getSyslogMessage(), onmsSyslogEvent.getTimestamp());
            mapSyslog(genericSyslogMessage, onmsSylsogsByHost);
        }

        return onmsSylsogsByHost;
    }

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

    private static Map<String, Map<String, List<TrapRecord>>> groupCpnTrapsByHost(List<TrapRecord> cpnTraps) {
        Map<String, Map<String, List<TrapRecord>>> cpnTrapsByHost = new HashMap<>();

        for (TrapRecord cpnTrap : cpnTraps) {
            cpnTrapsByHost.compute(EventUtils.getNodeLabelFromLocation(cpnTrap.getLocation()), (host, currentMap) -> {
                if (currentMap == null) {
                    Map<String, List<TrapRecord>> trapsForhost = new HashMap<>();
                    List<TrapRecord> trapsForOid = new ArrayList<>();
                    trapsForOid.add(cpnTrap);
                    trapsForhost.put(cpnTrap.getTrapTypeOid(), trapsForOid);

                    return trapsForhost;
                }

                currentMap.compute(cpnTrap.getTrapTypeOid(), (oid, currentTraps) -> {
                    if (currentTraps == null) {
                        List<TrapRecord> trapsForOid = new ArrayList<>();
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

    private static Map<String, Map<String, List<ESEventDTO>>> gorupOnmsTrapsByHost(List<ESEventDTO> onmsTraps) {
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
}
