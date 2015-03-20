package com.seenu.tracecompass.live;

import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateValueTypeException;
import org.eclipse.tracecompass.statesystem.core.exceptions.TimeRangeException;
import org.eclipse.tracecompass.statesystem.core.statevalue.TmfStateValue;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.event.ITmfLostEvent;
import org.eclipse.tracecompass.tmf.core.statesystem.AbstractTmfStateProvider;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;
import org.eclipse.tracecompass.tmf.core.statesystem.TmfStateSystemAnalysisModule;
import org.eclipse.tracecompass.tmf.core.timestamp.ITmfTimestamp;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

import com.seenu.tracecompass.live.TmfStateStatistics_2.Attributes;

public class TmfStatisticsTotalsModule2 extends TmfStateSystemAnalysisModule {

    /**
     * The ID of this analysis module (which is also the ID of the state system)
     */
    public static final String ID = "com.seenu.tracecompass.live.module.sine"; //$NON-NLS-1$

    private static final String NAME = "Sine Value"; //$NON-NLS-1$

    /**
     * Constructor
     */
    public TmfStatisticsTotalsModule2() {
        super();
        setId(ID);
        setName(NAME);
    }

    @Override
    protected ITmfStateProvider createStateProvider() {
        return new StatsProviderTotals(getTrace());
    }

    @Override
    protected String getSsFileName() {
        return "sine-totals.ht"; //$NON-NLS-1$
    }


    /**
     * The state provider for traces statistics that use TmfStateStatistics. It
     * should work with any trace type for which we can use the state system.
     *
     * Only one attribute will be stored, containing the total of events seen so
     * far. The resulting attribute tree will look like this:
     *
     * <pre>
     * (root)
     *   \-- total
     * </pre>
     *
     * @author Alexandre Montplaisir
     * @version 1.0
     */
    class StatsProviderTotals extends AbstractTmfStateProvider {

        /**
         * Version number of this input handler. Please bump this if you modify the
         * contents of the generated state history in some way.
         */
        private static final int VERSION = 2;

        /**
         * Constructor
        *
         * @param trace
         *            The trace for which we build this state system
         */
        public StatsProviderTotals(ITmfTrace trace) {
            super(trace, NAME);
        }

        @Override
        public int getVersion() {
            return VERSION;
        }

        @Override
        public StatsProviderTotals getNewInstance() {
            return new StatsProviderTotals(this.getTrace());
        }

        @Override
        protected void eventHandle(ITmfEvent event) {
            /* Do not count lost events in the total */
            if (event instanceof ITmfLostEvent) {
                return;
            }

            ITmfStateSystemBuilder ss = getStateSystemBuilder();

            /* Since this can be used for any trace types, normalize all the
             * timestamp values to nanoseconds. */
            final long ts = event.getTimestamp().normalize(0, ITmfTimestamp.NANOSECOND_SCALE).getValue();

            try {
                /* Total number of cache accesses */
                int quark = ss.getQuarkAbsoluteAndAdd(Attributes.TOTAL);
                ITmfEventField field = event.getContent().getField("Sine Value");
                long parseLong = Long.parseLong(field.getValue().toString());

                ss.modifyAttribute(ts, TmfStateValue.newValueLong(parseLong), quark);
            } catch (StateValueTypeException | TimeRangeException | AttributeNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

}
