/**
 * -----------------------------------------------------------------------
 *     Copyright (C) 2011 LM Ericsson Limited.  All rights reserved.
 * -----------------------------------------------------------------------
 */
package com.ericsson.eniq.events.server.serviceprovider.impl;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.ejb.EJB;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import com.ericsson.eniq.events.server.datasource.loadbalancing.LoadBalancingPolicy;
import com.ericsson.eniq.events.server.logging.ServicesLogger;
import com.ericsson.eniq.events.server.query.QueryParameter;
import com.ericsson.eniq.events.server.query.resultsettransformers.ResultSetTransformer;
import com.ericsson.eniq.events.server.serviceprovider.Service;
import com.ericsson.eniq.events.server.services.DataService;
import com.ericsson.eniq.events.server.services.StreamingDataService;
import com.ericsson.eniq.events.server.templates.mappingengine.TemplateMappingEngine;
import com.ericsson.eniq.events.server.templates.utils.TemplateUtils;
import com.ericsson.eniq.events.server.utils.AuditService;
import com.ericsson.eniq.events.server.utils.CSVResponseBuilder;
import com.ericsson.eniq.events.server.utils.LoadBalancingPolicyService;
import com.ericsson.eniq.events.server.utils.MediaTypeHandler;
import com.ericsson.eniq.events.server.utils.json.JSONUtils;
import org.apache.commons.lang.StringUtils;

import static com.ericsson.eniq.events.server.common.ApplicationConstants.MEDIA_TYPE;
import static com.ericsson.eniq.events.server.common.ApplicationConstants.REQUEST_ID;
import static com.ericsson.eniq.events.server.logging.performance.ServicesPerformanceThreadLocalHolder.releaseAllResources;

/**
 * The base class for all simple resource services in the services layer. This
 * class is responsible for controlling the flow through the services layer. A
 * simple Service is a 'stripped down' version of a GenericService - i.e the
 * request will not include date/time range, node type, timezone information
 * etc.
 *
 * @author epesmit
 * @since 2011
 */
public abstract class GenericSimpleService implements Service, GenericSimpleServiceInterface {

   @EJB
   private CSVResponseBuilder csvResponseBuilder;

   @EJB
   private DataService dataService;

   @EJB
   private TemplateUtils templateUtils;

   @EJB
   private TemplateMappingEngine templateMappingEngine;

   @EJB
   private LoadBalancingPolicyService loadBalancingPolicyService;

   @EJB
   private AuditService auditService;

   @EJB
   private StreamingDataService streamingDataService;

   @EJB
   private MediaTypeHandler mediaTypeHandler;

   @Override
   public String getData(final MultivaluedMap<String, String> parameters) {
      return getAndRunSimpleQuery(parameters, null);
   }

   public String getData(final MultivaluedMap<String, String> parameters,
                         final ResultSetTransformer<String> resultSetTransformerFactory) {
      return getAndRunQuery(parameters, resultSetTransformerFactory);
   }

   @Override
   public Response getDataAsCSV(final MultivaluedMap<String, String> parameters, final HttpServletResponse response) {
      getAndRunSimpleQuery(parameters, response);
      return csvResponseBuilder.buildHttpResponseForCSVData();
   }

   /**
    * Execute the SQL query against the database. Logic common to all services.
    *
    * @param query               the SQL query to execute
    * @param requestId           request ID
    * @param queryParameters     parameters for the SQL query
    * @param loadBalancingPolicy load balancing policy to use when selecting SQL connection to
    *                            use for query
    *                            the parameters to the call on the data service layer that are
    *                            specific to this service
    *
    * @return the result of the query in JSON format
    */
   protected String runSimpleQuery(final String query, final String requestId,
                                 final Map<String, QueryParameter> queryParameters, final LoadBalancingPolicy loadBalancingPolicy) {
      return dataService.getGridData(requestId, query, queryParameters, "0", "0", loadBalancingPolicy);
   }

   /**
    * Retrieve and run the query. Logic common to all services.
    *
    * @param parameters          parameters from the resource layer
    * @param httpServletResponse response object (can be null, used when streaming csv
    *                            response)
    *
    * @return json response, null if request is for csv data as this is
    *         streamed to the response
    */
   private String getAndRunSimpleQuery(final MultivaluedMap<String, String> parameters,
                                       final HttpServletResponse httpServletResponse) {
      try {
         final String templateFile = templateMappingEngine.getTemplate(getTemplatePath(), parameters, null);
         final String query = templateUtils.getQueryFromTemplate(templateFile);
         if (StringUtils.isBlank(query)) {
            return JSONUtils.JSONBuildFailureError();
         }
         return logAndRunQuery(httpServletResponse, parameters, query);
      } finally {
         releaseAllResources();
      }
   }

