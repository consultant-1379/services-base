/**
 * -----------------------------------------------------------------------
 *     Copyright (C) 2011 LM Ericsson Limited.  All rights reserved.
 * -----------------------------------------------------------------------
 */
package com.ericsson.eniq.events.server.serviceprovider.impl;

import static com.ericsson.eniq.events.server.common.ApplicationConstants.*;
import static com.ericsson.eniq.events.server.common.TechPackData.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MultivaluedMap;

import org.jmock.Expectations;
import org.junit.Before;
import org.junit.Test;

import com.ericsson.eniq.events.server.common.TechPackList;
import com.ericsson.eniq.events.server.common.TechPackRepresentation;
import com.ericsson.eniq.events.server.common.tablesandviews.AggregationTableInfo;
import com.ericsson.eniq.events.server.datasource.loadbalancing.LoadBalancingPolicy;
import com.ericsson.eniq.events.server.logging.performance.ServicePerformanceTraceLogger;
import com.ericsson.eniq.events.server.query.QueryGenerator;
import com.ericsson.eniq.events.server.query.QueryGeneratorParameters;
import com.ericsson.eniq.events.server.query.QueryParameter;
import com.ericsson.eniq.events.server.services.datatiering.DataTieringHandler;
import com.ericsson.eniq.events.server.services.exclusivetacs.ExclusiveTACHandler;
import com.ericsson.eniq.events.server.test.common.BaseJMockUnitTest;
import com.ericsson.eniq.events.server.utils.AuditService;
import com.ericsson.eniq.events.server.utils.FormattedDateTimeRange;
import com.ericsson.eniq.events.server.utils.QueryUtils;
import com.ericsson.eniq.events.server.utils.datetime.DateTimeHelper;
import com.ericsson.eniq.events.server.utils.parameterchecking.ParameterChecker;
import com.ericsson.eniq.events.server.utils.parameterchecking.RequiredParameters;
import com.ericsson.eniq.events.server.utils.techpacks.TechPackDescriptionMappingsService;
import com.ericsson.eniq.events.server.utils.techpacks.TechPackLicensingService;
import com.ericsson.eniq.events.server.utils.techpacks.TechPackListFactory;
import com.sun.jersey.core.util.MultivaluedMapImpl;

/**
 * @author EEMECOY
 */
public class GenericServiceTest extends BaseJMockUnitTest {

    private static final String JSON_ERROR_RESULT_PREFIX = "{\"success\":\"false\",\"errorDescription\":";

    private StubbedGenericService service;

    boolean areRawTablesRequiredForAllQueries;

    boolean requiredToCheckValidParameterValue;

    TechPackListFactory techPackListFactory;

    private final String EMPTY_JSON_SUCCESS_RESULT = "{\"success\":\"true\",\"errorDescription\":\"\",\"data\":[]}";

    QueryGenerator queryGenerator;

    private final String BUILD_QUERY_FAILURE_JSON = JSON_ERROR_RESULT_PREFIX + "\"Failed to build query\"}";

    public List<String> applicableTechPacks = new ArrayList<String>();

    ParameterChecker parameterChecker;

    TechPackDescriptionMappingsService techPackDescriptionMappingsService;

    QueryUtils queryUtils;

    TechPackLicensingService techPackLicensingService;

    DataTieringHandler dataTieringHandler;

    TechPackList techpackList;

    @Before
    public void setup() {
        service = new StubbedGenericService();
        final ServicePerformanceTraceLogger mockedPerformanceTrace = createAndIgnore(ServicePerformanceTraceLogger.class);
        service.setPerformanceTrace(mockedPerformanceTrace);
        final DateTimeHelper mockedDateTimeHelper = createAndIgnore(DateTimeHelper.class);
        service.setDateTimeHelper(mockedDateTimeHelper);
        techPackListFactory = mockery.mock(TechPackListFactory.class);
        service.setTechPackListFactory(techPackListFactory);
        final AuditService auditService = createAndIgnore(AuditService.class);
        service.setAuditService(auditService);
        parameterChecker = mockery.mock(ParameterChecker.class);
        service.setParameterChecker(parameterChecker);
        queryGenerator = mockery.mock(QueryGenerator.class);
        service.setQueryGenerator(queryGenerator);
        techPackDescriptionMappingsService = mockery.mock(TechPackDescriptionMappingsService.class);
        service.setTechPackDescriptionMappingsService(techPackDescriptionMappingsService);
        queryUtils = mockery.mock(QueryUtils.class);
        service.setQueryUtils(queryUtils);
        final ExclusiveTACHandler exclusiveTACHandler = mockery.mock(ExclusiveTACHandler.class);
        mockery.checking(new Expectations() {
            {
                allowing(exclusiveTACHandler).queryIsExclusiveTacRelated(with(any(MultivaluedMap.class)));
            }
        });
        service.setExclusiveTACHandler(exclusiveTACHandler);
        techPackLicensingService = mockery.mock(TechPackLicensingService.class);
        service.setTechPackLicensingService(techPackLicensingService);
        dataTieringHandler = mockery.mock(DataTieringHandler.class);
        service.setDataTieringHandler(dataTieringHandler);
        mockery.checking(new Expectations() {
            {
                allowing(dataTieringHandler).appplyLatencyForDataTiering(with(any(FormattedDateTimeRange.class)),
                        with(any(boolean.class)), with(any(List.class)), with(any(MultivaluedMap.class)));
                //                will(returnValue(false));
                allowing(dataTieringHandler).useDataTieringView(with(any(FormattedDateTimeRange.class)),
                        with(any(boolean.class)), with(any(Collection.class)));
            }
        });

        //reset field
        areRawTablesRequiredForAllQueries = false;
        applicableTechPacks.add(EVENT_E_SGEH);
        applicableTechPacks.add(EVENT_E_LTE);
    }

