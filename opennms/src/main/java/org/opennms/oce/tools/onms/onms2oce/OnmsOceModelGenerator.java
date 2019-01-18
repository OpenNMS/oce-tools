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

import static java.util.stream.Collectors.groupingBy;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

import org.opennms.oce.datasource.common.inventory.ManagedObjectType;
import org.opennms.oce.datasource.v1.schema.Inventory;
import org.opennms.oce.datasource.v1.schema.MetaModel;
import org.opennms.oce.datasource.v1.schema.ModelObjectDef;
import org.opennms.oce.datasource.v1.schema.ModelObjectEntry;
import org.opennms.oce.datasource.v1.schema.ParentDefRef;
import org.opennms.oce.datasource.v1.schema.PeerDefRef;
import org.opennms.oce.datasource.v1.schema.PeerRef;
import org.opennms.oce.datasource.v1.schema.RelativeDefRef;
import org.opennms.oce.datasource.v1.schema.RelativeRef;
import org.opennms.oce.tools.cpn2oce.model.ModelObject;
import org.opennms.oce.tools.cpn2oce.model.ModelObjectType;
import org.opennms.oce.tools.tsaudit.OnmsAlarmSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

public class OnmsOceModelGenerator {
    private static final Logger LOG = LoggerFactory.getLogger(OnmsOceModelGenerator.class);
    public static final String MODEL_ROOT_TYPE = "Model";
    public static final String MODEL_ROOT_ID = "model";

    private final List<OnmsAlarmSummary> alarms;

    private MetaModel metaModel;
    private Inventory inventory;

    public OnmsOceModelGenerator(List<OnmsAlarmSummary> alarms) {
        this.alarms = Objects.requireNonNull(alarms);
    }

    public void generate(Integer nodeId, String nodeLabel) {
        // Generate and flatten the tree of MOs
        final Set<ModelObject> allMos = new LinkedHashSet<>();
        for (OnmsAlarmSummary alarm : alarms) {
            final ModelObject mo = getModelObject(alarm, nodeId.toString(), nodeLabel);
            if (mo != null) {
                allMos.add(mo);
            }
        }

        final Set<ModelObject> relatedMos = new LinkedHashSet<>();
        for (ModelObject mo : allMos) {
            traverse(mo, relatedMos);
        }
        allMos.addAll(relatedMos);

        final Set<ModelObjectDef> modelObjectDefs = new LinkedHashSet<>();

        // Build the model root element
        final ModelObjectDef rootModelDef = new ModelObjectDef();
        rootModelDef.setType(MODEL_ROOT_TYPE);
        final ParentDefRef rootModelParentDefRef = new ParentDefRef();
        rootModelParentDefRef.setType(MODEL_ROOT_TYPE);
        rootModelDef.getParentDefRef().add(rootModelParentDefRef);
        modelObjectDefs.add(rootModelDef);

        final Map<ModelObjectType, List<ModelObject>> allMosByType = allMos.stream()
                .collect(groupingBy(ModelObject::getType));

        metaModel = new MetaModel();
        for (Map.Entry<ModelObjectType, List<ModelObject>> entry : allMosByType.entrySet()) {
            final ModelObjectType type = entry.getKey();
            final ModelObjectDef def = new ModelObjectDef();
            def.setType(type.toString());

            final Set<ModelObjectType> parentTypes = new LinkedHashSet<>();
            final Set<ModelObjectType> peerTypes = new LinkedHashSet<>();
            final Set<ModelObjectType> uncleTypes = new LinkedHashSet<>();
            for (ModelObject mo : entry.getValue()) {
                if (mo.hasParent()) {
                    parentTypes.add(mo.getParent().getType());
                }
                for (ModelObject peer : mo.getPeers()) {
                    peerTypes.add(peer.getType());
                }
                for (ModelObject uncle : mo.getUncles()) {
                    uncleTypes.add(uncle.getType());
                }
            }

            if (parentTypes.size() > 0) {
                for (ModelObjectType parentType : parentTypes) {
                    ParentDefRef parentDefRef = new ParentDefRef();
                    parentDefRef.setType(parentType.toString());
                    def.getParentDefRef().add(parentDefRef);
                }
            } else {
                def.getParentDefRef().add(rootModelParentDefRef);
            }

            for (ModelObjectType peerType : peerTypes) {
                PeerDefRef peerDefRef = new PeerDefRef();
                peerDefRef.setType(peerType.toString());
                def.getPeerDefRef().add(peerDefRef);
            }

            for (ModelObjectType uncleType : uncleTypes) {
                RelativeDefRef relativeDefRef = new RelativeDefRef();
                relativeDefRef.setType(uncleType.toString());
                def.getRelativeDefRef().add(relativeDefRef);
            }

            modelObjectDefs.add(def);
        }

        // Build the meta-model
        modelObjectDefs.forEach(mo -> metaModel.getModelObjectDef().add(mo));

        inventory = new Inventory();

        // Add the root
        ModelObjectEntry modelRootEntry = new ModelObjectEntry();
        modelRootEntry.setType(MODEL_ROOT_TYPE);
        modelRootEntry.setId(MODEL_ROOT_ID);
        modelRootEntry.setParentType(MODEL_ROOT_TYPE);
        modelRootEntry.setParentId(MODEL_ROOT_ID);
        inventory.getModelObjectEntry().add(modelRootEntry);

        for (Map.Entry<ModelObjectType, List<ModelObject>> mosByType : allMosByType.entrySet()) {
            for (ModelObject mo : mosByType.getValue()) {
                ModelObjectEntry moe = new ModelObjectEntry();

                moe.setId(mo.getId());
                moe.setType(mo.getType().toString());
                if (mo.hasParent()) {
                    moe.setParentId(mo.getParent().getId());
                    moe.setParentType(mo.getParent().getType().toString());
                } else {
                    moe.setParentId(MODEL_ROOT_ID);
                    moe.setParentType(MODEL_ROOT_TYPE);
                }

                boolean firstPeer = true;
                for (ModelObject peer : mo.getPeers()) {
                    PeerRef peerRef = new PeerRef();
                    peerRef.setId(peer.getId());
                    peerRef.setEndpoint(firstPeer ? "A" : "Z");
                    peerRef.setType(peer.getType().toString());
                    moe.getPeerRef().add(peerRef);
                    if (firstPeer) {
                        firstPeer = false;
                    }
                }

                for (ModelObject uncle : mo.getUncles()) {
                    RelativeRef relativeRef = new RelativeRef();
                    relativeRef.setId(uncle.getId());
                    relativeRef.setType(uncle.getType().toString());
                    moe.getRelativeRef().add(relativeRef);
                }
                inventory.getModelObjectEntry().add(moe);
            }
        }
    }

