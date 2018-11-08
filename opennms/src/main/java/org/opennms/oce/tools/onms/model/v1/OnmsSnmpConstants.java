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

package org.opennms.oce.tools.onms.model.v1;

import org.snmp4j.smi.OID;

public class OnmsSnmpConstants {

    public static final OID authAddr = new OID(".1.3.6.1.4.1.9.2.1.5.0");
    public static final OID authenticationFailure = new OID(".1.3.6.1.6.3.1.1.5.5");

    public static final OID ifIndex = new OID(".1.3.6.1.2.1.2.2.1.1");
    public static final OID ifAdminStatus = new OID(".1.3.6.1.2.1.2.2.1.7"); // up(1),down(2),testing(3)
    public static final OID ifOperStatus = new OID(".1.3.6.1.2.1.2.2.1.8"); // up(1),down(2),testing(3)
    public static final OID ifAlias = new OID(".1.3.6.1.2.1.31.1.1.1.18");
    public static final OID ifDescr = new OID(".1.3.6.1.2.1.2.2.1.2");
    public static final OID ifName = new OID(".1.3.6.1.2.1.31.1.1.1.2");

    public static final OID linkUp = new OID(".1.3.6.1.6.3.1.1.5.4");
    public static final OID linkDown = new OID(".1.3.6.1.6.3.1.1.5.3");

    public static final OID coldStart = new OID(".1.3.6.1.6.3.1.1.5.1");
    public static final OID warmStart = new OID(".1.3.6.1.6.3.1.1.5.2");

    public static final OID rfc1657_bgpTraps = new OID(".1.3.6.1.2.1.15.7");
    public static final OID rfc1657_bgpEstablished = new OID(".1.3.6.1.2.1.15.7.1");
    public static final OID rfc1657_bgpBackwardTransition = new OID(".1.3.6.1.2.1.15.7.2");

    public static final OID rfc1269_bgpTraps = new OID(".1.3.6.1.2.1.15");
    public static final OID rfc1269_bgpEstablished = new OID(".1.3.6.1.2.1.15.1");
    public static final OID rfc1269_bgpBackwardTransition = new OID(".1.3.6.1.2.1.15.2");

    public static final OID bgpPeerState = new OID(".1.3.6.1.2.1.15.3.1.2");
    public static final OID bgpPeerRemoteAddr = new OID("1.3.6.1.2.1.15.3.1.7");
    public static final OID bgpPeerLastError = new OID(".1.3.6.1.2.1.15.3.1.14");

    public static final OID cbgpFsmStateChange = new OID(".1.3.6.1.4.1.9.9.187.0.1");
    public static final OID cbgpBackwardTransition = new OID(".1.3.6.1.4.1.9.9.187.0.2");
    public static final OID cbgpPeerLastErrorTxt = new OID(".1.3.6.1.4.1.9.9.187.1.2.1.1.7");
    public static final OID cbgpPeerPrevState = new OID(".1.3.6.1.4.1.9.9.187.1.2.1.1.8");

    public static final OID cikeTunnelStart = new OID(".1.3.6.1.4.1.9.9.171.2.0.1");
    public static final OID cikeTunnelStop = new OID(".1.3.6.1.4.1.9.9.171.2.0.2");
    public static final OID cikePeerLocalAddr = new OID(".1.3.6.1.4.1.9.9.171.1.2.2.1.6");
    public static final OID cikePeerRemoteAddr = new OID(".1.3.6.1.4.1.9.9.171.1.2.2.1.7");
    public static final OID cikeTunLifeTime = new OID(".1.3.6.1.4.1.9.9.171.1.2.3.1.15");
    public static final OID cikeTunHistTermReason = new OID(".1.3.6.1.4.1.9.9.171.1.4.2.1.1.2");

    public static final OID dsx1LineStatusChange = new OID(".1.3.6.1.2.1.10.18.15.0.1");
    public static final OID dsx1LineStatus = new OID(".1.3.6.1.2.1.10.18.6.1.10");

    public static final OID bridgeMibTopologyChange = new OID(".1.3.6.1.2.1.17.0.2");

    public static final OID ciscoIetfBfdMIB = new OID(".1.3.6.1.4.1.9.10.137");
    public static final OID ciscoBfdSessUp = new OID(".1.3.6.1.4.1.9.10.137.0.1");
    public static final OID ciscoBfdSessDown = new OID(".1.3.6.1.4.1.9.10.137.0.2");
    public static final OID ciscoBfdSessDiag = new OID(".1.3.6.1.4.1.9.10.137.1.2.1.8");

