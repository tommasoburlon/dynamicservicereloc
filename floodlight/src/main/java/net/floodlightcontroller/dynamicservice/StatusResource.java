package net.floodlightcontroller.dynamicservice;

import java.util.Optional;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/*
 * class that handle the "status" REST request, to obtain a snapshot of the system
 * useful for debugging
 */
public class StatusResource extends ServerResource {
	protected static Logger logger = LoggerFactory.getLogger(StatusResource.class);
	
	@Get("json")
	public String handleRequest() {
		DynamicServiceInterface controller = (DynamicServiceInterface)getContext()
				.getAttributes()
				.get(DynamicServiceInterface.class.getCanonicalName());
		if(controller == null)
			return "{\"status\": 0}";
		
		ObjectMapper mapper = new ObjectMapper();
			
		ObjectNode root = mapper.createObjectNode();
		ObjectNode services = mapper.createObjectNode();
						
		Iterable<ServiceData> servicesItr = controller.getServices();
		for(ServiceData service : servicesItr) {
			services.set(service.getName(), service.json());
		}
			
		root.put("status", 1);
		root.set("services", services);
					
		try {
			return mapper.writer().writeValueAsString(root);
		} catch (Exception e) {
			logger.info(e.toString());
		}
		
		return "{\"status\": 0}";
	}
}
