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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.opennms.oce.tools.cpn.EventUtils;
import org.opennms.oce.tools.cpn.events.MatchingSyslogEventRecord;
import org.opennms.oce.tools.cpn.events.MatchingTrapEventRecord;
import org.opennms.oce.tools.cpn2oce.EventMapper;
import org.opennms.oce.tools.cpn2oce.model.ModelObject;
import org.opennms.oce.tools.onms.client.ESEventDTO;
import org.opennms.oce.tools.onms.model.v1.OnmsSnmpConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.smi.OID;

import com.google.common.annotations.VisibleForTesting;

public class EventMatcher {
    private static final Logger LOG = LoggerFactory.getLogger(EventMatcher.class);
    private static final long timeDeltaAllowedMs = TimeUnit.MILLISECONDS.convert(120, TimeUnit.SECONDS);
    @VisibleForTesting
    static final long syslogDateFuzzMs = TimeUnit.MILLISECONDS.convert(1, TimeUnit.SECONDS);

    private static final Mean mean = new Mean();
    private static final StandardDeviation standardDeviation = new StandardDeviation(false);


    public static Map<String, Integer> matchSyslogEventsScopedByTimeAndHost(List<? extends MatchingSyslogEventRecord> cpnSyslogs, List<ESEventDTO> onmsSyslogs) {
        final Set<Integer> alreadyMatchedOnmsEvents = new HashSet<>();
        // Group the syslogs by node
        List<GenericSyslogMessage> genericCpnSyslogs = mapSyslogMessagesFromCpn(cpnSyslogs);
        List<GenericSyslogMessage> genericOnmsSyslogs = mapSyslogMessagesFromOnms(onmsSyslogs);

        Map<String, Integer> cpnEventIdToOnmsEventId = new HashMap<>();
        AtomicBoolean foundMatch = new AtomicBoolean();

        // Iterate through each list of syslogs
        for (GenericSyslogMessage cpnSyslog : genericCpnSyslogs) {
            foundMatch.set(false);

            genericOnmsSyslogs.stream()
                    .filter(candidateSyslog ->
                            !alreadyMatchedOnmsEvents.contains(Integer.parseInt(candidateSyslog.getId())) &&
                                    cpnSyslog.equalsIgnoringHost(candidateSyslog))
                    .findAny()
                    // If we found a match then record a mapping between the event Ids
                    .ifPresent(onmsSyslog -> {
                        Integer onmsId = Integer.parseInt(onmsSyslog.getId());
                        cpnEventIdToOnmsEventId.put(cpnSyslog.getId(), onmsId);
                        alreadyMatchedOnmsEvents.add(onmsId);
                        foundMatch.set(true);
                    });

            // If we didn't find the event using an exact matching strategy, try allowing for some time delta
            if (!foundMatch.get()) {
                genericOnmsSyslogs.stream()
                        .filter(candidateSyslog ->
                                !alreadyMatchedOnmsEvents.contains(Integer.parseInt(candidateSyslog.getId())) &&
                                        cpnSyslog.equalsIgnoringHostFuzzyDate(candidateSyslog, syslogDateFuzzMs))
                        .findAny()
                        // If we found a match then record a mapping between the event Ids
                        .ifPresent(onmsSyslog -> {
                            Integer onmsId = Integer.parseInt(onmsSyslog.getId());
                            cpnEventIdToOnmsEventId.put(cpnSyslog.getId(), onmsId);
                            LOG.debug("Matched syslog events that were time-skewed: {}->{}",cpnSyslog.getId(), onmsId);
                            alreadyMatchedOnmsEvents.add(onmsId);
                        });
            }
        }

        return Collections.unmodifiableMap(cpnEventIdToOnmsEventId);
    }

