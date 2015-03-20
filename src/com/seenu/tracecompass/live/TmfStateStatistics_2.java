/*******************************************************************************
 * Copyright (c) 2012, 2014 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Alexandre Montplaisir - Initial API and implementation
 *   Patrick Tasse - Fix TimeRangeException
 ******************************************************************************/

package com.seenu.tracecompass.live;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.tmf.core.statistics.ITmfStatistics;

/**
 * Implementation of ITmfStatistics which uses a state history for storing its
 * information. In reality, it uses two state histories, one for "event totals"
 * information (which should ideally use a fast backend), and another one for
 * the rest (per event type, per CPU, etc.).
 *
 * Compared to the event-request-based statistics calculations, it adds the
 * building the history first, but gives much faster response times once built :
 * Queries are O(log n) wrt the size of the trace, and O(1) wrt to the size of
 * the time interval selected.
 *
 * @author Alexandre Montplaisir
 */
public class TmfStateStatistics_2 implements ITmfStatistics {

    // ------------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------------

    /** The cache access totals state system */
    private final ITmfStateSystem totalsStats;

    // ------------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------------

    /**
     * Constructor
     *
     * @param totals
     *            The state system containing the "totals" information
     */
    public TmfStateStatistics_2(ITmfStateSystem totals) {
        this.totalsStats = totals;
    }

    /**
     * Return the state system containing the "totals" values
     *
     * @return The "totals" state system
     */
    public ITmfStateSystem getTotalsSS() {
        return totalsStats;
    }


    // ------------------------------------------------------------------------
    // ITmfStatistics
    // ------------------------------------------------------------------------

    @Override
    public void dispose() {
        totalsStats.dispose();
    }

    @Override
    public List<Long> histogramQuery(final long start, final long end, final int nb) {
        final List<Long> list = new LinkedList<>();
        final long increment = (end - start) / nb;

        if (totalsStats.isCancelled()) {
            return list;
        }

        /*
         * We will do one state system query per "border", and save the
         * differences between each border.
         */
        long prevTotal = (start == totalsStats.getStartTime()) ? 0 : getEventCountAt(start);
        long curTime = start + increment;

        long curTotal, count;
        for (int i = 0; i < nb - 1; i++) {
            curTotal = getEventCountAt(curTime);
            count = curTotal - prevTotal;
            list.add(count);

            curTime += increment;
            prevTotal = curTotal;
        }

        /*
         * For the last bucket, we'll stretch its end time to the end time of
         * the requested range, in case it got truncated down.
         */
        curTotal = getEventCountAt(end);
        count = curTotal - prevTotal;
        list.add(count);

        return list;
    }

    @Override
    public long getEventsTotal() {
        long endTime = totalsStats.getCurrentEndTime();
        int count = 0;

        try {
            final int quark = totalsStats.getQuarkAbsolute(Attributes.TOTAL);
            count= (int) totalsStats.querySingleState(endTime, quark).getStateValue().unboxLong();

        } catch (StateSystemDisposedException e) {
            /* Assume there is no events for that range */
            return 0;
        } catch (AttributeNotFoundException e) {
            e.printStackTrace();
        }

        return count;
    }

    @Override
    public long getEventsInRange(long start, long end) {
        long startCount;
        if (start == totalsStats.getStartTime()) {
            startCount = 0;
        } else {
            /*
             * We want the events happening at "start" to be included, so we'll
             * need to query one unit before that point.
             */
            startCount = getEventCountAt(start - 1);
        }
        long endCount = getEventCountAt(end);

        return endCount - startCount;
    }

    // ------------------------------------------------------------------------
    // Helper methods
    // ------------------------------------------------------------------------

    private long getEventCountAt(long timestamp) {
        /* Make sure the target time is within the range of the history */
        long ts = checkStartTime(timestamp, totalsStats);
        ts = checkEndTime(ts, totalsStats);

        try {
            final int quark = totalsStats.getQuarkAbsolute(Attributes.TOTAL);
            ITmfStateValue stateValue = totalsStats.querySingleState(ts, quark).getStateValue();

            long count = stateValue.unboxLong();
            return count;

        } catch (StateSystemDisposedException e) {
            /* Assume there is no (more) events, nothing will be put in the map. */
        } catch (AttributeNotFoundException e) {
            e.printStackTrace();
        }

        return 0;
    }

    private static long checkStartTime(long initialStart, ITmfStateSystem ss) {
        long start = initialStart;
        if (start < ss.getStartTime()) {
            return ss.getStartTime();
        }
        return start;
    }

    private static long checkEndTime(long initialEnd, ITmfStateSystem ss) {
        long end = initialEnd;
        if (end > ss.getCurrentEndTime()) {
            return ss.getCurrentEndTime();
        }
        return end;
    }

    /**
     * The attribute names that are used in the state provider
     */
    public static class Attributes {
        /** Total nb of events */
        public static final String TOTAL = "sine_value"; //$NON-NLS-1$
    }

	@Override
	public Map<String, Long> getEventTypesTotal() {
		HashMap<String, Long> hashMap = new HashMap<String, Long>();
		hashMap.put("org.eclipse.linuxtools.tracing.examples.nexus", getEventsTotal());
		return hashMap;
	}

	@Override
	public Map<String, Long> getEventTypesInRange(long start, long end) {
		HashMap<String, Long> hashMap = new HashMap<String, Long>();
		hashMap.put("org.eclipse.linuxtools.tracing.examples.nexus", getEventsInRange(start, end));
		return hashMap;
	}
}
