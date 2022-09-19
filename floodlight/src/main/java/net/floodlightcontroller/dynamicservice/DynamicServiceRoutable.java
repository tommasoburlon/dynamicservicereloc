package net.floodlightcontroller.dynamicservice;

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.resource.ServerResource;
import org.restlet.routing.Router;

import net.floodlightcontroller.restserver.RestletRoutable;

/*
 * class to configure the REST interface
 */
public class DynamicServiceRoutable implements RestletRoutable{

	@Override
	public Restlet getRestlet(Context context) {
		Router router = new Router(context);
		
		// action is a param, should be "subscribe" or "unsubscribe"
		router.attach("/server/{action}", ServerSubscriptionResource.class);
		router.attach("/client/{action}", ClientSubscriptionResource.class);
		router.attach("/status", StatusResource.class);
		
		return router;
	}

	@Override
	public String basePath() {
		return "/dynamicreloc";
	}

}
