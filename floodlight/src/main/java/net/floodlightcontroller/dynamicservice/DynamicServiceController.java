package net.floodlightcontroller.dynamicservice;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.naming.InsufficientResourcesException;

import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFFlowDelete;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActionSetField;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxms;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.ICMP;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.util.FlowModUtils;

/*
 * Main controller
 */
public class DynamicServiceController implements DynamicServiceInterface, IOFMessageListener, IFloodlightModule{
	protected static final Logger logger = LoggerFactory.getLogger(DynamicServiceController.class);
	
	protected IFloodlightProviderService floodlightProvider;
	protected IRestApiService restApiService;
	protected IOFSwitchService switchService;
	
	//list of available unused virtual ip for services
	protected PoolManager pool = new PoolManager()
			.add(IPv4Address.of("10.10.10.0").getInt(), IPv4Address.of("10.10.10.2").getInt());
	protected final static MacAddress VIRTUAL_MAC = MacAddress.of("00:00:00:00:00:fe");
	
	
	public static final int APPLICATION_ID = 47;
	
	protected ServiceManager serviceManager = new ServiceManager();
	
	
	/*
	* function to remove a route for the switches that implement association
	* between a client to a service, if the client is null remove all routes
	* (the service become unreachable)
	*/
	public void updateSwitches(ServiceData service, ClientData client) {
		
		// if client is null removes all the route associated with the application id
		int userID = (client == null) ? 0 : client.getIP().getInt();
		U64 mask = U64.of((client == null) ? 0xFFF0000000000000L : 0xFFFFFFFFFFFFFFFFL);
		
		Iterable<DatapathId> switches = service.getSwitches();
		for(DatapathId id : switches) {
			IOFSwitch sw = switchService.getActiveSwitch(id);
			
			if(sw == null)
				continue;
			
			//removes direct route
			Match.Builder matchBuilder = sw.getOFFactory().buildMatch()
					.setExact(MatchField.ETH_TYPE, EthType.IPv4)
					.setExact(MatchField.IPV4_DST, service.getIp());
			
			OFFlowDelete.Builder deleteBuilder = sw.getOFFactory().buildFlowDelete()
					.setCookie(AppCookie.makeCookie(APPLICATION_ID, userID))
					.setCookieMask(mask)
					.setMatch(matchBuilder.build());

			sw.write(deleteBuilder.build());
			
			//removes inverse route
			Match.Builder matchBuilderInverse = sw.getOFFactory().buildMatch()
					.setExact(MatchField.ETH_TYPE, EthType.IPv4)
					.setExact(MatchField.IPV4_SRC, service.getIp());
			
			OFFlowDelete.Builder deleteBuilderInverse = sw.getOFFactory().buildFlowDelete()
					.setCookie(AppCookie.makeCookie(APPLICATION_ID, userID))
					.setCookieMask(mask)
					.setMatch(matchBuilderInverse.build());

			sw.write(deleteBuilderInverse.build());
		}
		
		if(client == null)
			service.emptySwitch();
	}
	
	//methods inherited by the DynamicServiceInterface class
	
	public void subscribeClient(String name, ClientData client) {
		Optional<ServiceData> service = serviceManager.getService(name);
		if(service.isPresent())
			service.get().subscribe(client);
	}
	
	public Optional<ServerData> getServer(String name) {
		Optional<ServiceData> service = serviceManager.getService(name);
		if(service.isPresent())
			return Optional.of(service.get().getServer());
		return Optional.empty();
	}
	public boolean isSubscribe(String name, ClientData client) {
		Optional<ServiceData> service = serviceManager.getService(name);
		if(service.isPresent())
			return service.get().isSubscribed(client);
		return false;
	}
	
	public Iterable<ServiceData> getServices(){
		return serviceManager.getServices();
	}
	
	public void unsubscribeClient(String name, ClientData client) { 
		Optional<ServiceData> service = serviceManager.getService(name);
		if(service.isPresent() && service.get().isSubscribed(client)) {
			service.get().unsubscribe(client);
			//client removed update the route
			updateSwitches(service.get(), client);
		}
	}

