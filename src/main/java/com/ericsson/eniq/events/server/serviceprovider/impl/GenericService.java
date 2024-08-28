/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2014
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.eniq.events.server.serviceprovider.impl;

import static com.ericsson.eniq.events.server.common.ApplicationConstants.*;
import static com.ericsson.eniq.events.server.logging.performance.ServicesPerformanceThreadLocalHolder.*;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

import javax.ejb.EJB;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.*;

import org.apache.commons.lang.StringUtils;

import com.ericsson.eniq.events.server.common.*;
import com.ericsson.eniq.events.server.common.exception.ServiceException;
import com.ericsson.eniq.events.server.datasource.loadbalancing.LoadBalancingPolicy;
import com.ericsson.eniq.events.server.kpi.KPI;
import com.ericsson.eniq.events.server.logging.ServicesLogger;
import com.ericsson.eniq.events.server.logging.performance.ServicePerformanceTraceLogger;
import com.ericsson.eniq.events.server.logging.performance.ServicesPerformanceThreadLocalHolder;
import com.ericsson.eniq.events.server.query.*;
import com.ericsson.eniq.events.server.serviceprovider.Service;
import com.ericsson.eniq.events.server.services.DataService;
import com.ericsson.eniq.events.server.services.StreamingDataService;
import com.ericsson.eniq.events.server.services.datatiering.DataTieringHandler;
import com.ericsson.eniq.events.server.services.exclusivetacs.ExclusiveTACHandler;
import com.ericsson.eniq.events.server.utils.*;
import com.ericsson.eniq.events.server.utils.config.ApplicationConfigManager;
import com.ericsson.eniq.events.server.utils.datetime.DateTimeHelper;
import com.ericsson.eniq.events.server.utils.json.JSONUtils;
import com.ericsson.eniq.events.server.utils.parameterchecking.ParameterChecker;
import com.ericsson.eniq.events.server.utils.parameterchecking.RequiredParameters;
import com.ericsson.eniq.events.server.utils.techpacks.*;

/**
 * The base class for all resource services in the services layer. This class is responsible for controlling the flow through the services layer ie
 * the order in which steps such as logging, parameter checking, query generation and query execution are performed.
 * <p/>
 * Only resource/query specific logic should exist in the subclasses of this class.
 * 
 * @TODO update how tech packs are licensed (this was updated in a hurry for delivery to Smarttone) All techpack licensing should happen in one place
 *       (currently called from here, in getAndRunQuery() and in TechPackListFactory). Also to consider - having some sort of Tech Pack object, which
 *       is intercepted by some sort of licensing interceptor, which filters out the unlicensed tech packs
 * 
 * @author EEMECOY
 */
public abstract class GenericService implements Service, GenericServiceInterface {

    @EJB
    private ServicePerformanceTraceLogger performanceTrace;

    @EJB
    private AuditService auditService;

    @EJB
    private CSVResponseBuilder csvResponseBuilder;

    @EJB
    private StreamingDataService streamingDataService;

    @EJB
    private DataService dataService;

    @EJB
    private LoadBalancingPolicyService loadBalancingPolicyService;

    @EJB
    private ParameterChecker parameterChecker;

    @EJB
    private DateTimeHelper dateTimeHelper;

    @EJB(beanName = "QueryGenerator")
    private IQueryGenerator queryGenerator;

    @EJB
    private QueryUtils queryUtils;

    @EJB
    protected TechPackListFactory techPackListFactory;

    @EJB
    private MediaTypeHandler mediaTypeHandler;

    @EJB
    private TechPackDescriptionMappingsService techPackDescriptionMappingsService;

    @EJB
    protected ApplicationConfigManager applicationConfigManager;

    @EJB
    private ExclusiveTACHandler exclusiveTACHandler;

    @EJB
    private TechPackLicensingService techPackLicensingService;

    @EJB
    private DataTieringHandler dataTieringHandler;

    @Override
    public String getData(final MultivaluedMap<String, String> parameters) {
        return getAndRunQuery(parameters, null);
    }

    @Override
    public Response getDataAsCSV(final MultivaluedMap<String, String> parameters, final HttpServletResponse response) {
        getAndRunQuery(parameters, response);
        return csvResponseBuilder.buildHttpResponseForCSVData();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.ericsson.eniq.events.server.serviceprovider.impl.GenericServiceInterface#runQuery(java.lang.String, java.lang.String, java.util.Map,
     * com.ericsson.eniq.events.server.datasource.loadbalancing.LoadBalancingPolicy, java.util.Map)
     */
    @Override
    public String runQuery(final String query, final String requestId, final Map<String, QueryParameter> queryParameters,
                           final LoadBalancingPolicy loadBalancingPolicy, final Map<String, Object> serviceSpecificDataServiceParameters) {
        final String tzOffset = (String) serviceSpecificDataServiceParameters.get(TZ_OFFSET);
        return getDataService().getGridData(requestId, query, queryParameters, getTimeColumnIndices(), tzOffset, loadBalancingPolicy);
    }

