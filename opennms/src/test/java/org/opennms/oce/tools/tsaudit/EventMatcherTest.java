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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.junit.Test;
import org.opennms.oce.tools.cpn.events.MatchingSyslogEventRecord;
import org.opennms.oce.tools.cpn.events.MatchingTrapEventRecord;
import org.opennms.oce.tools.onms.client.ESEventDTO;

public class EventMatcherTest {

    @Test
    public void canMatchLinkDownTraps() {
        List<MatchingTrapEventRecord> cpnTraps = new ArrayList<>();
        List<ESEventDTO> onmsTraps = new ArrayList<>();

        Date ts = new Date();

        String cpnId = "101";
        String host = "testhost";
        int ifIndex = 436305920;
        String ifDescr = "Ethernet1/32";
        String location = String.format("%s: %s", host, ifDescr);

        String trapType = ".1.3.6.1.6.3.1.1.5.3";
        MatchingTrapEventRecord t1 = new ImplMatchingTrapEventRecord(cpnId, location, ts, trapType);

        cpnTraps.add(t1);

        ESEventDTO e1 = new ESEventDTO();
        Integer onmsId = 1;
        e1.setId(onmsId);
        e1.setNodeLabel(host);
        e1.setTimestamp(new Date(ts.getTime() - 20000));
        setTrapTypeOid(e1, trapType);
        setIfDescrOid(e1, ifIndex, ifDescr);

        onmsTraps.add(e1);

        Map<String, Integer> matchedTraps = EventMatcher.matchTrapEventsScopedByTimeAndHost(cpnTraps, onmsTraps);
        assertThat(matchedTraps.get(cpnId), equalTo(onmsId));
    }

    @Test
    public void canNotMatchLinkDownTraps() {
        List<MatchingTrapEventRecord> cpnTraps = new ArrayList<>();
        List<ESEventDTO> onmsTraps = new ArrayList<>();

        Date ts = new Date();

        String cpnId = "101";
        String host = "testhost";
        int ifIndex = 436305920;
        String ifDescr = "Ethernet1/32";
        String location = String.format("%s: %s", host, ifDescr);

        String trapType = ".1.3.6.1.6.3.1.1.5.3";
        MatchingTrapEventRecord t1 = new ImplMatchingTrapEventRecord(cpnId, location, ts, trapType);

        cpnTraps.add(t1);

        ESEventDTO e1 = new ESEventDTO();
        Integer onmsId = 1;
        e1.setId(onmsId);
        e1.setNodeLabel(host);
        e1.setTimestamp(new Date(ts.getTime() - 20000));
        setTrapTypeOid(e1, trapType);
        setIfDescrOid(e1, ifIndex, ifDescr + "/0"); // NOT THE SAME AS THE CPN TRAP

        onmsTraps.add(e1);

        Map<String, Integer> matchedTraps = EventMatcher.matchTrapEventsScopedByTimeAndHost(cpnTraps, onmsTraps);
        assertThat(matchedTraps.containsKey(cpnId), equalTo(false));
    }

    @Test
    public void canNotMatchEventTwice() throws ExecutionException, InterruptedException {
        List<MatchingSyslogEventRecord> cpnSyslogs = new ArrayList<>();
        List<ESEventDTO> onmsSyslogs = new ArrayList<>();

        String syslogMsg1Header = "<188>123456: Jul 17 04:36:01.993: ";
        String syslogMsg1Body = "%CDP-4-NATIVE_VLAN_MISMATCH: Native VLAN mismatch " +
                "discovered on GigabitEthernet0/43 (503), with Switch GigabitEthernet1/0/24 (1).";
        String syslogMsg1 = syslogMsg1Header + syslogMsg1Body;
        String host1 = "testhost";

        String cpnEventId1 = "1";
        MatchingSyslogEventRecord s1 = new MatchingSyslogEventRecordImpl(cpnEventId1, syslogMsg1, host1);
        // Dupe of s1 with a different Id
        String cpnEventId2 = "2";
        MatchingSyslogEventRecord s2 = new MatchingSyslogEventRecordImpl(cpnEventId2, syslogMsg1, host1);


        ESEventDTO e1 = new ESEventDTO();
        Integer onmsEventId = 101;
        e1.setId(onmsEventId);
        e1.setNodeLabel(host1);
        e1.setSyslogMessage(syslogMsg1Body);
        e1.setTimestamp(SyslogParser.parse(syslogMsg1).getDate());

        cpnSyslogs.add(s1);
        cpnSyslogs.add(s2);

        onmsSyslogs.add(e1);

        Map<String, Integer> results = EventMatcher.matchSyslogEventsScopedByTimeAndHost(cpnSyslogs, onmsSyslogs);
        // only one should have matched since we don't allow matching the same Onms event to multiple cpn events
        assertThat(results.values(), hasSize(1));
    }

    private void setTrapTypeOid(ESEventDTO event, String trapTypeOid) {
        if (event.getP_oids() != null) {
            event.getP_oids().clear();
        }

        List<Map<String, String>> p_oids = new ArrayList<>();
        Map<String, String> trapType = new HashMap<>();
        trapType.put("oid", ".1.3.6.1.6.3.1.1.4.3.0");
        trapType.put("value", trapTypeOid);
        p_oids.add(trapType);

        event.setP_oids(p_oids);
    }

    private void setIfDescrOid(ESEventDTO event, int ifIndex, String ifDescr) {
        List<Map<String, String>> p_oids = event.getP_oids();
        Map<String, String> ifDescrVb = new HashMap<>();
        ifDescrVb.put("oid", ".1.3.6.1.2.1.2.2.1.2." + ifIndex);
        ifDescrVb.put("value", ifDescr);
        p_oids.add(ifDescrVb);
    }

    static class ImplMatchingTrapEventRecord implements MatchingTrapEventRecord {
        private final String id;
        private final String location;
        private final Date date;
        private final String trapTypeOid;

        public ImplMatchingTrapEventRecord(String id, String location, Date date,
                                           String trapTypeOid) {
            this.id = id;
            this.location = location;
            this.date = date;
            this.trapTypeOid = trapTypeOid;
        }

        @Override
        public String getTrapTypeOid() {
            return trapTypeOid;
        }

        @Override
        public Date getTime() {
            return date;
        }

        @Override
        public String getDetailedDescription() {
            return null;
        }

        @Override
        public String getEventId() {
            return id;
        }

        @Override
        public String getDescription() {
            return null;
        }

        @Override
        public String getLocation() {
            return location;
        }
    }

    static class MatchingSyslogEventRecordImpl implements MatchingSyslogEventRecord {
        private final String id;
        private final String detailedDescription;
        private final String location;

        public MatchingSyslogEventRecordImpl(String id, String detailedDescription, String location) {
            this.id = id;
            this.detailedDescription = detailedDescription;
            this.location = location;
        }

        @Override
        public Date getTime() {
            return null;
        }

        @Override
        public String getDetailedDescription() {
            return detailedDescription;
        }

        @Override
        public String getEventId() {
            return id;
        }

        @Override
        public String getDescription() {
            return null;
        }

        @Override
        public String getLocation() {
            return location;
        }
    }
}
