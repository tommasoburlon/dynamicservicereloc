package net.floodlightcontroller.dynamicservice;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;

/*
 * Class containing the data of a server linked with a service
 */
public class ServerData {
	private IPv4Address ip;
	private MacAddress mac;
	
	//priority is currently unused
	private int priority;
	
	public ServerData() {
		this.ip = null;
		this.mac = null;
	};
	
	//setter/getter methods
	public IPv4Address getIP() { return ip; }
	public ServerData setIP(IPv4Address ip) { this.ip = ip; return this; }
	
	public int getPriority() { return priority; }
	public ServerData setPriority(int _priority) {priority = _priority; return this; }
	public MacAddress getMAC() { return mac; }
	public ServerData setMAC(MacAddress mac) { this.mac = mac; return this; }
	
	@Override
	public boolean equals(Object o) {
		if(!(o instanceof ServerData)) return false;
		
		ServerData data = (ServerData) o;
		
		return ip.equals(data.ip) && mac.equals(data.mac);
	}
	
	@Override
	public int hashCode() {
		int k = 17;
		int h1 = ip.hashCode();
		int h2 = mac.hashCode();
		return h1 * k + h2;
	}
	
	@Override
	public String toString() {
		return "(" + ip.toString() + ", " + mac.toString() + ")";
	}
}