    /**
     * @param parameters
     *            parameters from the resource layer
     * @param httpServletResponse
     *            response object (can be null, used when streaming csv response)
     * @return json response, null if request is for csv data as this is streamed to the response
     */
    private String getAndRunQuery(final MultivaluedMap<String, String> parameters, final HttpServletResponse httpServletResponse) {
        try {
            final String errorMessage = getAndCheckRequiredParameters(parameters);
            if (StringUtils.isNotEmpty(errorMessage)) {
                return errorMessage;
            }
            final List<String> licensedTechPacks = techPackLicensingService.getLicensedTechPacks(getApplicableTechPacks(parameters));
            if (licensedTechPacks.isEmpty()) {
                return getJSONErrorForNoLicensedTechPacksPresent(parameters);
            }
            final FormattedDateTimeRange formattedDateTimeRange = translateDateTimeParameters(parameters, licensedTechPacks);
            final TechPackList techPackList = createTechPackList(formattedDateTimeRange, parameters);
            if (shouldReportErrorAboutRawTables(techPackList)) {
                return JSONUtils.JSONEmptySuccessResult();
            }

            final String query = getQuery(parameters, formattedDateTimeRange, techPackList);
            if (StringUtils.isBlank(query)) {
                return JSONUtils.JSONBuildFailureError();
            }
            return logAndRunQuery(httpServletResponse, parameters, formattedDateTimeRange, query);
        } finally {
            postQueryTracing();
            releaseAllResources();
        }
    }

    /**
     * @param parameters
     * @return
     */
    protected String getAndCheckRequiredParameters(final MultivaluedMap<String, String> parameters) {
        final RequiredParameters requiredParameters = new RequiredParameters(getStaticParameters(), getRequiredParametersForQuery(),
                requiredToCheckValidParameterValue(parameters));
        return parameterChecker.performValidityChecking(requiredParameters, parameters, getApplicableTechPacks(parameters));
    }

    private String getJSONErrorForNoLicensedTechPacksPresent(final MultivaluedMap<String, String> requestParameters) {
        final List<String> featureDescriptions = techPackDescriptionMappingsService
                .getFeatureDescriptionsForTechPacks(getApplicableTechPacks(requestParameters));
        return JSONUtils.JSONNoLicensedFeaturesError(featureDescriptions);
    }

    private String logAndRunQuery(final HttpServletResponse httpServletResponse, final MultivaluedMap<String, String> parameters,
                                  final FormattedDateTimeRange formattedDateTimeRange, final String query) {
        final Map<String, QueryParameter> queryParameters = getQueryParameters(parameters, formattedDateTimeRange);
        auditService.logAuditEntryForQuery(parameters, query, queryParameters);
        if (mediaTypeHandler.isMediaTypeApplicationCSV(parameters.get(MEDIA_TYPE))) {
            streamDataAsCSV(parameters, parameters.getFirst(TZ_OFFSET), getTimeColumnIndices(), query, httpServletResponse, queryParameters);
            return null;
        }
        return runQuery(query, getRequestId(parameters), queryParameters, getLoadBalancingPolicy(parameters),
                getServiceSpecificDataServiceParameters(parameters));
    }

    /**
     * This method sets up the appropriate headers etc for and executes streaming the csv data into the response.
     * 
     * @param parameters
     *            the parameters from resource layer
     * @param tzOffset
     *            the tz offset
     * @param timeColumn
     *            the time column
     * @param query
     *            the query
     * @param response
     * @param queryParameters
     */
    private void streamDataAsCSV(final MultivaluedMap<String, String> parameters, final String tzOffset, final List<Integer> timeColumnIndexes,
                                 final String query, final HttpServletResponse response, final Map<String, QueryParameter> queryParameters) {
        response.setContentType("application/csv");
        response.setHeader("Content-disposition", "attachment; filename=export.csv");
        try {
            this.streamingDataService.streamDataAsCsv(query, queryParameters, timeColumnIndexes, tzOffset, getLoadBalancingPolicy(parameters),
                    response.getOutputStream());
        } catch (final IOException e) {
            ServicesLogger.error(getClass().getName(), "streamDataAsCSV", e);
        }
    }