	public Optional<ServiceData> getService(String name){ return serviceManager.getService(name); }
	
	public void subscribeServer(String name, ServerData server) throws IllegalArgumentException, InsufficientResourcesException{
		Optional<ServiceData> serviceWrapper = serviceManager.getService(name);
		ServiceData service;
		
		//if service does not exist create a new service
		if(!serviceWrapper.isPresent()) {
			Optional<Long> freeip = pool.get();
			if(!freeip.isPresent())
				throw new InsufficientResourcesException("all virtual ip used");
			
			Iterable<ServiceData> services = serviceManager.getServices();
			for(ServiceData s : services)
				if(s.getServer().equals(server))
					throw new IllegalArgumentException("ip already used for another service");
			
			service = new ServiceData()
					.addServer(server)
					.setName(name)
					.setIp(IPv4Address.of(freeip.get().intValue()));
			serviceManager.insertService(service);
		}else {
			service = serviceWrapper.get();
		}
		
		//subscribe the server to the service
		ServerData data = service.getServer();
		if(!server.equals(data)) {
			service.addServer(server);
			updateSwitches(service, null);
		}
	}
	
	public void unsubscribeServer(String name, ServerData server) { 
		Optional<ServiceData> serviceWrapper = serviceManager.getService(name);
		ServiceData service;
		if(!serviceWrapper.isPresent())
			return;
		service = serviceWrapper.get();
		
		ServerData data = service.getServer();
		
		service.removeServer(server);
		//if there is no server to handle the service, the service is removed
		if(service.getServer() == null) {
			serviceManager.removeService(service);
			pool.add(service.getIp().getInt());
		}
		
		//if the server is relocated the flow table must be updated
		if(!data.equals(service.getServer()))
			updateSwitches(service, null);
	}
	

	
	@Override
	public String getName() {
		return DynamicServiceController.class.getSimpleName();
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		return false;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(DynamicServiceInterface.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>,
		IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>,
		IFloodlightService>();
		m.put(DynamicServiceInterface.class, this);
		return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> response = new ArrayList<>();
		response.add(IFloodlightProviderService.class);
		response.add(IRestApiService.class);
		response.add(IOFSwitchService.class);
		response.add(IOFSwitchService.class);
		return response;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider = (IFloodlightProviderService) context.getServiceImpl(IFloodlightProviderService.class);
		restApiService = (IRestApiService) context.getServiceImpl(IRestApiService.class);
		switchService = context.getServiceImpl(IOFSwitchService.class);

		AppCookie.registerApp(APPLICATION_ID, DynamicServiceController.class.getSimpleName());
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		logger.info("Dynamic relocator is starting....");
		
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);		
		restApiService.addRestletRoutable(new DynamicServiceRoutable());		
	}

