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

package org.opennms.oce.tools.dsmapping;

import java.io.File;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.junit.Test;
import org.opennms.oce.datasource.v1.schema.Alarms;
import org.opennms.oce.datasource.v1.schema.Inventory;
import org.opennms.oce.datasource.v1.schema.MetaModel;
import org.opennms.oce.datasource.v1.schema.Situations;

public class DSMApperTest {
    @Test
    public void test() throws JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance(MetaModel.class, Inventory.class, Alarms.class, Situations
                .class);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        Alarms cpnAlarms = (Alarms) unmarshaller.unmarshal(new File("/cpndata/testCpn/cpn.alarms.xml"));
        Alarms onmsAlarms = (Alarms) unmarshaller.unmarshal(new File("/cpndata/testOnms/onms.alarms.xml"));
//        System.out.println(DSMapper.mapAlarms(cpnAlarms, onmsAlarms));
//        System.out.println(DSMapper.mapEventsToAlarms(cpnAlarms));
    }
}