    /**
     * Returns true if 1) raw tables have not been found for the specified tech packs, and raw tables are required for the aggregation queries or 2)
     * raw tables have not been found for the specified tech packs, and this query is using the raw tables (eg its for the last 5 minutes)
     * <p/>
     * Returns false otherwise
     * 
     * @param techPackList
     * @return boolean
     */
    boolean shouldReportErrorAboutRawTables(final TechPackList techPackList) {
        if (!techPackList.hasRawTables()) {
            if (areRawTablesRequiredForAggregationQueries()) {
                return true;
            }
            if (!techPackList.shouldQueryUseAggregationTables()) {
                return true;
            }
        }
        return false;
    }

    private Map<String, QueryParameter> getQueryParameters(final MultivaluedMap<String, String> requestParameters,
                                                           final FormattedDateTimeRange formattedDateTimeRange) {
        final Map<String, QueryParameter> queryParameters = new HashMap<String, QueryParameter>();
        queryParameters.putAll(getServiceSpecificQueryParameters(requestParameters));
        queryParameters.putAll(this.queryUtils.getQueryParameters(requestParameters, formattedDateTimeRange, getTemplatePath()));
        return queryParameters;
    }

    @Override
    public boolean isDataTieredService(final MultivaluedMap<String, String> parameters) {
        return false;
    }

    protected String getQuery(final MultivaluedMap<String, String> parameters, final FormattedDateTimeRange formattedDateTimeRange,
                              final TechPackList techPackList) {
        preQueryTracing(parameters);
        final QueryGeneratorParameters queryGeneratorParameters = constructQueryGeneratorParameters(formattedDateTimeRange, parameters, techPackList);
        return queryGenerator.getQuery(queryGeneratorParameters);
    }

    protected TechPackList createTechPackList(final FormattedDateTimeRange formattedDateTimeRange,
                                              final MultivaluedMap<String, String> requestParameters) {
        final RequestParametersWrapper requestParametersWrapper = new RequestParametersWrapper(requestParameters);
        final String type = requestParametersWrapper.getType();
        TechPackList tpList = null;
        final List<String> rawTableKeys = getRawTableKeys();
        final List<String> measurementTypes = getMeasurementTypes();
        if (forceAggregationType() != null) {
            tpList = techPackListFactory.createTechPackListWithSpecifiedAggregation(getApplicableTechPacks(requestParameters),
                    formattedDateTimeRange, getAggregationView(type), forceAggregationType());
        } else if (rawTableKeys == null && measurementTypes == null) {
            tpList = techPackListFactory.createTechPackList(getApplicableTechPacks(requestParameters), formattedDateTimeRange,
                    getAggregationView(type));
        } else if (rawTableKeys != null && measurementTypes == null) {
            tpList = techPackListFactory.createTechPackListWithKeys(getApplicableTechPacks(requestParameters), rawTableKeys, formattedDateTimeRange,
                    getAggregationView(type));
        } else if (rawTableKeys == null && measurementTypes != null) {
            tpList = techPackListFactory.createTechPackListWithMeasuermentType(getApplicableTechPacks(requestParameters), measurementTypes,
                    formattedDateTimeRange, getAggregationView(type), getTableSuffixKey());
        }
        return tpList;
    }

    private QueryGeneratorParameters constructQueryGeneratorParameters(final FormattedDateTimeRange formattedDateTimeRange,
                                                                       final MultivaluedMap<String, String> requestParameters,
                                                                       final TechPackList techPackList) {
        final Map<String, Object> templateParam = getServiceSpecificTemplateParameters(requestParameters, formattedDateTimeRange, techPackList);
        if (StringUtils.isNotBlank(requestParameters.getFirst(MEDIA_TYPE))
                && mediaTypeHandler.isMediaTypeApplicationCSV(requestParameters.get(MEDIA_TYPE))) {
            final String tzOffset = requestParameters.getFirst(TZ_OFFSET);
            final char sign = tzOffset.charAt(0);
            final String first = tzOffset.substring(1, 3);
            final String last = tzOffset.substring(3, 5);
            final Integer total = (Integer.parseInt(first) * 60 + Integer.parseInt(last));
            final String tzOffsetQuery = sign + total.toString();

            templateParam.put(CSV_PARAM, new Boolean(true));
            templateParam.put(TZ_OFFSET, tzOffsetQuery);
        }

        return new QueryGeneratorParameters(getTemplatePath(), requestParameters, templateParam, formattedDateTimeRange,
                getDrillDownTypeForService(requestParameters), getMaxAllowableSize(), techPackList, getKPIList(),
                isExclusiveTacRelated(requestParameters), dataTieringHandler.useDataTieringView(formattedDateTimeRange,
                        isDataTieredService(requestParameters), techPackList.getTechPacks()));
    }

