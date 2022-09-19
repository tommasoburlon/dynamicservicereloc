package net.floodlightcontroller.dynamicservice;

import org.projectfloodlight.openflow.types.IPv4Address;

/*
 * Class that represent a client subscribed to a service
 */
public class ClientData {
	
	private IPv4Address ip;
	
	public ClientData(IPv4Address ip) {
		this.ip = ip;
	};
	
	public IPv4Address getIP() { return ip; }
	public ClientData setIP(IPv4Address ip) { this.ip = ip; return this; }
	
	@Override
	public boolean equals(Object o) {
		if(!(o instanceof ClientData)) return false;
		ClientData data = (ClientData) o;
		return data.ip.equals(ip);
	}
	
	@Override
	public int hashCode() {
		return ip.hashCode();
	}
	
	@Override
	public String toString() {
		return ip.toString();
	}
}
