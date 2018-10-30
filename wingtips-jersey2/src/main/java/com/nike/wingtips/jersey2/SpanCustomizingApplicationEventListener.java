package com.nike.wingtips.jersey2;

import com.nike.wingtips.tags.KnownZipkinTags;

import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.internal.routing.RoutingContext;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;
import org.glassfish.jersey.uri.UriTemplate;

import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.ext.Provider;

import static org.glassfish.jersey.server.monitoring.RequestEvent.Type.REQUEST_MATCHED;

/**
 * This sets the request property "http.route" so that it can be used in naming the HTTP span.
 *
 * <p>This is intended to be used in Jersey 2 environments that are also using the Wingtips {@code
 * RequestTracingFilter}.
 *
 * <p>NOTE: This class was mostly copied from Zipkin's
 * <a href="https://github.com/openzipkin/brave/blob/1cffdc124647643800f624f0499dabffcabf649b/instrumentation/jersey-server/src/main/java/brave/jersey/server/SpanCustomizingApplicationEventListener.java">
 * SpanCustomizingApplicationEventListener
 * </a>.
 */
@Provider
@SuppressWarnings("WeakerAccess")
public class SpanCustomizingApplicationEventListener implements ApplicationEventListener, RequestEventListener {

    @Inject
    SpanCustomizingApplicationEventListener() {
    }

    public static SpanCustomizingApplicationEventListener create() {
        return new SpanCustomizingApplicationEventListener();
    }

    @Override
    public void onEvent(ApplicationEvent event) {
        // Only onRequest is used.
    }

    @Override
    public RequestEventListener onRequest(RequestEvent requestEvent) {
        if (requestEvent.getType() == RequestEvent.Type.START) {
            return this;
        }

        return null;
    }

    @Override
    public void onEvent(RequestEvent event) {
        // We only care about the REQUEST_MATCHED event.
        if (event.getType() != REQUEST_MATCHED) {
            return;
        }

        ContainerRequest request = event.getContainerRequest();
        // Setting the http.route as a setProperty() on this ContainerRequest will bubble out to the
        //      HttpServletRequest as a request attribute.
        request.setProperty(KnownZipkinTags.HTTP_ROUTE, route(request));
    }

    /**
     * This returns the matched template as defined by a base URL and path expressions.
     *
     * <p>Matched templates are pairs of (resource path, method path) added with
     * {@link RoutingContext#pushTemplates(UriTemplate, UriTemplate)}.
     * This code skips redundant slashes from either source caused by Path("/") or Path("").
     */
    protected String route(ContainerRequest request) {
        ExtendedUriInfo uriInfo = request.getUriInfo();
        List<UriTemplate> templates = uriInfo.getMatchedTemplates();
        int templateCount = templates.size();
        if (templateCount == 0) {
            return "";
        }
        StringBuilder builder = null; // don't allocate unless you need it!
        String basePath = uriInfo.getBaseUri().getPath();
        String result = null;

        if (!"/".equals(basePath)) { // skip empty base paths
            result = basePath;
        }
        for (int i = templateCount - 1; i >= 0; i--) {
            String template = templates.get(i).getTemplate();
            if ("/".equals(template)) {
                continue; // skip allocation
            }
            if (builder != null) {
                builder.append(template);
            }
            else if (result != null) {
                builder = new StringBuilder(result).append(template);
                result = null;
            }
            else {
                result = template;
            }
        }

        return (result != null)
               ? result
               : (builder != null)
                 ? builder.toString()
                 : "";
    }
}