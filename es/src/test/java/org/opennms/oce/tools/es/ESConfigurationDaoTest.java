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

package org.opennms.oce.tools.es;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.io.File;

import org.junit.Test;

public class ESConfigurationDaoTest {

    @Test
    public void canParseConfig() {
        ClassLoader classLoader = getClass().getClassLoader();
        File configFile = new File(classLoader.getResource("es-config.yaml").getFile());
        ESConfigurationDao dao = new ESConfigurationDao(configFile);
        ESConfiguration config = dao.getConfig();

        assertThat(config.getClusters().keySet(), hasSize(1));
        ESClusterConfiguration clusterConfig = config.getFirstCluster();
        assertThat(clusterConfig.getUrl(), equalTo("http://localhost:9200"));
        assertThat(clusterConfig.getReadTimeout(), equalTo(120000));
        assertThat(clusterConfig.getConnTimeout(), equalTo(30000));

        assertThat(clusterConfig.getOpennmsEventIndex(), equalTo("opennms-events-raw-*"));
        assertThat(clusterConfig.getOpennmsAlarmIndex(), equalTo("opennms-alarms-*"));
    }
}