    protected boolean isExclusiveTacRelated(final MultivaluedMap<String, String> requestParameters) {
        return exclusiveTACHandler.queryIsExclusiveTacRelated(requestParameters);
    }

    /**
     * Default implementation of getKPIList() to reduce impact on existing services. This parameter
     * 
     * @return
     */
    public List<KPI> getKPIList() {
        return new ArrayList<KPI>();
    }

    private LoadBalancingPolicy getLoadBalancingPolicy(final MultivaluedMap<String, String> requestParameters) {
        return loadBalancingPolicyService.getLoadBalancingPolicy(requestParameters);
    }

    protected FormattedDateTimeRange translateDateTimeParameters(final MultivaluedMap<String, String> parameters, final List<String> licensedTechPacks) {
        final FormattedDateTimeRange timeRange = dateTimeHelper.translateDateTimeParameters(parameters, licensedTechPacks);

        if (dataTieringHandler.appplyLatencyForDataTiering(timeRange, isDataTieredService(parameters), licensedTechPacks, parameters)) {
            return dateTimeHelper.getDataTieredDateTimeRange(timeRange);
        }

        return timeRange;
    }

    private void postQueryTracing() {
        setRequestEndTime(Calendar.getInstance().getTimeInMillis());
        performanceTrace.detailed(Level.INFO, getContextInfo());
    }

    private void preQueryTracing(final MultivaluedMap<String, String> parameters) {
        setRequestStartTime(Calendar.getInstance().getTimeInMillis());
        ServicesPerformanceThreadLocalHolder.setUriInfo(parameters.getFirst(REQUEST_URI));
        auditService.logAuditEntryForURI(parameters);
    }

    /**
     * Given a list of request parameters that should be injected directly into the query, construct a QueryParameter object for each of these request
     * parameters.
     * 
     * @param requestParametersToIncludeInQuery
     *            list of the request parameters that should be injected directly into the SQL query with the same parameter name
     * @param parameters
     *            parameters provided by resource layer
     * @return map of query parameters and their values to be used in SQL query
     */
    protected Map<String, QueryParameter> mapRequestParametersDirectlyToQueryParameters(final List<String> requestParametersToIncludeInQuery,
                                                                                        final MultivaluedMap<String, String> parameters) {
        final HashMap<String, QueryParameter> queryParameters = new HashMap<String, QueryParameter>();
        for (final String requestParameter : requestParametersToIncludeInQuery) {
            final QueryParameter queryParameter = queryUtils.createQueryParameter(requestParameter, parameters.getFirst(requestParameter));
            queryParameters.put(requestParameter, queryParameter);
        }
        return queryParameters;
    }

    /**
     * Returns the decoded query parameters.
     * 
     * @param uriInfo
     * @return Decoded query parameters
     */
    protected MultivaluedMap<String, String> getDecodedQueryParameters(final UriInfo uriInfo) {
        return uriInfo.getQueryParameters(true);
    }

    protected DataService getDataService() {
        return dataService;
    }

    String getRequestId(final MultivaluedMap<String, String> parameters) {
        return parameters.getFirst(REQUEST_ID);
    }

    /**
     * Update templateParameters with group definition.
     * 
     * @param templateParameters
     *            template parameters
     * @param requestParameters
     *            parameters provided by resource layer
     */
    protected void updateTemplateParametersWithGroupDefinition(final Map<String, Object> templateParameters,
                                                               final MultivaluedMap<String, String> requestParameters) {
        if (requestParameters.containsKey(GROUP_NAME_PARAM)) {
            final Map<String, Group> templateGroupDefs = dataService.getGroupsForTemplates();
            templateParameters.put(GROUP_DEFINITIONS, templateGroupDefs);
            final String groupName = requestParameters.getFirst(GROUP_NAME_PARAM);
            if (groupName == null || groupName.length() == 0) {
                throw new ServiceException(GROUP_NAME_PARAM + " undefined");
            }
        }
    }

    /**
     * @param performanceTrace
     *            the performanceTrace to set
     */
    public void setPerformanceTrace(final ServicePerformanceTraceLogger performanceTrace) {
        this.performanceTrace = performanceTrace;
    }