    public static final OID mplsL3VpnMIB = new OID(".1.3.6.1.2.1.10.166.11");
    public static final OID mplsL3VpnVrfUp = new OID(".1.3.6.1.2.1.10.166.11.0.1");
    public static final OID mplsL3VpnVrfDown = new OID(".1.3.6.1.2.1.10.166.11.0.2");
    public static final OID mplsL3VpnIfConfRowStatus = new OID(".1.3.6.1.2.1.10.166.11.1.2.1.1.5");
    public static final OID mplsL3VpnVrfOperStatus = new OID(".1.3.6.1.2.1.10.166.11.1.2.2.1.6");

    public static final OID ospfTraps = new OID(".1.3.6.1.2.1.14.16.2");
    public static final OID ospfNbrStateChange = new OID(".1.3.6.1.2.1.14.16.2.2");
    public static final OID ospfIfAuthFailure = new OID(".1.3.6.1.2.1.14.16.2.6");
    public static final OID ospfTxRetransmit = new OID(".1.3.6.1.2.1.14.16.2.10");
    public static final OID ospfOriginateLsa = new OID("1.3.6.1.2.1.14.16.2.12");
    public static final OID ospfMaxAgeLsa = new OID("1.3.6.1.2.1.14.16.2.13");
    public static final OID ospfIfStateChange = new OID(".1.3.6.1.2.1.14.16.2.16");

    public static final OID ospfRouterId = new OID(".1.3.6.1.2.1.14.1.1");
    public static final OID ospfIfIpAddress = new OID(".1.3.6.1.2.1.14.7.1.1");
    public static final OID ospfAddressLessIf = new OID(".1.3.6.1.2.1.14.7.1.2");
    public static final OID ospfNbrRtrId = new OID(".1.3.6.1.2.1.14.10.1.3");
    public static final OID ospfPacketType = new OID(".1.3.6.1.2.1.14.16.1.3");
    public static final OID ospfLsdbType = new OID(".1.3.6.1.2.1.14.4.1.2");
    public static final OID ospfLsdbLsid = new OID(".1.3.6.1.2.1.14.4.1.3");
    public static final OID ospfLsdbRouterId = new OID(".1.3.6.1.2.1.14.4.1.4");
    public static final OID ospfLsdbAreaId = new OID(".1.3.6.1.2.1.14.4.1.1");
    public static final OID ospfNbrIpAddr = new OID(".1.3.6.1.2.1.14.10.1.1");
    public static final OID ospfNbrAddressLessIndex = new OID(".1.3.6.1.2.1.14.10.1.2");
    public static final OID ospfNbrState = new OID(".1.3.6.1.2.1.14.10.1.6");
    public static final OID ospfPacketSrc = new OID(".1.3.6.1.2.1.14.16.1.4");
    public static final OID ospfConfigErrorType = new OID(".1.3.6.1.2.1.14.16.1.2");
    public static final OID ospfIfState = new OID(".1.3.6.1.2.1.14.7.1.12");

    public static final OID entityMIBTraps = new OID(".1.3.6.1.2.1.47.2");
    public static final OID entConfigChange = new OID(".1.3.6.1.2.1.47.2.0.1");
    public static final OID entLastChangeTime = new OID(".1.3.6.1.2.1.47.1.4.1");

    public static final OID ciscoSonetMIB = new OID(".1.3.6.1.4.1.9.9.126");
    public static final OID ciscoSonetSectionStatusChange = new OID(".1.3.6.1.4.1.9.9.126.0.1");
    public static final OID ciscoSonetLineStatusChange = new OID(".1.3.6.1.4.1.9.9.126.0.2");
    public static final OID ciscoSonetPathStatusChange = new OID(".1.3.6.1.4.1.9.9.126.0.3");
    public static final OID sonetSectionCurrentStatus = new OID(".1.3.6.1.2.1.10.39.1.2.1.1.1");

    public static final OID cefcFRUMIBNotificationPrefix = new OID(".1.3.6.1.4.1.9.9.117.2");
    public static final OID cefcModuleStatusChange = new OID(".1.3.6.1.4.1.9.9.117.2.0.1");
    public static final OID cefcPowerStatusChange = new OID(".1.3.6.1.4.1.9.9.117.2.0.2");
    public static final OID cefcFRUInserted = new OID(".1.3.6.1.4.1.9.9.117.2.0.3");
    public static final OID cefcFRURemoved = new OID(".1.3.6.1.4.1.9.9.117.2.0.4");
    public static final OID cefcFanTrayStatusChange = new OID(".1.3.6.1.4.1.9.9.117.2.0.6");