   /**
    * Retrieve and run the query. Logic common to all services.
    *
    * @param parameters parameters from the resource layer
    *
    * @return json response, null if request is for csv data as this is
    *         streamed to the response
    */
   private String getAndRunQuery(final MultivaluedMap<String, String> parameters,
                                 final ResultSetTransformer<String> resultSetTransformerFactory
   ) {
      try {
         final String templateFile = templateMappingEngine.getTemplate(getTemplatePath(), parameters, null);
         final String query = templateUtils.getQueryFromTemplate(templateFile, parameters);

         if (StringUtils.isBlank(query)) {
            return JSONUtils.JSONBuildFailureError();
         }
         return logAndRunQuery(parameters, query, resultSetTransformerFactory);
      } finally {
         releaseAllResources();
      }
   }

   /**
    * Log and run the query. Logic common to all services.
    *
    * @param parameters parameters from the resource layer
    * @param query
    *
    * @return json response, null if request is for csv data as this is
    *         streamed to the response
    */
   private String logAndRunQuery(
           final MultivaluedMap<String, String> parameters, final String query,
           final ResultSetTransformer<String> resultSetTransformerFactory) {
      final Map<String, QueryParameter> queryParameters = new HashMap<String, QueryParameter>();
      auditService.logAuditEntryForQuery(parameters, query, queryParameters);

      return dataService.getData(query, queryParameters, resultSetTransformerFactory);
   }

   /**
    * Log and run the query. Logic common to all services.
    *
    * @param httpServletResponse response object (can be null, used when streaming csv
    *                            response)
    * @param parameters          parameters from the resource layer
    * @param query
    *
    * @return json response, null if request is for csv data as this is
    *         streamed to the response
    */
   private String logAndRunQuery(final HttpServletResponse httpServletResponse,
                                 final MultivaluedMap<String, String> parameters, final String query) {
      final Map<String, QueryParameter> queryParameters = new HashMap<String, QueryParameter>();
      auditService.logAuditEntryForQuery(parameters, query, queryParameters);
      if (mediaTypeHandler.isMediaTypeApplicationCSV(parameters.get(MEDIA_TYPE))) {
         streamDataAsCSV(parameters, query, httpServletResponse, queryParameters);
         return null;
      }
      return runSimpleQuery(query, getRequestId(parameters), queryParameters, getLoadBalancingPolicy(parameters));
   }

   /**
    * This method sets up the appropriate headers etc for and executes
    * streaming the csv data into the response.
    *
    * @param parameters      the parameters from resource layer
    * @param query           the query
    * @param response
    * @param queryParameters
    */
   private void streamDataAsCSV(final MultivaluedMap<String, String> parameters, final String query,
                                final HttpServletResponse response, final Map<String, QueryParameter> queryParameters) {
      response.setContentType("application/csv");
      response.setHeader("Content-disposition", "attachment; filename=export.csv");
      try {
         this.streamingDataService.streamDataAsCsv(query, queryParameters, "0", "0",
                 getLoadBalancingPolicy(parameters), response.getOutputStream());
      } catch (final IOException e) {
         ServicesLogger.error(getClass().getName(), "streamDataAsCSV", e);
      }
   }

   /**
    * @param parameters the parameters from resource layer
    *
    * @return
    */
   private LoadBalancingPolicy getLoadBalancingPolicy(final MultivaluedMap<String, String> parameters) {
      return loadBalancingPolicyService.getLoadBalancingPolicy(parameters);
   }

   /**
    * @param parameters the parameters from resource layer
    *
    * @return
    */
   private String getRequestId(final MultivaluedMap<String, String> parameters) {
      return parameters.getFirst(REQUEST_ID);
   }

   /** @param auditService the auditService to set */
   public void setAuditService(final AuditService auditService) {
      this.auditService = auditService;
   }

   /** @param csvResponseBuilder the csvResponseBuilder to set */
   public void setCsvResponseBuilder(final CSVResponseBuilder csvResponseBuilder) {
      this.csvResponseBuilder = csvResponseBuilder;
   }

   /** @param streamingDataService the streamingDataService to set */
   public void setStreamingDataService(final StreamingDataService streamingDataService) {
      this.streamingDataService = streamingDataService;
   }

   /** @param dataService the dataService to set */
   public void setDataService(final DataService dataService) {
      this.dataService = dataService;
   }

   /** @param loadBalancingPolicyService the loadBalancingPolicyService to set */
   public void setLoadBalancingPolicyService(final LoadBalancingPolicyService loadBalancingPolicyService) {
      this.loadBalancingPolicyService = loadBalancingPolicyService;
   }

   /** @param mediaTypeHandler the mediaTypeHandler to set */
   public void setMediaTypeHandler(final MediaTypeHandler mediaTypeHandler) {
      this.mediaTypeHandler = mediaTypeHandler;
   }

   /** @param templateMappingEngine the templateMappingEngine to set */
   public void setTemplateMappingEngine(final TemplateMappingEngine templateMappingEngine) {
      this.templateMappingEngine = templateMappingEngine;
   }

   /** @param templateUtils the templateUtils to set */
   public void setTemplateUtils(final TemplateUtils templateUtils) {
      this.templateUtils = templateUtils;
   }

    protected DataService getDataService() {
        return dataService;
    }

}