    @Test
    public void testBuildFailureJSONReturnedForEmptyQuery() {
        expectCallsOnTechPackListFactoryAndTechPackListResult(1, true);
        final String blankQuery = "";
        expectCallOnQueryGenerator(blankQuery);
        expectCallOnParameterChecker();
        expectCallOnTechPackLicensingService(applicableTechPacks, applicableTechPacks);
        final MultivaluedMap<String, String> parameters = new MultivaluedMapImpl();
        final String result = service.getData(parameters);
        assertThat(result, is(BUILD_QUERY_FAILURE_JSON));
    }

    private void expectCallOnTechPackLicensingService(final List<String> techPacks, final List<String> licensedTechPacks) {
        mockery.checking(new Expectations() {
            {
                one(techPackLicensingService).getLicensedTechPacks(techPacks);
                will(returnValue(licensedTechPacks));
            }
        });

    }

    private void expectCallOnParameterChecker() {
        mockery.checking(new Expectations() {
            {
                one(parameterChecker).performValidityChecking(with(any(RequiredParameters.class)),
                        with(any(MultivaluedMap.class)), with(any(List.class)));
                will(returnValue(""));
            }
        });

    }

    private void expectCallOnQueryGenerator(final String queryToReturn) {
        mockery.checking(new Expectations() {
            {
                one(queryGenerator).getQuery(with(any(QueryGeneratorParameters.class)));
                will(returnValue(queryToReturn));
            }
        });

    }

    @Test
    public void testErrorMessageReturnedWhenNoLicensedTechPacksPresent() {
        areRawTablesRequiredForAllQueries = true;
        expectCallOnParameterChecker();
        final List<String> descriptionsForTechPacks = new ArrayList<String>();
        descriptionsForTechPacks.add("2G Feature");
        descriptionsForTechPacks.add("4G Feature");
        expectCallsOnTechPackDescriptionMappingsService(descriptionsForTechPacks);
        expectCallOnTechPackLicensingService(applicableTechPacks, Collections.<String> emptyList());
        final MultivaluedMap<String, String> parameters = new MultivaluedMapImpl();
        final String result = service.getData(parameters);
        final String expectedErrorMessage = buildExpectedErrorMessage(descriptionsForTechPacks);
        assertThat(result, is(expectedErrorMessage));
    }

    private void expectCallsOnTechPackDescriptionMappingsService(final List<String> descriptionsForTechPacks) {

        mockery.checking(new Expectations() {
            {
                one(techPackDescriptionMappingsService).getFeatureDescriptionsForTechPacks(applicableTechPacks);
                will(returnValue(descriptionsForTechPacks));
            }
        });

    }

    private String buildExpectedErrorMessage(final List<String> descriptionsForTechPacks) {

        final StringBuilder stringBuilder = new StringBuilder(JSON_ERROR_RESULT_PREFIX);
        stringBuilder.append("\"The features ");
        for (int i = 0; i < descriptionsForTechPacks.size(); i++) {
            stringBuilder.append(descriptionsForTechPacks.get(i));
            if (i < descriptionsForTechPacks.size() - 1) {
                stringBuilder.append(COMMA);
                stringBuilder.append(" ");
            }
        }
        stringBuilder.append(" are not licensed");
        stringBuilder.append("\"}");
        return stringBuilder.toString();
    }

    @Test
    public void testEmptyJsonSuccessResultReturnedWhenNoRawTablesPresentAndQueryIsForRawTimeRange() {
        areRawTablesRequiredForAllQueries = false;

        expectCallsOnTechPackListFactoryAndTechPackListResult(1, false);
        expectCallOnParameterChecker();
        expectCallOnTechPackLicensingService(applicableTechPacks, applicableTechPacks);
        final MultivaluedMap<String, String> parameters = new MultivaluedMapImpl();
        final String result = service.getData(parameters);
        assertThat(result, is(EMPTY_JSON_SUCCESS_RESULT));
    }