    public static final OID cefcFRUPowerAdminStatus = new OID(".1.3.6.1.4.1.9.9.117.1.1.2.1.1");
    public static final OID cefcFRUPowerOperStatus = new OID(".1.3.6.1.4.1.9.9.117.1.1.2.1.2");
    public static final OID cefcModuleStatusLastChangeTime = new OID(".1.3.6.1.4.1.9.9.117.1.2.1.1.4");
    public static final OID cefcModuleOperStatus = new OID(".1.3.6.1.4.1.9.9.117.1.2.1.1.2");
    public static final OID cefcFanTrayOperStatus = new OID(".1.3.6.1.4.1.9.9.117.1.4.1.1.1");
    public static final OID entPhysicalDescr = new OID(".1.3.6.1.2.1.47.1.1.1.1.2");
    public static final OID entPhysicalContainedIn = new OID(".1.3.6.1.2.1.47.1.1.1.1.4");
    public static final OID entPhysicalName = new OID(".1.3.6.1.2.1.47.1.1.1.1.7");

    public static final OID entitySensorMIBNotificationPrefix = new OID(".1.3.6.1.4.1.9.9.91.2");
    public static final OID entSensorThresholdNotification = new OID(".1.3.6.1.4.1.9.9.91.2.0.1");
    public static final OID entSensorThresholdValue = new OID(".1.3.6.1.4.1.9.9.91.1.2.1.1.4");
    public static final OID entSensorValue = new OID(".1.3.6.1.4.1.9.9.91.1.1.1.1.4");

    public static final OID ds3Traps = new OID(".1.3.6.1.2.1.10.30.15");
    public static final OID dsx3LineStatusChange = new OID(".1.3.6.1.2.1.10.30.15.0.1");
    public static final OID dsx3LineStatus = new OID(".1.3.6.1.2.1.10.30.5.1.10");

    public static final OID mplsTeStdMIB = new OID(".1.3.6.1.2.1.10.166.3");
    public static final OID mplsTunnelUp = new OID(".1.3.6.1.2.1.10.166.3.0.1");
    public static final OID mplsTunnelDown = new OID(".1.3.6.1.2.1.10.166.3.0.2");
    public static final OID mplsTunnelRerouted = new OID(".1.3.6.1.2.1.10.166.3.0.3");
    public static final OID mplsTunnelReoptimized = new OID(".1.3.6.1.2.1.10.166.3.0.4");
    public static final OID mplsTunnelAdminStatus = new OID(".1.3.6.1.2.1.10.166.3.2.2.1.34");
    public static final OID mplsTunnelOperStatus = new OID(".1.3.6.1.2.1.10.166.3.2.2.1.35");

    public static final OID mplsLdpStdMIB = new OID(".1.3.6.1.2.1.10.166.4");
    public static final OID mplsLdpSessionUp = new OID(".1.3.6.1.2.1.10.166.4.0.3");
    public static final OID mplsLdpSessionDown = new OID(".1.3.6.1.2.1.10.166.4.0.4");
    public static final OID mplsLdpSessionState = new OID(".1.3.6.1.4.1.9.10.65.1.3.3.2.1.1");
    public static final OID mplsLdpSessionDiscontinuityTime = new OID(".1.3.6.1.2.1.10.166.4.1.3.3.1.8");
    public static final OID mplsLdpSessionStatsUnknownMesTypeErrors = new OID(".1.3.6.1.2.1.10.166.4.1.3.4.1.1");
    public static final OID mplsLdpSessionStatsUnknownTlvErrors = new OID(".1.3.6.1.2.1.10.166.4.1.3.4.1.2");

    public static final OID cmplsFrrMIB = new OID(".1.3.6.1.4.1.9.10.98");
    public static final OID cmplsFrrProtected = new OID(".1.3.6.1.4.1.9.10.98.0.1");
    public static final OID cmplsFrrUnProtected = new OID(".1.3.6.1.4.1.9.10.98.0.2");
    public static final OID cmplsFrrConstNumProtectingTunOnIf = new OID(".1.3.6.1.4.1.9.10.98.2.1.1.1.12");
    public static final OID cmplsFrrConstNumProtectedTunOnIf = new OID(".1.3.6.1.4.1.9.10.98.2.1.1.1.13");
    public static final OID cmplsFrrConstBandwidth = new OID(".1.3.6.1.4.1.9.10.98.2.1.1.1.10");

