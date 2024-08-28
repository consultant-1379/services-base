/**
 * -----------------------------------------------------------------------
 *     Copyright (C) 2011 LM Ericsson Limited.  All rights reserved.
 * -----------------------------------------------------------------------
 */
package com.ericsson.eniq.events.server.resources;

import static com.ericsson.eniq.events.server.common.ApplicationConstants.*;
import static com.ericsson.eniq.events.server.test.common.ApplicationTestConstants.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;

import org.jmock.Expectations;
import org.junit.Before;
import org.junit.Test;

import com.ericsson.eniq.events.server.serviceprovider.Service;
import com.ericsson.eniq.events.server.test.common.BaseJMockUnitTest;
import com.sun.jersey.core.util.MultivaluedMapImpl;

/**
 * @author eemecoy
 *
 */
public class AbstractResourceTest extends BaseJMockUnitTest {

    private static final String SAMPLE_PARAMETER_NAME = "SAMPLE_PARAMETER_NAME";

    private static final String SAMPLE_REQUEST_URI = "somePath/someURI";

    private static final String SAMPLE_IP_ADDRESS = "0.0.0.0";

    private SimpleResource resource;

    HttpHeaders mockedHttpHeaders;

    UriInfo mockedUriInfo;

    @Before
    public void setup() {
        resource = new SimpleResource();
        mockedHttpHeaders = mockery.mock(HttpHeaders.class);
        resource.httpHeaders = mockedHttpHeaders;
        mockedUriInfo = mockery.mock(UriInfo.class);
        resource.uriInfo = mockedUriInfo;
    }

    @Test
    public void testMapResourceLayerParameters_RequestID() {
        setUpExpectations(null, new MultivaluedMapImpl(), SAMPLE_REQUEST_URI, SAMPLE_IP_ADDRESS, SAMPLE_REQUEST_ID);
        final MultivaluedMap<String, String> result = resource.mapResourceLayerParameters();
        assertThat(result.getFirst(REQUEST_ID), is(SAMPLE_REQUEST_ID));
    }

    @Test
    public void testMapResourceLayerParameter_UserNameIPAddress() {
        setUpExpectations(null, new MultivaluedMapImpl(), SAMPLE_REQUEST_URI, SAMPLE_IP_ADDRESS, SAMPLE_REQUEST_ID);
        final MultivaluedMap<String, String> result = resource.mapResourceLayerParameters();
        assertThat(result.getFirst(IP_ADDRESS_PARAM), is(SAMPLE_IP_ADDRESS));
    }

    @Test
    public void testMapResourceLayerParameters_URI() {
        setUpExpectations(null, new MultivaluedMapImpl(), SAMPLE_REQUEST_URI, SAMPLE_IP_ADDRESS, SAMPLE_REQUEST_ID);
        final MultivaluedMap<String, String> result = resource.mapResourceLayerParameters();
        assertThat(result.getFirst(REQUEST_URI), is(SAMPLE_REQUEST_URI));
    }

    private void setUpExpectations(final MediaType mediaType, final MultivaluedMap<String, String> queryParameters,
            final String requestURI, final String ipAddress, final String requestId) {
        final List<MediaType> acceptableMediaTypes = new ArrayList<MediaType>();
        if (mediaType != null) {
            acceptableMediaTypes.add(mediaType);
        }
        expectGetMediaTypesOnHttpHeaders(acceptableMediaTypes);
        expectGetRequestHeadersOnHttpHeaders(ipAddress);
        expectGetRequestIdOnHttpHeaders(requestId);
        expectGetQueryParametersOnUriInfo(queryParameters);
        expectGetRequestUriOnUriInfo(requestURI);
    }

    private void expectGetRequestIdOnHttpHeaders(final String requestId) {

        final MultivaluedMap<String, String> requestHeaders = new MultivaluedMapImpl();
        requestHeaders.add(REQUEST_ID, requestId);
        mockery.checking(new Expectations() {
            {
                one(mockedHttpHeaders).getRequestHeaders();
                will(returnValue(requestHeaders));

            }
        });

    }

    private void expectGetRequestHeadersOnHttpHeaders(final String ipAddress) {
        final List<String> ipAddresses = new ArrayList<String>();
        ipAddresses.add(ipAddress);
        mockery.checking(new Expectations() {
            {
                one(mockedHttpHeaders).getRequestHeader(IP_ADDRESS_PARAM);
                will(returnValue(ipAddresses));
            }
        });

    }

    private void expectGetRequestUriOnUriInfo(final String requestURI) {
        final URI mockedRequestURI = URI.create(requestURI);
        mockery.checking(new Expectations() {
            {
                one(mockedUriInfo).getRequestUri();
                will(returnValue(mockedRequestURI));
            }
        });

    }

    @Test
    public void testMapResourceLayerParameters_MediaType() {
        final MediaType mediaType = MediaType.APPLICATION_JSON_TYPE;
        setUpExpectations(mediaType, new MultivaluedMapImpl(), SAMPLE_REQUEST_URI, SAMPLE_IP_ADDRESS, SAMPLE_REQUEST_ID);
        final MultivaluedMap<String, String> result = resource.mapResourceLayerParameters();
        final List<String> mediaTypes = result.get(MEDIA_TYPE);
        assertThat(mediaTypes.size(), is(1));
        assertThat(mediaTypes.contains(mediaType.toString()), is(true));

    }

    @Test
    public void testMapResourceLayerParameters_URLParameters() {
        final MultivaluedMap<String, String> queryParameters = new MultivaluedMapImpl();
        final String nodeParameterValue = SAMPLE_BSC;
        queryParameters.add(NODE_PARAM, nodeParameterValue);
        setUpExpectations(null, queryParameters, SAMPLE_REQUEST_URI, SAMPLE_IP_ADDRESS, SAMPLE_REQUEST_ID);

        final MultivaluedMap<String, String> result = resource.mapResourceLayerParameters();
        assertThat(result.getFirst(NODE_PARAM), is(nodeParameterValue));
    }

    @Test
    public void testMapResourceLayerParametersHandlesMultipleValuesForSameParameter() {
        final MultivaluedMap<String, String> queryParameters = new MultivaluedMapImpl();
        final String firstParmameterValue = "London";
        queryParameters.add(SAMPLE_PARAMETER_NAME, firstParmameterValue);
        final String secondParmameterValue = "Paris";
        queryParameters.add(SAMPLE_PARAMETER_NAME, secondParmameterValue);
        setUpExpectations(null, queryParameters, SAMPLE_REQUEST_URI, SAMPLE_IP_ADDRESS, SAMPLE_REQUEST_ID);

        final MultivaluedMap<String, String> result = resource.mapResourceLayerParameters();

        final List<String> parameterValues = result.get(SAMPLE_PARAMETER_NAME);
        assertThat(parameterValues.size(), is(2));
        assertThat(parameterValues.contains(firstParmameterValue), is(true));
        assertThat(parameterValues.contains(secondParmameterValue), is(true));
    }

    private void expectGetQueryParametersOnUriInfo(final MultivaluedMap<String, String> queryParameters) {
        mockery.checking(new Expectations() {
            {
                one(mockedUriInfo).getQueryParameters(true);
                will(returnValue(queryParameters));
            }
        });

    }

    private void expectGetMediaTypesOnHttpHeaders(final List<MediaType> acceptableMediaTypes) {
        mockery.checking(new Expectations() {
            {
                one(mockedHttpHeaders).getAcceptableMediaTypes();
                will(returnValue(acceptableMediaTypes));
            }
        });
    }

    class SimpleResource extends AbstractResource {

        @Override
        protected Service getService() {
            return null;
        }

    }
}
