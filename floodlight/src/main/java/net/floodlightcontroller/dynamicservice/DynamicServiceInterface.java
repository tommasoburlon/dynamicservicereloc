package net.floodlightcontroller.dynamicservice;

import java.util.Optional;

import javax.naming.InsufficientResourcesException;

import org.projectfloodlight.openflow.types.IPv4Address;

import net.floodlightcontroller.core.module.IFloodlightService;

/*
 * Interface for the sub/unsub service implemented by the controller
 */
public interface DynamicServiceInterface extends IFloodlightService{
		
	public void subscribeClient(String service, ClientData client);
	public void unsubscribeClient(String service, ClientData client);
	public void subscribeServer(String name, ServerData server) throws IllegalArgumentException, InsufficientResourcesException;
	public void unsubscribeServer(String name, ServerData server);
	public Optional<ServerData> getServer(String name);
	public boolean isSubscribe(String name, ClientData client);
	public Iterable<ServiceData> getServices();
	public Optional<ServiceData> getService(String name);
}