    public static final OID ciscoIetfPimExtMIB = new OID(".1.3.6.1.4.1.9.10.120");
    public static final OID ciscoIetfPimExtInterfaceUp = new OID(".1.3.6.1.4.1.9.10.120.0.1");
    public static final OID ciscoIetfPimExtInterfaceDown = new OID(".1.3.6.1.4.1.9.10.120.0.2");
    public static final OID cPimIfStatus = new OID(".1.3.6.1.4.1.9.10.119.1.1.2.1.11");

    public static final OID ciscoBgp4MIB = new OID(".1.3.6.1.4.1.9.9.187");
    public static final OID cbgpPrefixThresholdExceeded = new OID(".1.3.6.1.4.1.9.9.187.0.3");
    public static final OID cbgpPrefixThresholdClear = new OID(".1.3.6.1.4.1.9.9.187.0.4");
    public static final OID cbgpPeerPrefixAdminLimit = new OID(".1.3.6.1.4.1.9.9.187.1.2.4.1.3");
    public static final OID cbgpPeerPrefixThreshold = new OID(".1.3.6.1.4.1.9.9.187.1.2.4.1.4");
    public static final OID cbgpPeerPrefixClearThreshold = new OID(".1.3.6.1.4.1.9.9.187.1.2.4.1.5");

    public static final OID dot1dBridge = new OID(".1.3.6.1.2.1.17");
    public static final OID newRoot = new OID(".1.3.6.1.2.1.17.0.1");

    public static final OID ciscoRFMIBNotificationsPrefix = new OID(".1.3.6.1.4.1.9.9.176.2");
    public static final OID ciscoRFSwactNotif = new OID(".1.3.6.1.4.1.9.9.176.2.0.1");
    public static final OID ciscoRFProgressionNotif = new OID(".1.3.6.1.4.1.9.9.176.2.0.2");
    public static final OID cRFStatusUnitId = new OID(".1.3.6.1.4.1.9.9.176.1.1.1");
    public static final OID cRFStatusUnitState = new OID(".1.3.6.1.4.1.9.9.176.1.1.2");
    public static final OID cRFStatusPeerUnitId = new OID(".1.3.6.1.4.1.9.9.176.1.1.3");
    public static final OID cRFStatusPeerUnitState = new OID(".1.3.6.1.4.1.9.9.176.1.1.4");
    public static final OID cRFStatusLastSwactReasonCode = new OID(".1.3.6.1.4.1.9.9.176.1.1.8");

    public static final OID ciscoFlashMIBTrapPrefix = new OID(".1.3.6.1.4.1.9.9.10.1.3");
    public static final OID ciscoFlashDeviceChangeTrap = new OID(".1.3.6.1.4.1.9.9.10.1.3.0.4");
    public static final OID ciscoFlashDeviceInsertedNotif = new OID(".1.3.6.1.4.1.9.9.10.1.3.0.5");
    public static final OID ciscoFlashDeviceRemovedNotif = new OID(".1.3.6.1.4.1.9.9.10.1.3.0.6");
    public static final OID ciscoFlashDeviceMinPartitionSize = new OID(".1.3.6.1.4.1.9.9.10.1.1.2.1.3");
    public static final OID ciscoFlashDeviceName = new OID(".1.3.6.1.4.1.9.9.10.1.1.2.1.7");

    public static final OID mplsLdpNotifications = new OID(".1.3.6.1.4.1.9.10.65.2");
    public static final OID mplsLdpMibMplsLdpSessionUp = new OID(".1.3.6.1.4.1.9.10.65.2.0.3");
    public static final OID mplsLdpMibMplsLdpSessionDown = new OID(".1.3.6.1.4.1.9.10.65.2.0.4");
    public static final OID mplsLdpMibMplsLdpSessionState = new OID(".1.3.6.1.4.1.9.10.65.1.3.3.2.1.1");

    public static final OID ciscoOtnIfMIB = new OID(".1.3.6.1.4.1.9.9.639");
    public static final OID coiOtnIfOTUStatusChange = new OID(".1.3.6.1.4.1.9.9.639.0.1");
    public static final OID coiOtnIfODUStatusChange = new OID(".1.3.6.1.4.1.9.9.639.0.2");
    public static final OID coiOtnIfOTUStatus = new OID(".1.3.6.1.4.1.9.9.639.1.1.1.1.16");