	/*
	 * function to create a new direct and reverse route from a client to a service
	 */
	public void createRoutes(ServiceData service, IOFSwitch sw, OFMessage msg, Ethernet eth) {
		service.insertSwitch(sw);
			
		
		OFPacketIn pi = (OFPacketIn) msg;
		IPv4 ipv4 = (IPv4) eth.getPayload();
		
		IPv4Address clientIP = ipv4.getSourceAddress();
		
		
		ServerData server = service.getServer();
		
		// exact match client ---> service
		Match.Builder matchBuilder = sw.getOFFactory().buildMatch()
				.setExact(MatchField.ETH_TYPE, EthType.IPv4)
				.setExact(MatchField.IPV4_DST, service.getIp())
				.setExact(MatchField.IPV4_SRC, clientIP);
		
		ArrayList<OFAction> actionList = new ArrayList<>();
		
		OFOxms oxms = sw.getOFFactory().oxms();
		
		//virtual_mac to server_mac
		OFActionSetField changeDstMAC = sw.getOFFactory().actions().buildSetField()
				.setField(oxms.buildEthDst().setValue(server.getMAC()).build()).build();
		actionList.add(changeDstMAC);
		
		//virtual_ip to server_ip
		OFActionSetField changeDstIP = sw.getOFFactory().actions().buildSetField()
				.setField(oxms.buildIpv4Dst().setValue(server.getIP()).build()).build();
		actionList.add(changeDstIP);
		
		OFActionOutput output = sw.getOFFactory().actions().buildOutput()
				.setMaxLen(0xFFffFFff)
				.setPort(OFPort.TABLE)
				.build();
		actionList.add(output);
		
		//add the match, action pair
		OFFlowAdd.Builder addBuilder = sw.getOFFactory().buildFlowAdd()
				.setHardTimeout(100000)
				.setIdleTimeout(100000)
				.setBufferId(OFBufferId.NO_BUFFER)
				.setPriority(FlowModUtils.PRIORITY_MAX)
				.setCookie(AppCookie.makeCookie(APPLICATION_ID, clientIP.getInt()))
				.setMatch(matchBuilder.build())
				.setActions(actionList);
		
		sw.write(addBuilder.build());
		
		//build reverse route service ---> client
		Match.Builder matchBuilderInverse = sw.getOFFactory().buildMatch()
				.setExact(MatchField.ETH_TYPE, EthType.IPv4)
				.setExact(MatchField.IPV4_SRC, server.getIP())
				.setExact(MatchField.ETH_SRC, server.getMAC())
				.setExact(MatchField.IPV4_DST, clientIP);
		
		ArrayList<OFAction> actionListInverse = new ArrayList<>();
				
		OFActionSetField changeDstMACInverse = sw.getOFFactory().actions().buildSetField()
				.setField(oxms.buildEthSrc().setValue(VIRTUAL_MAC).build()).build();
		actionListInverse.add(changeDstMACInverse);
		
		OFActionSetField changeDstIPInverse = sw.getOFFactory().actions().buildSetField()
				.setField(oxms.buildIpv4Src().setValue(service.getIp()).build()).build();
		actionListInverse.add(changeDstIPInverse);
		
		OFActionOutput outputInverse = sw.getOFFactory().actions().buildOutput()
				.setMaxLen(0xFFffFFff)
				.setPort(OFPort.TABLE)
				.build();
		actionListInverse.add(outputInverse);
		
		OFFlowAdd.Builder addBuilderInverse = sw.getOFFactory().buildFlowAdd()
				.setHardTimeout(100000)
				.setIdleTimeout(100000)
				.setBufferId(OFBufferId.NO_BUFFER)
				.setCookie(AppCookie.makeCookie(APPLICATION_ID, clientIP.getInt()))
				.setPriority(FlowModUtils.PRIORITY_MAX)
				.setMatch(matchBuilderInverse.build())
				.setActions(actionListInverse);
		
		sw.write(addBuilderInverse.build());
		
		OFPacketOut.Builder packetBuilder = sw.getOFFactory().buildPacketOut()
				.setBufferId(pi.getBufferId())
				.setInPort(OFPort.ANY)
				.setActions(actionList);
		
		if(pi.getBufferId() == OFBufferId.NO_BUFFER) {
			byte[] data = pi.getData();
			packetBuilder.setData(data);
		}
		
		sw.write(packetBuilder.build());
		
	}
	
