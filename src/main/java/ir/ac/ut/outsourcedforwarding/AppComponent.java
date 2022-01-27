/*
 * Copyright 2022-present Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ir.ac.ut.outsourcedforwarding;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.onlab.packet.Ethernet;
import org.onlab.packet.VlanId;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.host.HostService;
import org.onosproject.net.packet.*;
import org.onosproject.net.topology.*;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Set;


@Component(immediate = true)
public class AppComponent {
    private ApplicationId appId;

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    protected TopologyService topologyService;

    protected HostService hostService;


    private OutsourcedForwardingPacketProcessor processor = new OutsourcedForwardingPacketProcessor();


    @Activate
    protected void activate() {
        packetService.addProcessor(processor, PacketProcessor.director(2));
        requestIntercepts();
        appId = coreService.registerApplication("ir.ac.ut.outsourcedforwarding");

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        packetService.removeProcessor(processor);

        log.info("Stopped");
    }

    private void requestIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
        selector.matchEthType(Ethernet.TYPE_IPV6);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }

    private class OutsourcedForwardingPacketProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            // Stop processing if the packet has been handled, since we
            // can't do any more to it.
            if (context.isHandled()) {
                return;
            }

            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();
            if (ethPkt == null) {
                return;
            }

            HostId dstId = HostId.hostId(ethPkt.getDestinationMAC(), VlanId.vlanId(ethPkt.getVlanID()));
            Host dst = hostService.getHost(dstId);

            // send data to an http server
            Topology topology = topologyService.currentTopology();
            String srcDeviceName = pkt.receivedFrom().deviceId().toString();
            String dstDeviceName = dst.location().deviceId().toString();
            TopologyGraph graph = topologyService.getGraph(topology);
            Set<TopologyEdge> edges = graph.getEdges();
            Set<TopologyVertex> vertices = graph.getVertexes();

            StringBuilder verticesOutput = new StringBuilder();
            for (TopologyVertex tv : vertices) {
                String verticeDeviceId = tv.deviceId().toString();
                verticesOutput.append(verticeDeviceId).append("|");
            }

            StringBuilder edgesOutput = new StringBuilder();
            for (TopologyEdge te : edges) {
                String srcDeviceId = te.link().src().deviceId().toString();
                String dstDeviceId = te.link().dst().deviceId().toString();
                edgesOutput.append(srcDeviceId).append("-").append(dstDeviceId).append("|");
            }

            StringBuilder output = new StringBuilder();
            output.append(srcDeviceName).append("\n").append(dstDeviceName).append("\n");
            output.append(verticesOutput.toString()).append("\n").append(edgesOutput.toString());

            HttpURLConnection connection = null;
            try {
                URL url = new URL("http://0.0.0.0:7000");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);

                DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
                wr.writeBytes(output.toString());
                wr.flush();
                wr.close();
                int status = connection.getResponseCode();
            } catch (Exception e) {
                log.info(e.toString());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
    }
}
