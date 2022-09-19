package net.floodlightcontroller.dynamicservice;

import java.io.IOException;
import java.util.Optional;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;


/*
 * Class that implement the handle of the REST requests for subscription/unsubscription 
 * of a client to a service
 */
public class ClientSubscriptionResource extends ServerResource {
	protected static Logger logger = LoggerFactory.getLogger(ClientSubscriptionResource.class);
	
	
	public ObjectNode completeRequest(DynamicServiceInterface controller, String action, ObjectMapper mapper, JsonNode root) {
		ObjectNode response = mapper.createObjectNode();
		response.put("status", 0);
		
		// ip: ip of the client, service: service to be un/subscribe using the string representation
		JsonNode addrNode = root.get("ip");
		JsonNode serviceNode = root.get("service");
		
		if(addrNode == null)
			response.put("reason", "no ip argument");
		if(serviceNode == null)
			response.put("reason", "no service argument");
		if(addrNode == null || serviceNode == null)
			return response;	
		
		IPv4Address addr = IPv4Address.of(addrNode.asText());
		ClientData data = new ClientData(addr);
		Optional<ServiceData> serviceWrapper = controller.getService(serviceNode.asText());
		
		if(!serviceWrapper.isPresent())
			response.put("reason", "service not found");
		if(!serviceWrapper.isPresent())
			return response;
		
		//send to the client the virtual_ip of the service
		response.put("ip", serviceWrapper.get().getIp().toString());
		
		if(action.equals("subscribe")) {
			controller.subscribeClient(serviceNode.asText(), data);
			response.put("status", 1);
		}else if(action.equals("unsubscribe")){
			controller.unsubscribeClient(serviceNode.asText(), data);
			response.put("status", 1);
		}else {
			response.put("reason", "resourse \"" + action + "\" unknown");
		}
		return response;
	}
	
	@Post("json")
	public String handleRequest(String jsonString) {
		//retrieve the controller to un/sub the client
		DynamicServiceInterface controller =
                (DynamicServiceInterface)getContext().getAttributes().
                    get(DynamicServiceInterface.class.getCanonicalName());
		
		String action = (String) getRequestAttributes().get("action");
		
		ObjectMapper mapper = new ObjectMapper();
		
		JsonNode json = null;
		
		try {
			json = mapper.readTree(jsonString);
		}catch(Exception e) {
			return "{\"status\": 0, \"reason\": \"failed json parsing\"}";
		}
		
		
		try {
			return mapper.writer().writeValueAsString(completeRequest(controller, action, mapper, json));
		}catch(Exception e) {
			return "{\"status\": 0, \"reason\": \"failed json serialization\"}";
		}
	}
	
}