    @Test
    public void testEmptyJsonSuccessResultReturnedWhenNoRawTablesPresentAndAreRequiredForAllTimeRangeQueries() {
        areRawTablesRequiredForAllQueries = true;

        expectCallsOnTechPackListFactoryAndTechPackListResult(1, false);
        expectCallOnParameterChecker();
        expectCallOnTechPackLicensingService(applicableTechPacks, applicableTechPacks);
        final MultivaluedMap<String, String> parameters = new MultivaluedMapImpl();
        final String result = service.getData(parameters);
        assertThat(result, is(EMPTY_JSON_SUCCESS_RESULT));
    }

    private void expectCallsOnTechPackListFactoryAndTechPackListResult(final int numberTechPacksToReturn,
            final boolean shouldQueryUseAggregationTables) {
        final TechPackList mockedTechPackList = mockery.mock(TechPackList.class);
        final List<TechPackRepresentation> techPacks = new ArrayList<TechPackRepresentation>();
        for (int i = 0; i < numberTechPacksToReturn; i++) {
            final TechPackRepresentation techPackRepresentation = new TechPackRepresentation("");
            final List<String> rawErrTables = new ArrayList<String>();
            techPackRepresentation.setErrRawTables(rawErrTables);
            final List<String> rawSucTables = new ArrayList<String>();
            techPackRepresentation.setSucRawTables(rawSucTables);
            techPacks.add(techPackRepresentation);
        }
        mockery.checking(new Expectations() {
            {
                one(techPackListFactory).createTechPackList(with(equal(applicableTechPacks)),
                        with(any(FormattedDateTimeRange.class)), with(equal((AggregationTableInfo) null)));
                will(returnValue(mockedTechPackList));
                allowing(mockedTechPackList).hasRawTables();
                will(returnValue(false));
                allowing(mockedTechPackList).shouldQueryUseAggregationTables();
                will(returnValue(shouldQueryUseAggregationTables));
                allowing(mockedTechPackList).getTechPacks();
            }
        });

    }

    @Test
    public void testshouldReportErrorAboutRawTablesIsFalseWhenRawTablesAreRequiredAndFound() {
        areRawTablesRequiredForAllQueries = true;
        final TechPackList techPackList = createMockedTechPackList(true, false);
        assertThat(service.shouldReportErrorAboutRawTables(techPackList), is(false));
    }

    @Test
    public void testshouldReportErrorAboutRawTablesIsTrueWhenNoRawTablesFoundAndRawTablesAreRequired() {
        areRawTablesRequiredForAllQueries = true;
        final TechPackList techPackList = createMockedTechPackList(false, false);
        assertThat(service.shouldReportErrorAboutRawTables(techPackList), is(true));
    }

    @Test
    public void testshouldReportErrorAboutRawTablesIsFalseWhenRawTablesAreNotRequiredForQuery() {
        areRawTablesRequiredForAllQueries = false;
        final TechPackList techPackList = createMockedTechPackList(false, true);
        assertThat(service.shouldReportErrorAboutRawTables(techPackList), is(false));
    }

    private TechPackList createMockedTechPackList(final boolean hasRawTables,
            final boolean shouldQueryUseAggregationTables) {
        final TechPackList mockedTechPackList = mockery.mock(TechPackList.class);
        mockery.checking(new Expectations() {
            {
                allowing(mockedTechPackList).hasRawTables();
                will(returnValue(hasRawTables));
                allowing(mockedTechPackList).shouldQueryUseAggregationTables();
                will(returnValue(shouldQueryUseAggregationTables));
            }
        });
        return mockedTechPackList;

    }

    @Test
    public void testmapRequestParametersDirectlyToQueryParameters() {
        final String requestParameter = RNC_ID_PARAM;
        final String parameterValue = "RNC01";
        expectCallOnQueryUtilsCreateQueryParameters(requestParameter, parameterValue);
        final List<String> requestParametersToIncludeInQuery = new ArrayList<String>();
        requestParametersToIncludeInQuery.add(requestParameter);
        final MultivaluedMap<String, String> requestParameters = new MultivaluedMapImpl();
        requestParameters.putSingle(requestParameter, parameterValue);
        final Map<String, QueryParameter> result = service.mapRequestParametersDirectlyToQueryParameters(
                requestParametersToIncludeInQuery, requestParameters);
        assertThat(result.size(), is(1));
    }

    private void expectCallOnQueryUtilsCreateQueryParameters(final String requestParameter, final String parameterValue) {
        mockery.checking(new Expectations() {
            {
                one(queryUtils).createQueryParameter(requestParameter, parameterValue);
            }
        });

    }

