package net.floodlightcontroller.dynamicservice;

import java.io.IOException;
import java.util.Optional;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;


/*
 * Class the handle the REST requests for the server subscription/unsubscription
 */
public class ServerSubscriptionResource extends ServerResource{
	protected static Logger logger = LoggerFactory.getLogger(ServerSubscriptionResource.class);
	
	
	
	public ObjectNode completeRequest(DynamicServiceInterface controller, String action, ObjectMapper mapper, JsonNode root) {
		ObjectNode response = mapper.createObjectNode();
		
		//ip: ip of the server, mac: mac of the server, service: name of the service, priority: unused
		JsonNode addrNode = root.get("ip");
		JsonNode macNode  = root.get("mac");
		JsonNode serviceNode = root.get("service");
		JsonNode priorityNode = root.get("priority");
		
		if(addrNode == null)
			response.put("reason", "no ip address");
		if(macNode == null)
			response.put("reason", "no mac address");
		if(serviceNode == null)
			response.put("reason", "no service name");
		if(addrNode == null || macNode == null || serviceNode == null)
			return response;
				
				
		IPv4Address addr = IPv4Address.of(addrNode.asText());
		MacAddress mac = MacAddress.of(macNode.asText());
		int priority = priorityNode == null ? 0 : priorityNode.asInt();
		
		
		ServerData server = new ServerData()
				.setIP(addr)
				.setMAC(mac)
				.setPriority(priority);
		
		if(action.equals("subscribe")) {
			try {
				controller.subscribeServer(serviceNode.asText(), server);
				response.put("status", 1);
			}catch(Exception e) {
				response.put("status", 0);
				response.put("reason", e.getMessage());
			}
			
		}else if(action.equals("unsubscribe")){
			controller.unsubscribeServer(serviceNode.asText(), server);
			response.put("status", 1);
		}else {
			response.put("response", "resourse \"" + action + "\" unknown");
		}
			
		return response;
	}
	
	@Post("json")
	public String handleRequest(String jsonString) {
		DynamicServiceInterface controller =
                (DynamicServiceInterface)getContext().getAttributes().
                    get(DynamicServiceInterface.class.getCanonicalName());
				
		String action = (String) getRequestAttributes().get("action");
		
		ObjectMapper mapper = new ObjectMapper();
		JsonNode json = null;
		
		try {
			json = mapper.readTree(jsonString);
		}catch(Exception e) {
			return  "{\"status\": 0, \"reason\": \"failed json parsing\"}";
		}
		
		try {
			return mapper.writer().writeValueAsString(completeRequest(controller, action, mapper, json));
		}catch(Exception e) {
			return  "{\"status\": 0, \"reason\": \"failed json parsing\"}";
		}
	}
}
