package net.floodlightcontroller.dynamicservice;

import java.util.HashSet;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import net.floodlightcontroller.core.IOFSwitch;

/*
 * Class containing service data, like the name, the virtual ip, the server linked, and a list
 * of subscribed client
 */
public class ServiceData {
	private String name;
	private IPv4Address ip;
	
	private ServerData server;
	private HashSet<ClientData> clients;
	
	//switches programmed to link client with the service
	private HashSet<DatapathId> switches;
	
	public ServiceData(){
		clients = new HashSet<>();
		switches = new HashSet<>();
	}
	
	// "getter"/"setter" methods
	public ServiceData addServer(ServerData _server) { server = _server; return this; }
	public ServiceData removeServer(ServerData _server) {if(server.equals(_server)) server = null; return this; }
	public ServerData getServer() { return server; }
	
	public ServiceData setName(String _name) { name = _name; return this; }
	public ServiceData setIp(IPv4Address _ip) { ip = _ip; return this; }
	public ServiceData subscribe(ClientData client) { clients.add(client); return this; }
	public ServiceData unsubscribe(ClientData client) { clients.remove(client); return this; }
	
	public String getName() { return name; }
	public IPv4Address getIp() { return ip; }
	
	public ServiceData insertSwitch(IOFSwitch sw) { switches.add(sw.getId()); return this; }
	public ServiceData removeSwitch(IOFSwitch sw) { switches.remove(sw.getId()); return this; }
	public ServiceData emptySwitch() { switches.clear(); return this; }
	public Iterable<DatapathId> getSwitches(){ return switches; }
	
	public boolean isSubscribed(ClientData client) { return clients.contains(client); }
	
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		
		builder.append("service: " + name + ", ");
		builder.append("server: " + server + ", ");
		builder.append("ip: " + ip + ", ");
		builder.append("clients: [");
		for(ClientData client : clients) builder.append(client + ", ");
		builder.append("]");
		
		return builder.toString();
	}
	
	public ObjectNode json() {
		ObjectMapper mapper = new ObjectMapper();
		
		ObjectNode root = mapper.createObjectNode();
		
		root.put("name", name);
		root.put("virtual_ip", ip.toString());
		root.put("server_ip", server.getIP().toString());
		root.put("server_mac", server.getMAC().toString());
		
		ArrayNode clientArray = mapper.createArrayNode();
		for(ClientData client : clients) {
			ObjectNode clientNode = mapper.createObjectNode();
			clientNode.put("ip", client.getIP().toString());
			clientArray.add(clientNode);
		}
		root.set("clients", clientArray);
		
		return root;
	}
	
}
