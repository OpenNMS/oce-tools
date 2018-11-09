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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;

import java.util.List;

import org.junit.Test;
import org.opennms.oce.tools.cpn2oce.model.ModelObject;
import org.opennms.oce.tools.cpn2oce.model.ModelObjectKey;

public class OceModelObjetKeyMapperTest {
    private EventMapper mapper = new EventMapper();

    @Test
    public void canGetRelatedObjectIds() {
        ModelObject mo = mapper.parse("BFD connectivity down", "agg-red: BundleEthernet1.3509<->agg-blue: BundleEthernet1.3509");
        List<ModelObjectKey> keys = OceModelObjetKeyMapper.getRelatedObjectIds(mo);

        assertThat(keys, containsInAnyOrder(
                new ModelObjectKey("DEVICE,agg-red","PORT,agg-red: BundleEthernet1.3509","LINK,agg-red: BundleEthernet1.3509<->agg-blue: BundleEthernet1.3509" ),
                new ModelObjectKey("DEVICE,agg-blue","PORT,agg-blue: BundleEthernet1.3509","LINK,agg-red: BundleEthernet1.3509<->agg-blue: BundleEthernet1.3509")
        ));

        mo = mapper.parse("CPU utilization exceeded upper threshold", "infra-blue");
        keys = OceModelObjetKeyMapper.getRelatedObjectIds(mo);
        assertThat(keys, contains(
                new ModelObjectKey("DEVICE,infra-blue", "CPU,CPU on infra-blue")
        ));

    }
}