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

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import io.searchbox.client.JestClient;
import io.searchbox.client.JestClientFactory;
import io.searchbox.client.config.HttpClientConfig;
import io.searchbox.core.Bulk;
import io.searchbox.core.Index;

public class ESClient {

    private static final Logger LOG = LoggerFactory.getLogger(ESClient.class);

    private final ESClusterConfiguration clusterConfiguration;
    private final JestClient jestClient;

    public ESClient(ESClusterConfiguration clusterConfiguration) {
        this.clusterConfiguration = Objects.requireNonNull(clusterConfiguration);

        JestClientFactory factory = new JestClientFactory();
        factory.setHttpClientConfig(new HttpClientConfig
                .Builder(clusterConfiguration.getUrl())
                .multiThreaded(true)
                .readTimeout(clusterConfiguration.getReadTimeout())
                .connTimeout(clusterConfiguration.getConnTimeout())
                .build());
        jestClient = factory.getObject();
    }

    public <T> void bulkIndex(List<T> records, String index, String type, Function<T,String> idProvider) throws IOException {
        LOG.debug("Inserting {} records into index '{}'.", records.size(), index);
        int numRecordsRemaining = records.size();
        for (List<T> recordPartition : Lists.partition(records, 1000)) {
            numRecordsRemaining -= recordPartition.size();
            LOG.debug("Inserting batch of {} records into index '{}'. {} records remaining.", recordPartition.size(), index, numRecordsRemaining);
            final Bulk.Builder builder = new Bulk.Builder();
            for (T record : recordPartition) {
                builder.addAction(new Index.Builder(record).index(index).type(type).id(idProvider.apply(record)).build());
            }
            jestClient.execute(builder.build());
        }
        LOG.debug("Done inserting into index '{}'.", index);
    }

    public JestClient getJestClient() {
        return jestClient;
    }

    public ESClusterConfiguration getClusterConfiguration() {
        return clusterConfiguration;
    }
}