    private static List<ESEventDTO> filterMatchesForTrap(MatchingTrapEventRecord cpnTrap,
                                                         List<ESEventDTO> potentialMatches) {
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

    /**
     * Matches traps from CPN/OpenNMS with the notion that they may not occur at the same time.
     *
     * This function attempts to match the traps based on their time and type.
     *
     * Time wise, we attempt to find a corresponding trap that closest in time, while minimizing the standard
     * deviation of these differences.
     *
     * @param cpnTraps traps from CPN to match
     * @param onmsTraps traps from OpenNMS to match
     * @return map of CPN events id, to OpenNMS event id
     */
    public static Map<String, Integer> matchTrapEventsScopedByTimeAndHost(List<? extends MatchingTrapEventRecord> cpnTraps,
                                                                          List<ESEventDTO> onmsTraps) {
        // Index the traps by id for quick lookup
        final Map<String, MatchingTrapEventRecord> cpnTrapsById = cpnTraps.stream()
                .collect(Collectors.toMap(MatchingTrapEventRecord::getEventId, e -> e));
        final Map<Integer, ESEventDTO> onmsTrapsById = onmsTraps.stream()
                .collect(Collectors.toMap(ESEventDTO::getId, e -> e));

        int maxIterations = 10; // limit the number of iterations - we shouldn't hit this, but just to be safe
        double targetSigma = TimeUnit.SECONDS.toMillis(5); // acceptable value of sigma
        double sigma;
        long mu = 0;
        long lastMu;
        int iteration = 1;
        boolean didMatchAllTraps;

        Map<String, Integer> cpnEventIdToOnmsEventId;
        do {
            // Match the traps, correcting the timestamps by the average delta
            cpnEventIdToOnmsEventId = matchTrapEventsScopedByTimeAndHost(cpnTraps, onmsTraps, mu);

            // Compute the difference in time between the matches
            final double[] deltas = cpnEventIdToOnmsEventId.entrySet().stream()
                    .mapToDouble(e -> {
                        final long cpnTrapTime = cpnTrapsById.get(e.getKey()).getTime().getTime();
                        final long onmsTrapTime = onmsTrapsById.get(e.getValue()).getTimestamp().getTime();
                        return (double)(onmsTrapTime - cpnTrapTime);
                    }).toArray();

            // Compute the mean and standard deviation of the deltas
            lastMu = mu;
            mu = (long)Math.floor(mean.evaluate(deltas));
            sigma = standardDeviation.evaluate(deltas);

            didMatchAllTraps = cpnTraps.size() - cpnEventIdToOnmsEventId.keySet().size() == 0;
            /* DEBUG
            System.out.printf("Iteration: %d\n", iteration);
            System.out.printf("Deltas: %s\n", Arrays.toString(deltas));
            System.out.printf("Mu: %d (previously at %d)\n", mu, lastMu);
            System.out.printf("Sigma: %.2f\n", sigma);
            System.out.printf("All matched?: %s\n", didMatchAllTraps);
            */

            iteration++;
        } while (iteration <= maxIterations // while we haven't reached the iteration limit
                && (!didMatchAllTraps || sigma > targetSigma) // while we haven't matched all of the traps AND reached our target sigma
                && mu != lastMu); // while we're still adjusting the mean

        return Collections.unmodifiableMap(cpnEventIdToOnmsEventId);
    }

    private static Map<String, Integer> matchTrapEventsScopedByTimeAndHost(List<? extends MatchingTrapEventRecord> cpnTraps,
                                                                          List<ESEventDTO> onmsTraps, long drift) {
        final Set<Integer> alreadyMatchedOnmsEvents = new HashSet<>();
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
                    // Remove any Onms events that have already been paired so we don't pair them twice
                    potentialMatches.removeIf(potential -> alreadyMatchedOnmsEvents.contains(potential.getId()));
                    // Refine the potential matches for this specific instance of the trap
                    List<ESEventDTO> refinedPotentialMatches = filterMatchesForTrap(cpnTrap, potentialMatches);

                    // Find the closest matching event (by time delta) within the allowed time range
                    timeWindowSearch(cpnTrap.getTime().getTime(), refinedPotentialMatches, drift)
                            .ifPresent(id -> {
                                cpnEventIdToOnmsEventId.put(cpnTrap.getEventId(), id);
                                alreadyMatchedOnmsEvents.add(id);
                            });
                }
            }
        }
        return Collections.unmodifiableMap(cpnEventIdToOnmsEventId);
    }

    private static List<GenericSyslogMessage> mapSyslogMessagesFromCpn(List<? extends MatchingSyslogEventRecord> syslogEvents) {
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

    private static Map<String, List<MatchingTrapEventRecord>> groupCpnTrapsByType(List<?
            extends MatchingTrapEventRecord> cpnTraps) {
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

    private static Optional<Integer> timeWindowSearch(long timestamp, List<ESEventDTO> traps, long drift) {
        long previousDelta = Long.MAX_VALUE;
        Optional<Integer> match = Optional.empty();

        for (ESEventDTO trap : traps) {
            long trapTime = trap.getTimestamp().getTime() - drift;

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

    private static String oid2str(OID oid) {
        return "." + oid.toDottedString();
    }
}
