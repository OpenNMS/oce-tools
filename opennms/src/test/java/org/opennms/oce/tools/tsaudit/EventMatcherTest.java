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
import static org.hamcrest.CoreMatchers.is;
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
    public void testMatchingSyslogs() throws ExecutionException, InterruptedException {
        List<MatchingSyslogEventRecord> cpnSyslogs = new ArrayList<>();
        List<ESEventDTO> onmsSyslogs = new ArrayList<>();

        String syslogMsg1Header = "<188>123456: Jul 17 04:36:01.993: ";
        String syslogMsg1Body = "%CDP-4-NATIVE_VLAN_MISMATCH: Native VLAN mismatch " +
                "discovered on GigabitEthernet0/43 (503), with Switch GigabitEthernet1/0/24 (1).";
        String syslogMsg1 = syslogMsg1Header + syslogMsg1Body;
        String host1 = "testhost";

        String cpnEventId = "1";
        MatchingSyslogEventRecord s1 = new MatchingSyslogEventRecordImpl(cpnEventId, syslogMsg1, host1);

        ESEventDTO e1 = new ESEventDTO();
        Integer onmsEventId = 101;
        e1.setId(onmsEventId);
        e1.setNodeLabel(host1);
        e1.setSyslogMessage(syslogMsg1Body);
        e1.setTimestamp(SyslogParser.parse(syslogMsg1).getDate());

        cpnSyslogs.add(s1);
        onmsSyslogs.add(e1);

        Map<String, Integer> results = EventMatcher.matchSyslogEvents(cpnSyslogs, onmsSyslogs);
        assertThat(results.get(cpnEventId), is(equalTo(onmsEventId)));
        e1.setNodeLabel("fail");
        results = EventMatcher.matchSyslogEvents(cpnSyslogs, onmsSyslogs);
        assertThat(results.keySet(), hasSize(0));
    }

    @Test
    public void testMatchingTraps() {
        List<MatchingTrapEventRecord> cpnTraps = new ArrayList<>();
        List<ESEventDTO> onmsTraps = new ArrayList<>();

        Date ts = new Date();

        String cpnId = "101";
        String host = "testhost";
        String trapType = ".1.2.3";
        MatchingTrapEventRecord t1 = new ImplMatchingTrapEventRecord(cpnId, host, ts, trapType);

        ESEventDTO e1 = new ESEventDTO();
        Integer onmsId = 1;
        e1.setId(onmsId);
        e1.setNodeLabel(host);
        e1.setTimestamp(ts);
        setTrapTypeOid(e1, trapType);

        cpnTraps.add(t1);
        onmsTraps.add(e1);

        Map<String, Integer> results = EventMatcher.matchTrapEvents(cpnTraps, onmsTraps);
        assertThat(results.get(cpnId), is(equalTo(onmsId)));
        e1.setNodeLabel("fail");
        results = EventMatcher.matchTrapEvents(cpnTraps, onmsTraps);
        assertThat(results.keySet(), hasSize(0));
        e1.setNodeLabel("host");
        setTrapTypeOid(e1, ".4.5.6");
        assertThat(results.keySet(), hasSize(0));
    }

    private void setTrapTypeOid(ESEventDTO event, String trapTypeOid) {
        if (event.getP_oids() != null) {
            event.getP_oids().clear();
        }
        List<Map<String, String>> p_oids = new ArrayList<>();
        Map<String, String> trapType = new HashMap<>();
        trapType.put(".1.3.6.1.6.3.1.1.4.3.0", trapTypeOid);
        p_oids.add(trapType);

        event.setP_oids(p_oids);
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
        public String getDetailedDescription() {
            return detailedDescription;
        }

        @Override
        public String getEventId() {
            return id;
        }

        @Override
        public String getLocation() {
            return location;
        }
    }
}