    /**
     * @param auditService
     *            the auditService to set
     */
    public void setAuditService(final AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * @param csvResponseBuilder
     *            the csvResponseBuilder to set
     */
    public void setCsvResponseBuilder(final CSVResponseBuilder csvResponseBuilder) {
        this.csvResponseBuilder = csvResponseBuilder;
    }

    /**
     * This function is used to get the interval time for the template. (for example, Event Volume & Network Event Volume need a interval for result
     * column time.)
     * 
     * @param dateTimeRange
     * @return interval time when timerange = TR_1 & TR_2 return 1 min when timerange = TR_3 return 15 mins when timerange = TR_4 return 1440 mins
     *         (1day)
     */
    public int getInterval(final FormattedDateTimeRange dateTimeRange) {
        final String timerange = dateTimeHelper.getEventDataSourceType(dateTimeRange).toString();
        if (timerange.equalsIgnoreCase(FIFTEEN_MINUTES)) {
            return 15;
        } else if (timerange.equalsIgnoreCase(DAY)) {
            return 1440;
        }
        return 1;
    }

    /**
     * @param streamingDataService
     *            the streamingDataService to set
     */
    public void setStreamingDataService(final StreamingDataService streamingDataService) {
        this.streamingDataService = streamingDataService;
    }

    /**
     * @param dataService
     *            the dataService to set
     */
    public void setDataService(final DataService dataService) {
        this.dataService = dataService;
    }

    /**
     * @param loadBalancingPolicyService
     *            the loadBalancingPolicyService to set
     */
    public void setLoadBalancingPolicyService(final LoadBalancingPolicyService loadBalancingPolicyService) {
        this.loadBalancingPolicyService = loadBalancingPolicyService;
    }

    /**
     * @param parameterChecker
     *            the parameterChecker to set
     */
    public void setParameterChecker(final ParameterChecker parameterChecker) {
        this.parameterChecker = parameterChecker;
    }

    /**
     * @param dateTimeHelper
     *            the dateTimeHelper to set
     */
    public void setDateTimeHelper(final DateTimeHelper dateTimeHelper) {
        this.dateTimeHelper = dateTimeHelper;
    }

    protected DateTimeHelper getDateTimeHelper() {
        return dateTimeHelper;
    }

    /**
     * This is used to override the query generation method. Can be used to generate KPI queries using the KPI framework
     * 
     * @param queryGenerator
     *            The query generation logic
     */
    public void setQueryGenerator(final IQueryGenerator queryGenerator) {
        this.queryGenerator = queryGenerator;
    }

    public void setQueryUtils(final QueryUtils queryUtils) {
        this.queryUtils = queryUtils;
    }

    protected QueryUtils getQueryUtils() {
        return queryUtils;
    }

    public void setTechPackListFactory(final TechPackListFactory techPackListFactory) {
        this.techPackListFactory = techPackListFactory;
    }

    public void setMediaTypeHandler(final MediaTypeHandler mediaTypeHandler) {
        this.mediaTypeHandler = mediaTypeHandler;
    }

    /**
     * @param techPackDescriptionMappingsService
     *            the techPackDescriptionMappingsService to set
     */
    public void setTechPackDescriptionMappingsService(final TechPackDescriptionMappingsService techPackDescriptionMappingsService) {
        this.techPackDescriptionMappingsService = techPackDescriptionMappingsService;
    }

    /**
     * @return the applicationConfigManager
     */
    protected ApplicationConfigManager getApplicationConfigManager() {
        return applicationConfigManager;
    }

    /**
     * @param applicationConfigManager
     *            the applicationConfigManager to set
     */
    public void setApplicationConfigManager(final ApplicationConfigManager applicationConfigManager) {
        this.applicationConfigManager = applicationConfigManager;
    }

    protected boolean successRawEnabled() {
        return applicationConfigManager.isSuccessRawEnabled();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.ericsson.eniq.events.server.serviceprovider.impl.GenericServiceInterface#getTimeColumnIndices()
     */
    @Override
    public List<Integer> getTimeColumnIndices() {
        return null;
    }

    public EventDataSourceType forceAggregationType() {
        return null;
    }

    /**
     * @return the exclusiveTACHandler
     */
    protected ExclusiveTACHandler getExclusiveTACHandler() {
        return exclusiveTACHandler;
    }

    /**
     * @param exclusiveTACHandler
     *            the exclusiveTACHandler to set
     */
    public void setExclusiveTACHandler(final ExclusiveTACHandler exclusiveTACHandler) {
        this.exclusiveTACHandler = exclusiveTACHandler;
    }

    public void setTechPackLicensingService(final TechPackLicensingService techPackLicensingService) {
        this.techPackLicensingService = techPackLicensingService;

    }

    public void setDataTieringHandler(final DataTieringHandler dataTieringHandler) {
        this.dataTieringHandler = dataTieringHandler;
    }
}
