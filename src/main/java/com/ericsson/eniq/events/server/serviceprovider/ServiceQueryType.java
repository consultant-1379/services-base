/**
 * -----------------------------------------------------------------------
 *     Copyright (C) 2011 LM Ericsson Limited.  All rights reserved.
 * -----------------------------------------------------------------------
 */
package com.ericsson.eniq.events.server.serviceprovider;

/**
 * Specifying the type of query
 * This is similiar to the existing node parameter provided in some URLs, but is extended to provide clear
 * distinction between the likes of an IMSI query and an IMSI group query
 *  
 * @author eemecoy
 *
 */
public enum ServiceQueryType {
    RNC, ACCESS_AREA, IMSI, IMSI_GROUP, TERMINAL, TERMINAL_GROUP

}
