/**
 * -----------------------------------------------------------------------
 *     Copyright (C) 2011 LM Ericsson Limited.  All rights reserved.
 * -----------------------------------------------------------------------
 */
package com.ericsson.eniq.events.server.serviceprovider.impl;

import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MultivaluedMap;

import com.ericsson.eniq.events.server.common.TechPackList;
import com.ericsson.eniq.events.server.common.tablesandviews.AggregationTableInfo;
import com.ericsson.eniq.events.server.datasource.loadbalancing.LoadBalancingPolicy;
import com.ericsson.eniq.events.server.query.QueryParameter;
import com.ericsson.eniq.events.server.utils.FormattedDateTimeRange;

/**
 * The methods declared in this interface are to be defined in all classes extending the class GenericService.
 * This is to enforce definitions for these functions that are specific to each type of service while common logic is to
 * be placed in GenericService.
 *
 * @author eflatib
 * @since 2011
 */
public interface GenericServiceInterface {

    /**
     * Execute the SQL query against the database.
     * Logic common to all services.
     *
     * @param query               the SQL query to execute
     * @param requestId           request ID
     * @param queryParameters     parameters for the SQL query
     * @param loadBalancingPolicy load balancing policy to use when selecting SQL connection to use for query
     * @param serviceSpecificDataServiceParameters
     *                            the parameters to the call on the data service layer that are specific to this service
     * @return the result of the query in JSON format
     */
    String runQuery(final String query, String requestId, Map<String, QueryParameter> queryParameters,
            LoadBalancingPolicy loadBalancingPolicy, final Map<String, Object> serviceSpecificDataServiceParameters);

    /**
     * Fetch the template path for this service
     * This is used when looking up the templateMap.csv
     *
     * @return key for the templateMap.csv file eg EVENT_ANALYSIS or SUBBI/SUBSCRIBER_DETAILS
     */
    String getTemplatePath();

    /**
     * Put together the template parameters that are specific to this query
     * These template parameters will be added to the common template parameters, all of which are pumped into
     * the template at the query generation stage
     *
     * @param requestParameters request parameters provided by user
     * @param dateTimeRange     date time range for query
     * @param techPackList      the list of tech pack tables and views that should be used for the query
     *                          Included as a parameter to this method so that each sub Service can
     *                          access the techPackList object to get the specific tables and views that
     *                          it needs for its query
     * @return map of the service specific template parameters
     */
    Map<String, Object> getServiceSpecificTemplateParameters(MultivaluedMap<String, String> requestParameters,
            FormattedDateTimeRange dateTimeRange, TechPackList techPackList);

    /**
     * Return the parameters to the call on the data service layer that are specific to this service or query
     * Examples are timeColumn - some services require this to be passed down to the data service layer.
     * The map of parameters returned is used in the runQuery() method above
     *
     * @param requestParameters the request parameters provided by the user
     * @return map of parameters for the data service layer
     */
    Map<String, Object> getServiceSpecificDataServiceParameters(final MultivaluedMap<String, String> requestParameters);

    /**
     * Implementations that require additional query parameters should provide these parameters by implementing this
     * method to return these additional parameters for the SQL query
     * <p/>
     * Ideally, this method should return only a list of request parameters that are injected directly into the SQL
     * query (the framework could then take care of the injection), but due to legacy code, some queries inject
     * some more complicated parameters into the SQL, see MultipleRankingResource for an example
     *
     * @param requestParameters request parameters provided by user
     * @return the additional query parameters that should be added on query execution
     */
    Map<String, QueryParameter> getServiceSpecificQueryParameters(final MultivaluedMap<String, String> requestParameters);

    /**
     * This method returns if any feature specific service classes should apply data tiering concept or not. 
     * 
     * Any service classes returning error and success data together need to override this interface to return true 
     * if they want to use 15MIN success table and RAW error table for raw queries (<=2 hour).
     * 
     * @return true or false
     */
    boolean isDataTieredService(final MultivaluedMap<String, String> parameters);

    /**
     * Return the list of parameters that must be present in the URL for this service
     *
     * @return the parameters required for this query
     */
    List<String> getRequiredParametersForQuery();

    /**
     * Return the display parameters that are valid for this service/query eg GRID, CHART
     *
     * @return the display parameters which are valid for this service/query
     */
    MultivaluedMap<String, String> getStaticParameters();

    /**
     * Fetch the drill down type for the service/query
     * This is only required if the templateMap.csv uses the DRILLTYPE column to distinguish between different queries
     *
     * @param requestParameters request parameters provided by user
     * @return the drill down type for service/query
     */
    String getDrillDownTypeForService(MultivaluedMap<String, String> requestParameters);

    /**
     * Fetch the aggregation view applicable for this view/query (and type, if applicable)
     * <p/>
     * The return is an AggregationTableInfo that should be used (for the given node type if provided) - this contains information
     * on an aggregation key, and on the timeranges that the aggregation is available for
     *
     * @param type type of node, eg TAC or APN (not required if there is only one aggregation possible for service)
     * @return map of aggregation views
     */
    AggregationTableInfo getAggregationView(String type);

    /**
     * Fetch the list of tech packs that should be used in this query
     *
     * @param requestParameters request parameters provided by user
     * @return the list of tech packs that are applicable to this tech pack
     */
    List<String> getApplicableTechPacks(MultivaluedMap<String, String> requestParameters);

    /**
     * Some aggregation queries use raw tables ie if the number of impacted subscribers is required in the query result
     *
     * @return boolean  true if raw tables are required for aggregation queries for this service, false otherwise
     */
    boolean areRawTablesRequiredForAggregationQueries();

    /**
     * Get the maximum result size that should be returned for this query
     *
     * @return int
     */
    int getMaxAllowableSize();

    /**
     * @param requestParameters request parameters provided by user
     * @return boolean          true if the parameter values should be checked to see if they are valid values
     */
    boolean requiredToCheckValidParameterValue(final MultivaluedMap<String, String> requestParameters);

    /**
     * Get table suffix key
     *
     * @return table suffix key
     */
    String getTableSuffixKey();

    /**
     * Get the measurement types of tech pack tables
     *
     * @return List<String> measurement types
     */
    List<String> getMeasurementTypes();

    /**
     * Get the raw table key(s)
     *
     * @return List<String> raw table keys
     */
    List<String> getRawTableKeys();

    /**
     * Get the time column indices for a service (Time columns contain data/time data).
     * Results for specified time columns are translated from UTC to local time using the offset.
     * The default implementation of this method returns null, and is implemented in in GenericService
     * Services that include time columns (e.g Event Time) as part of their results, should override this method.
     *
     * @return List<Integer> of column indices, where the columns contain date/times
     */
    List<Integer> getTimeColumnIndices();
}
