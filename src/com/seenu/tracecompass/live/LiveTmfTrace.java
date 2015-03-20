package com.seenu.tracecompass.live;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.event.TmfEvent;
import org.eclipse.tracecompass.tmf.core.event.TmfEventField;
import org.eclipse.tracecompass.tmf.core.event.TmfEventType;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfTraceException;
import org.eclipse.tracecompass.tmf.core.timestamp.ITmfTimestamp;
import org.eclipse.tracecompass.tmf.core.timestamp.TmfTimestamp;
import org.eclipse.tracecompass.tmf.core.trace.ITmfContext;
import org.eclipse.tracecompass.tmf.core.trace.ITmfEventParser;
import org.eclipse.tracecompass.tmf.core.trace.TmfContext;
import org.eclipse.tracecompass.tmf.core.trace.TmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TraceValidationStatus;
import org.eclipse.tracecompass.tmf.core.trace.location.ITmfLocation;
import org.eclipse.tracecompass.tmf.core.trace.location.TmfLongLocation;

public class LiveTmfTrace extends TmfTrace implements ITmfEventParser{

	ITmfLocation currentLoc = null;
	long count;

	private long initOffset;
	private long currentChunk;

	private File fFile;
	private String[] fEventTypes = new String[] {"Time", "Sine Value", "Cos Value"};
	private FileChannel fFileChannel;
	private MappedByteBuffer fMappedByteBuffer;

	private static final long CHUNK_SIZE = 65536;

	public LiveTmfTrace() {
	}
	
	public LiveTmfTrace(IResource resource, Class<? extends ITmfEvent> type, String path, int cacheSize, long interval) throws TmfTraceException {
		super(resource, type, path, cacheSize, interval);
	}

	public LiveTmfTrace(TmfTrace trace) throws TmfTraceException {
		super(trace);
	}

	@Override
	public IStatus validate(IProject project, String path) {
		File f = new File(path);
		if (!f.exists()) {
			return new Status(IStatus.ERROR, Activator.PLUGIN_ID,
					"File does not exist"); //$NON-NLS-1$
		}
		if (!f.isFile()) {
			return new Status(IStatus.ERROR, Activator.PLUGIN_ID, path
					+ " is not a file"); //$NON-NLS-1$
		}
		if(f.getName().endsWith(".live"))
			return new TraceValidationStatus(100, Activator.LIVE_TRACE_TYPE_ID); //$NON-NLS-1$

		return new Status(IStatus.ERROR, Activator.PLUGIN_ID, path+ " is not a live trace file"); //$NON-NLS-1$
	}

	@Override
	public void initTrace(IResource resource, String path, Class<? extends ITmfEvent> type, String name, String traceTypeId) throws TmfTraceException {
		super.initTrace(resource, path, type, name, traceTypeId);
		fFile = new File(path);
		fFile.length();
		count = 0 ;

		for(String eventType: fEventTypes){
			initOffset+=eventType.length()+1;
		}

		try {
			fFileChannel = new FileInputStream(fFile).getChannel();
			currentChunk = 0;
			seekChunk(currentChunk);
		} catch (IOException e) {
		}
	}

	private void seekChunk(long chunkNum) throws IOException {
		final long position = initOffset + chunkNum*CHUNK_SIZE;
		long size = Math.min((fFileChannel.size()-position), CHUNK_SIZE);
		if(size<0){
			System.out.println("ERROR: $$$$$");
		}

		fMappedByteBuffer = fFileChannel.map(MapMode.READ_ONLY, position, size);
		currentLoc = new TmfLongLocation(position);
	}

	@Override
	public ITmfLocation getCurrentLocation() {
		return currentLoc;
	}

	@Override
	public double getLocationRatio(ITmfLocation location) {
		return ((TmfLongLocation) location).getLocationInfo().doubleValue()/getNbEvents();
	}

	@Override
	public ITmfContext seekEvent(ITmfLocation location) {
		TmfLongLocation tl = null;

		if(location==null){
			tl = new TmfLongLocation(0);
		}else{
			tl = (TmfLongLocation) location;
		}

		Long longVal = tl.getLocationInfo();
		long chunkVal = longVal/CHUNK_SIZE;
		long remainder = longVal % CHUNK_SIZE;

		if(chunkVal!=currentChunk){
			try {
				seekChunk(chunkVal);
				currentChunk = chunkVal;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		if(remainder<fMappedByteBuffer.limit()){
			fMappedByteBuffer.position((int) remainder);
		}

		return new TmfContext(tl);
	}

	@Override
	public ITmfContext seekEvent(double ratio) {
		return seekEvent((long)ratio*getNbEvents()/100);
	}

	@Override
	public ITmfEvent parseEvent(ITmfContext context) {
		TmfLongLocation location = (TmfLongLocation) context.getLocation();
		Long info = location.getLocationInfo();
		TmfEvent event = null;
		System.out.print("Count: "+ ++count);
		System.out.println("; Parsing event rank: "+ context.getRank());

		if(fMappedByteBuffer.position()==fMappedByteBuffer.limit()){
			if(fMappedByteBuffer.limit()==CHUNK_SIZE){
				try {
					seekChunk(++currentChunk);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}else{
				return null;
			}
		}

		final TmfEventField[] events = new TmfEventField[fEventTypes.length];
		for(int i=0; i< events.length; i++){
			events[i] = new TmfEventField(fEventTypes[i], getBigInteger(fMappedByteBuffer), null);
		}
		long ts = Long.parseLong(events[0].getValue().toString());

		final TmfEventField content = new TmfEventField(ITmfEventField.ROOT_FIELD_ID, null, events);
		event = new TmfEvent(this, info, new TmfTimestamp(ts, ITmfTimestamp.MILLISECOND_SCALE), new TmfEventType(getTraceTypeId(), content), content);
		currentLoc = new TmfLongLocation(currentChunk*CHUNK_SIZE + fMappedByteBuffer.position());

		return event;
	}
	
	private long getBigInteger(ByteBuffer buffer){
		long data = 0x00000000ffffffffL & fMappedByteBuffer.getInt();
		return data;
	}
}