    class StubbedGenericService extends GenericService {

        /* (non-Javadoc)
         * @see com.ericsson.eniq.events.server.serviceprovider.impl.GenericService#runQuery(java.lang.String, java.lang.String, java.util.Map, com.ericsson.eniq.events.server.datasource.loadbalancing.LoadBalancingPolicy, java.util.Map)
         */
        @Override
        public String runQuery(final String query, final String requestId,
                final Map<String, QueryParameter> queryParameters, final LoadBalancingPolicy loadBalancingPolicy,
                final Map<String, Object> serviceSpecificDataServiceParameters) {
            return null;
        }

        @Override
        public Map<String, Object> getServiceSpecificTemplateParameters(
                final MultivaluedMap<String, String> requestParameters, final FormattedDateTimeRange dateTimeRange,
                final TechPackList techPackList) {
            return null;
        }

        /* (non-Javadoc)
         * @see com.ericsson.eniq.events.server.serviceprovider.impl.GenericService#getTemplatePath()
         */
        @Override
        public String getTemplatePath() {
            return null;
        }

        @Override
        public Map<String, Object> getServiceSpecificDataServiceParameters(
                final MultivaluedMap<String, String> requestParameters) {
            return null;
        }

        /* (non-Javadoc)
         * @see com.ericsson.eniq.events.server.serviceprovider.impl.GenericService#getRequiredParametersForQuery()
         */
        @Override
        public List<String> getRequiredParametersForQuery() {
            return null;
        }

        /* (non-Javadoc)
         * @see com.ericsson.eniq.events.server.serviceprovider.impl.GenericService#getValidDisplayParameters()
         */
        @Override
        public MultivaluedMap<String, String> getStaticParameters() {
            return null;
        }

        /* (non-Javadoc)
         * @see com.ericsson.eniq.events.server.serviceprovider.impl.GenericService#getDrillType()
         */
        @Override
        public String getDrillDownTypeForService(final MultivaluedMap<String, String> requestParameters) {
            return null;
        }

        /* (non-Javadoc)
         * @see com.ericsson.eniq.events.server.serviceprovider.impl.GenericService#getAggregationViews()
         */
        @Override
        public AggregationTableInfo getAggregationView(final String type) {
            return null;
        }

        /* (non-Javadoc)
         * @see com.ericsson.eniq.events.server.serviceprovider.impl.GenericService#getApplicableTechPacks()
         */
        @Override
        public List<String> getApplicableTechPacks(final MultivaluedMap<String, String> requestParameters) {
            return applicableTechPacks;
        }

        /* (non-Javadoc)
         * @see com.ericsson.eniq.events.server.serviceprovider.impl.GenericService#getMaxAllowableSize()
         */
        @Override
        public int getMaxAllowableSize() {
            return 0;
        }

        /* (non-Javadoc)
         * @see com.ericsson.eniq.events.server.serviceprovider.impl.GenericService#areRawTablesRequiredForQuery()
         */
        @Override
        public boolean areRawTablesRequiredForAggregationQueries() {
            return areRawTablesRequiredForAllQueries;
        }

        /* (non-Javadoc)
         * @see com.ericsson.eniq.events.server.serviceprovider.impl.GenericService#requiredToCheckValidParameterValue()
         */
        @Override
        public boolean requiredToCheckValidParameterValue(final MultivaluedMap<String, String> requestParameters) {
            return requiredToCheckValidParameterValue;
        }

        /* (non-Javadoc)
         * @see com.ericsson.eniq.events.server.serviceprovider.impl.GenericServiceInterface#getServiceSpecificQueryParameters(javax.ws.rs.core.MultivaluedMap)
         */
        @Override
        public Map<String, QueryParameter> getServiceSpecificQueryParameters(
                final MultivaluedMap<String, String> requestParameters) {
            // TODO Auto-generated method stub
            return null;
        }

        /* (non-Javadoc)
         * @see com.ericsson.eniq.events.server.serviceprovider.impl.GenericServiceInterface#getTableSuffixKey()
         */
        @Override
        public String getTableSuffixKey() {
            // TODO Auto-generated method stub
            return null;
        }

        /* (non-Javadoc)
         * @see com.ericsson.eniq.events.server.serviceprovider.impl.GenericServiceInterface#getMeasurementTypes()
         */
        @Override
        public List<String> getMeasurementTypes() {
            // TODO Auto-generated method stub
            return null;
        }

        /* (non-Javadoc)
         * @see com.ericsson.eniq.events.server.serviceprovider.impl.GenericServiceInterface#getRawTableKeys()
         */
        @Override
        public List<String> getRawTableKeys() {
            // TODO Auto-generated method stub
            return null;
        }
    }
}
