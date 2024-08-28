/**
 * -----------------------------------------------------------------------
 *     Copyright (C) 2011 LM Ericsson Limited.  All rights reserved.
 * -----------------------------------------------------------------------
 */
package com.ericsson.eniq.events.server.resources;

import static com.ericsson.eniq.events.server.common.ApplicationConstants.*;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import com.ericsson.eniq.events.server.common.MediaTypeConstants;
import com.ericsson.eniq.events.server.serviceprovider.Service;
import com.sun.jersey.core.util.MultivaluedMapImpl;

/**
 * A prototype for the rework which will be carried out as part of the WCDMA feature
 *
 * This class will need to be renamed, cannot use BaseResource as that class (and the corresponding framework) will
 * co-exist with this new framework for some time
 *
 * This class should be used as the base class for any new and refactored Resource classes.
 * Each sub resource should only contain information specific to the end points for that sub resource - all business logic
 * and query related information should be in the corresponding Service subclass
 *
 * @author eemecoy
 *
 */
public abstract class AbstractResource {

    @Context
    protected UriInfo uriInfo;

    @Context
    protected HttpHeaders httpHeaders;

    /** The response. */
    @Context
    protected HttpServletResponse response;

    /**
     * Map requests to data service queries and return JSON encoded result for
     * relevant data.
     *
     * Request parameters are encapsulated in a UriInfo instance. Since there are
     * many potential parameters and these have certain relationships processing
     * is more easily done by accessing those which are relevant.
     *
     * @return JSON encoded results
     * @throws WebApplicationException
     *           the web application exception
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String getData() throws WebApplicationException {
        final Service service = getService();
        return service.getData(mapResourceLayerParameters());
    }

    protected abstract Service getService();

    /**
     * Map requests to data service queries and return JSON encoded result for
     * relevant data.
     *
     * Request parameters are encapsulated in a UriInfo instance. Since there are
     * many potential parameters and these have certain relationships processing
     * is more easily done by accessing those which are relevant.
     *
     *
     * @return results in CSV format
     * @throws WebApplicationException
     *           the exception
     */
    @GET
    @Produces(MediaTypeConstants.APPLICATION_CSV)
    public Response getDataAsCSV() throws WebApplicationException {
        final Service service = getService();
        return service.getDataAsCSV(mapResourceLayerParameters(), response);
    }

    protected MultivaluedMap<String, String> mapResourceLayerParameters() {
        final MultivaluedMap<String, String> serviceProviderParameters = new MultivaluedMapImpl();
        serviceProviderParameters.putAll(getDecodedURIParameters());
        serviceProviderParameters.put(MEDIA_TYPE, getAcceptableMediaTypes());
        serviceProviderParameters.add(REQUEST_URI, getRequestURI());
        serviceProviderParameters.put(IP_ADDRESS_PARAM, getIpAddressesFromHttpHeader());
        serviceProviderParameters.add(REQUEST_ID, getRequestIdFromHttpHeader());
        return serviceProviderParameters;
    }

    private MultivaluedMap<String, String> getDecodedURIParameters() {
        return uriInfo.getQueryParameters(true);
    }

    private String getRequestIdFromHttpHeader() {
        return httpHeaders.getRequestHeaders().getFirst(REQUEST_ID);
    }

    private List<String> getIpAddressesFromHttpHeader() {
        return httpHeaders.getRequestHeader(IP_ADDRESS_PARAM);
    }

    private String getRequestURI() {
        return uriInfo.getRequestUri().toString();
    }

    private List<String> getAcceptableMediaTypes() {
        final List<MediaType> acceptableMediaTypes = httpHeaders.getAcceptableMediaTypes();
        final List<String> acceptableMediaTypesStringified = new ArrayList<String>();
        for (final MediaType mediaType : acceptableMediaTypes) {
            acceptableMediaTypesStringified.add(mediaType.toString());
        }
        return acceptableMediaTypesStringified;
    }

}
