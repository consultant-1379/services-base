/**
 * -----------------------------------------------------------------------
 *     Copyright (C) 2011 LM Ericsson Limited.  All rights reserved.
 * -----------------------------------------------------------------------
 */
package com.ericsson.eniq.events.server.serviceprovider.impl;

/**
 * @author epesmit
 * @since 2011
 *
 */
public interface GenericSimpleServiceInterface {

    /**
     * Fetch the template path for this service
     * This is used when looking up the templateMap.csv
     *
     * @return key for the templateMap.csv file eg EVENT_ANALYSIS or SUBBI/SUBSCRIBER_DETAILS
     */
    String getTemplatePath();

}
