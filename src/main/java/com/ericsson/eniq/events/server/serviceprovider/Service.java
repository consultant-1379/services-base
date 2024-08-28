/**
 * -----------------------------------------------------------------------
 *     Copyright (C) 2011 LM Ericsson Limited.  All rights reserved.
 * -----------------------------------------------------------------------
 */
package com.ericsson.eniq.events.server.serviceprovider;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

/**
 * Performs actual query and provides boundary between the interface exposed to the client (the REST end points)
 * and the business logic of the services application
 * 
 * Format of the serviceProviderParameters argument:
 * This is a map of all parameters required for the service provider layer.
 * It contains
 *      <li> all URI parameters provided in URL </li>
 *      <li> the user name(s) provided in the http header </li>
 *      <li> the ip address(es) provided in the http header </li>
 *      <li> the request ID provided in the http header </li>
 *      <li> the acceptable media type(s) provided in the http header </li>
 * 
 * @author EEMECOY
 *
 */
public interface Service {

    /**
     * Retrieve the data in CSV format for the given parameters
     * 
     * @param serviceProviderParameters         map of parameters - see list of required parameters above
     * @param response 
     */
    Response getDataAsCSV(MultivaluedMap<String, String> serviceProviderParameters, HttpServletResponse response);

    /**
     * Retrieve the data (in JSON format) for the given parameters 
     *
     * @param serviceProviderParameters         map of parameters - see list of required parameters above
     * @returns the result of the SQL query, formatted as JSON
     */
    String getData(MultivaluedMap<String, String> serviceProviderParameters);

}