    public MetaModel getMetaModel() {
        return metaModel;
    }

    public Inventory getInventory() {
        return inventory;
    }

    private static void traverse(ModelObject mo, Set<ModelObject> gather) {
        gather.add(mo);
        if (mo.hasParent()) {
            traverse(mo.getParent(), gather);
        }
        for (ModelObject peer : mo.getPeers()) {
            traverse(peer, gather);
        }
        for (ModelObject nephew : mo.getNephews()) {
            traverse(nephew, gather);
        }
    }

    public static ModelObject getModelObject(OnmsAlarmSummary alarm, String nodeId, String nodeLabel) {
        // Only derive inventory if the alarm has an MO type and instance
        if (Strings.isNullOrEmpty(alarm.getManagedObjectType()) || Strings.isNullOrEmpty(alarm.getManagedObjectInstance())) {
            return null;
        }

        ManagedObjectType type;

        try {
            type = ManagedObjectType.fromName(alarm.getManagedObjectType());
        } catch (NoSuchElementException nse) {
            LOG.warn("Found unsupported type: {} with id: {}. Skipping.", alarm.getManagedObjectType(), alarm.getManagedObjectInstance());
            return null;
        }

        ModelObject node = new ModelObject(nodeId, nodeLabel, ModelObjectType.DEVICE);

        switch (type) {

        case SnmpInterface:
            return new ModelObject(alarm.getManagedObjectInstance(), alarm.getManagedObjectInstance(), ModelObjectType.PORT, node);

        case Node:
            return node;

        case EntPhysicalEntity:
            // FIXME - hack
            return new ModelObject(nodeId, nodeLabel, ModelObjectType.FAN_TRAY, node);

        case BgpPeer:
            return new ModelObject(nodeId, nodeLabel, ModelObjectType.BGP_PEER);

        default:
            return null;
        }
    }

}
