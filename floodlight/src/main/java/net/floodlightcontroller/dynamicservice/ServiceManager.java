package net.floodlightcontroller.dynamicservice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;

import org.projectfloodlight.openflow.types.IPv4Address;

/*
 * classes that handle the services
 */
public class ServiceManager {
	private HashMap<IPv4Address, ServiceData> services;
	private HashMap<String, IPv4Address> names;
	
	public ServiceManager() {
		services = new HashMap<>();
		names = new HashMap<>();
	}
	
	public Optional<IPv4Address> getServiceIp(String name) {
		if(names.containsKey(name))
			return Optional.of(names.get(name));
		return Optional.empty();
	}
	
	public Optional<ServiceData> getService(String name) {
		if(names.containsKey(name))
			return Optional.of(services.get(names.get(name)));
		return Optional.empty();
	}
	
	public Optional<ServiceData> getService(IPv4Address vip){
		if(services.containsKey(vip))
			return Optional.of(services.get(vip));
		return Optional.empty();
	}
	
	public ServiceManager insertService(ServiceData service) {
		services.put(service.getIp(), service);
		names.put(service.getName(), service.getIp());
		return this;
	}
	
	public ServiceManager removeService(ServiceData service) {
		if(services.containsKey(service.getIp())) {
			services.remove(service.getIp());
			names.remove(service.getName());
		}
		return this;
	}
	
	public Iterable<ServiceData> getServices(){
		ArrayList<ServiceData> response = new ArrayList<>();
		for(ServiceData service : services.values())
			response.add(service);
		return response;
	}
	
	
}