	/*
	 * function that handle the arrival of an ARP packet
	 */
	public void handleARP(ServiceData service, IOFSwitch sw, OFMessage msg, Ethernet eth) {
		
		ARP arp = (ARP) eth.getPayload();
		OFPacketIn pi = (OFPacketIn) msg;
		
		// address resolution should be informed by the existence of the service
		ARP ArpReply = new ARP()
				.setHardwareType(ARP.HW_TYPE_ETHERNET)
				.setProtocolType(ARP.PROTO_TYPE_IP)
				.setHardwareAddressLength((byte)6)
				.setProtocolAddressLength((byte)4)
				.setOpCode(ARP.OP_REPLY)
				.setSenderHardwareAddress(VIRTUAL_MAC)
				.setSenderProtocolAddress(service.getIp())
				.setTargetHardwareAddress(arp.getSenderHardwareAddress())
				.setTargetProtocolAddress(arp.getSenderProtocolAddress());
			
		//creation of the ethernet frame
		IPacket ethReply = new Ethernet()
				.setSourceMACAddress(VIRTUAL_MAC)
				.setDestinationMACAddress(eth.getSourceMACAddress())
				.setEtherType(EthType.ARP)
				.setPriorityCode(eth.getPriorityCode())
				.setPayload(ArpReply);
		
		OFPort port = pi.getMatch().get(MatchField.IN_PORT);
		OFActionOutput.Builder actionBuilder = sw.getOFFactory().actions().buildOutput()
				.setPort(port);
		
		byte[] serializedData = ethReply.serialize();
		
		//send the ARP packet response
		OFPacketOut.Builder responseBuilder = sw.getOFFactory().buildPacketOut()
				.setBufferId(OFBufferId.NO_BUFFER)
				.setInPort(OFPort.ANY)
				.setActions(Collections.singletonList((OFAction) actionBuilder.build()))
				.setData(serializedData);
		
		sw.write(responseBuilder.build());
	}
	
	/*
	 * function that handle the message sent to clients that are not subscribed
	 */
	private void sendError(IOFSwitch sw, OFMessage msg, Ethernet eth) {
		IPv4 ipRecv = (IPv4) eth.getPayload();
		OFPacketIn pi = (OFPacketIn) msg;
		
		// respond using an icmp packet destination unreachable
		ICMP icmp = (ICMP) new ICMP()
		.setIcmpType(ICMP.DESTINATION_UNREACHABLE)
		.setIcmpCode((byte) 1)
		.setPayload(ipRecv);
		
		//wrap the icmp packet into the ip packet
		IPv4 ip = (IPv4) new IPv4()
				.setSourceAddress(ipRecv.getDestinationAddress())
				.setDestinationAddress(ipRecv.getSourceAddress())
				.setProtocol(IpProtocol.ICMP)
				.setTtl((byte) 32)
				.setPayload(icmp);
		
		//wrap the ip packet into an ethernet frame
		IPacket unreachable_reply = new Ethernet()
				.setSourceMACAddress(VIRTUAL_MAC)
				.setDestinationMACAddress(eth.getSourceMACAddress())
				.setEtherType(EthType.IPv4)
				.setPriorityCode(eth.getPriorityCode())
				.setPayload(ip);
		
		//send the frame to the input port
		OFPort port = pi.getMatch().get(MatchField.IN_PORT);
		OFActionOutput.Builder sendBuilder = sw.getOFFactory().actions().buildOutput()
				.setPort(port);
		
		//send the frame
		OFPacketOut.Builder outBuilder = sw.getOFFactory().buildPacketOut()
				.setBufferId(OFBufferId.NO_BUFFER)
				.setInPort(OFPort.ANY)
				.setActions(Collections.singletonList(sendBuilder.build()))
				.setData(unreachable_reply.serialize());
		
		sw.write(outBuilder.build());
	}
	
	@Override
	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		IPacket pkt = eth.getPayload();
		
		
		if(pkt instanceof IPv4) {
			
			IPv4 pkt4 = (IPv4) pkt;
			Optional<ServiceData> serviceWrapper = serviceManager.getService(pkt4.getDestinationAddress());
			if(serviceWrapper.isPresent()) {
				ServiceData service = serviceWrapper.get();
				
				ClientData data = new ClientData(pkt4.getSourceAddress());
				
				if(service.isSubscribed(data))
					this.createRoutes(service, sw, msg, eth);
				else
					this.sendError(sw, msg, eth);
				return Command.STOP;
			}
		} else if ((eth.isBroadcast() || eth.isMulticast()) && pkt instanceof ARP) {
			
			ARP arp = (ARP) pkt;
			Optional<ServiceData> serviceWrapper = serviceManager.getService(arp.getTargetProtocolAddress());
			
			if(serviceWrapper.isPresent()) {
				handleARP(serviceWrapper.get(), sw, msg, eth);
				
				return Command.STOP;
			}
		}
		
		return Command.CONTINUE;
	}

}