    public static final OID cAAAServerMIBNotificationPrefix = new OID(".1.3.6.1.4.1.9.10.56.2");
    public static final OID casServerStateChange = new OID(".1.3.6.1.4.1.9.10.56.2.0.1");
    public static final OID casState = new OID(".1.3.6.1.4.1.9.10.56.1.2.1.1.25");
    public static final OID casPreviousStateDuration = new OID(".1.3.6.1.4.1.9.10.56.1.2.1.1.27");
    public static final OID casTotalDeadTime = new OID(".1.3.6.1.4.1.9.10.56.1.2.1.1.28");

    public static final OID ciscoNtpMIB = new OID(".1.3.6.1.4.1.9.9.168");
    public static final OID ciscoNtpSrvStatusChange = new OID(".1.3.6.1.4.1.9.9.168.0.1");
    public static final OID cntpSysSrvStatus = new OID(".1.3.6.1.4.1.9.9.168.1.1.11");

    public static final OID vtpNotifications = new OID(".1.3.6.1.4.1.9.9.46.2");
    public static final OID vlanTrunkPortDynamicStatusChange = new OID(".1.3.6.1.4.1.9.9.46.2.0.7");
    public static final OID vlanTrunkPortDynamicStatus = new OID(".1.3.6.1.4.1.9.9.46.1.6.1.1.14");

    public static final OID rfc1315FrameRelay = new OID(".1.3.6.1.2.1.10.32");
    public static final OID frDLCIStatusChange = new OID(".1.3.6.1.2.1.10.32.0.1");
    public static final OID frCircuitIfIndex = new OID(".1.3.6.1.2.1.10.32.2.1.1");
    public static final OID frCircuitDlci = new OID(".1.3.6.1.2.1.10.32.2.1.2");
    public static final OID frCircuitState = new OID(".1.3.6.1.2.1.10.32.2.1.3");

    public static final OID entityStateMIB = new OID(".1.3.6.1.2.1.131");
    public static final OID entStateOperEnabled = new OID(".1.3.6.1.2.1.131.0.1");
    public static final OID entStateOperDisabled = new OID(".1.3.6.1.2.1.131.0.2");
    public static final OID entStateAdmin = new OID(".1.3.6.1.2.1.131.1.1.1.2");
    public static final OID entStateAlarm = new OID(".1.3.6.1.2.1.131.1.1.1.5");

    public static final OID ciscoEnvMonMIBNotificationPrefix = new OID(".1.3.6.1.4.1.9.9.13.3");
    public static final OID ciscoEnvMonTempStatusChangeNotif = new OID(".1.3.6.1.4.1.9.9.13.3.0.7");
    public static final OID ciscoEnvMonTemperatureStatusDescr = new OID(".1.3.6.1.4.1.9.9.13.1.3.1.2");
    public static final OID ciscoEnvMonTemperatureStatusValue = new OID(".1.3.6.1.4.1.9.9.13.1.3.1.3");
    public static final OID ciscoEnvMonTemperatureState = new OID(".1.3.6.1.4.1.9.9.13.1.3.1.6");

    public static final OID rttMonNotificationsPrefix = new OID(".1.3.6.1.4.1.9.9.42.2");
    public static final OID rttMonNotification = new OID(".1.3.6.1.4.1.9.9.42.2.0.5");
    public static final OID rttMonCtrlAdminTag = new OID(".1.3.6.1.4.1.9.9.42.1.2.1.1.3");
    public static final OID rttMonHistoryCollectionAddress = new OID(".1.3.6.1.4.1.9.9.42.1.4.1.1.5");
    public static final OID rttMonReactVar = new OID(".1.3.6.1.4.1.9.9.42.1.2.19.1.2");
    public static final OID rttMonReactOccurred = new OID(".1.3.6.1.4.1.9.9.42.1.2.19.1.10");
    public static final OID rttMonReactValue = new OID(".1.3.6.1.4.1.9.9.42.1.2.19.1.9");
    public static final OID rttMonReactThresholdRising = new OID(".1.3.6.1.4.1.9.9.42.1.2.19.1.5");
    public static final OID rttMonReactThresholdFalling = new OID(".1.3.6.1.4.1.9.9.42.1.2.19.1.6");
    public static final OID rttMonEchoAdminLSPSelector = new OID(".1.3.6.1.4.1.9.9.42.1.2.2.1.33");

}
