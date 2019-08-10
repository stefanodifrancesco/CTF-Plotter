package org.eclipse.tracecompass.ctf.plotter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.event.TmfEvent;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfTraceException;
import org.eclipse.tracecompass.tmf.core.project.model.TmfTraceType;
import org.eclipse.tracecompass.tmf.core.request.ITmfEventRequest;
import org.eclipse.tracecompass.tmf.core.request.TmfEventRequest;
import org.eclipse.tracecompass.tmf.core.timestamp.TmfTimeRange;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.ui.project.model.ITmfProjectModelElement;
import org.eclipse.tracecompass.tmf.ui.project.model.TmfProjectRegistry;
import org.eclipse.tracecompass.tmf.ui.project.model.TmfTraceElement;
import org.eclipse.tracecompass.tmf.ui.project.model.TmfTraceFolder;
import org.eclipse.tracecompass.tmf.ui.views.TmfView;
import org.swtchart.Chart;
import org.swtchart.ILineSeries;
import org.swtchart.ISeries.SeriesType;
import org.swtchart.LineStyle;
import org.swtchart.Range;

public class RTplotter extends TmfView {

	private static final String SERIES_NAME = "Series";
	private static final String Y_AXIS_TITLE = "Timestamp";
	private static final String X_AXIS_TITLE = "ID";
	private static final String FIELD = "timestamp_field"; // The name of the field that we want to display on the Y axis
	private static final String VIEW_ID = "org.eclipse.tracecompass.ctf.plotter.RTplot";
	private Chart chart;

	IWorkspace workspace = ResourcesPlugin.getWorkspace();
	IResourceChangeListener listener;

	public RTplotter() {
		super(VIEW_ID);
	}

	@Override
	public void createPartControl(Composite parent) {
		chart = new Chart(parent, SWT.BORDER);
		chart.getTitle().setVisible(false);
		chart.getAxisSet().getXAxis(0).getTitle().setText(X_AXIS_TITLE);
		chart.getAxisSet().getYAxis(0).getTitle().setText(Y_AXIS_TITLE);
		ILineSeries series = (ILineSeries) chart.getSeriesSet().createSeries(SeriesType.LINE, SERIES_NAME);
		series.setLineStyle(LineStyle.NONE);
		chart.getLegend().setVisible(false);

		
		Job job = new Job("Plotting") {
			protected IStatus run(IProgressMonitor monitor) {
				try {
					IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("Tracing");
					TmfTraceFolder destinationFolder = TmfProjectRegistry.getProject(project, true).getTracesFolder();
					System.out.println(destinationFolder);
						
					final String pathToUse = checkTracePath("/home/ctf");
					IFolder folder = destinationFolder.getResource();
					String traceName = getTraceName(pathToUse, folder);

					final List<ITmfProjectModelElement> elements = destinationFolder.getChildren();
					
					TmfTraceElement traceElement = null;
					for (ITmfProjectModelElement element : elements) {
						if (element instanceof TmfTraceElement && element.getName().equals(traceName)) {
							traceElement = (TmfTraceElement) element;
						}
					}

					final ITmfTrace trace = traceElement.instantiateTrace();
					final ITmfEvent traceEvent = traceElement.instantiateEvent();

					try {
						trace.initTrace(traceElement.getResource(),
								traceElement.getResource().getLocation().toOSString(), traceEvent.getClass(),
								traceElement.getElementPath(), traceElement.getTraceType());
					} catch (TmfTraceException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

					Display.getDefault().asyncExec(new Runnable() {
						@Override
						public void run() {
							drawTraceSelected(trace);
						}
					});

					// Wait the trace is analyzed
					try {
						Thread.sleep(200);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					// then dispose
					trace.dispose();

					if (monitor.isCanceled())
						return Status.CANCEL_STATUS;

					return Status.OK_STATUS;
				} finally {
					schedule(1000); // the job start again in 500 millisecond
				}
			}
		};
		job.setPriority(Job.SHORT);
		job.schedule(); // start as soon as possible
       
	}

	private String checkTracePath(String path) {
		File file = new File(path);
		if (file.exists() && !file.isDirectory()) {
			// First check parent
			File parent = file.getParentFile();
			String pathToUse = parent.getAbsolutePath();
			if (TmfTraceType.isDirectoryTrace(pathToUse)) {
				return pathToUse;
			}
			// Second check grandparent
			File grandParent = parent.getParentFile();
			if (grandParent != null) {
				pathToUse = grandParent.getAbsolutePath();
				if (TmfTraceType.isDirectoryTrace(pathToUse)) {
					return pathToUse;
				}
			}
		}
		return path;
	}

	private String getTraceName(String path, IFolder folder) {
		String name;
		File traceFile = new File(path);
		try {
			traceFile = traceFile.getCanonicalFile();
		} catch (IOException e) {
			/* just use original file path */
		}
		name = traceFile.getName();
		for (int i = 2; isWrongMember(folder, name, traceFile); i++) {
			name = traceFile.getName() + '(' + i + ')';
		}
		return name;
	}

	private boolean isWrongMember(IFolder folder, String name, final File traceFile) {
		final IResource candidate = folder.findMember(name);
		if (candidate != null) {
			final IPath rawLocation = candidate.getRawLocation();
			File file = rawLocation.toFile();
			try {
				file = file.getCanonicalFile();
			} catch (IOException e) {
				/* just use original file path */
			}
			return !file.equals(traceFile);
		}
		return false;
	}

	public void drawTraceSelected(ITmfTrace trace) {

		// Create the request to get data from the trace
		TmfEventRequest req = new TmfEventRequest(TmfEvent.class, TmfTimeRange.ETERNITY, 0, ITmfEventRequest.ALL_DATA,
				ITmfEventRequest.ExecutionType.FOREGROUND) {

			ArrayList<Double> xValues = new ArrayList<Double>();
			ArrayList<Double> yValues = new ArrayList<Double>();
			private double maxY = -Double.MAX_VALUE;
			private double minY = Double.MAX_VALUE;
			private double maxX = -Double.MAX_VALUE;
			private double minX = Double.MAX_VALUE;

			@Override
			public void handleData(ITmfEvent data) {
				// Called for each event
				super.handleData(data);

				ITmfEventField field = data.getContent().getField(FIELD);
				if (field != null) {
					Double yValue = ((Long) field.getValue()).doubleValue();
					minY = Math.min(minY, yValue);
					maxY = Math.max(maxY, yValue);
					yValues.add(yValue);

					double xValue = ((Long) data.getContent().getField("ID_field").getValue()).doubleValue();
					xValues.add(xValue);
					minX = Math.min(minX, xValue);
					maxX = Math.max(maxX, xValue);
				}
			}

			@Override
			public void handleSuccess() {
				// Request successful, not more data available
				super.handleSuccess();

				System.out.println(xValues.size());
				System.out.println(yValues.size());
				final double x[] = toArray(xValues);
				final double y[] = toArray(yValues);

				// This part needs to run on the UI thread since it updates the chart SWT
				// control
				Display.getDefault().asyncExec(new Runnable() {

					@Override
					public void run() {
						chart.getSeriesSet().getSeries()[0].setXSeries(x);
						chart.getSeriesSet().getSeries()[0].setYSeries(y);

						// Set the new range
						if (!xValues.isEmpty() && !yValues.isEmpty()) {
							chart.getAxisSet().getXAxis(0).setRange(new Range(0, x[x.length - 1]));
							chart.getAxisSet().getYAxis(0).setRange(new Range(minY, maxY));
						} else {
							chart.getAxisSet().getXAxis(0).setRange(new Range(0, 1));
							chart.getAxisSet().getYAxis(0).setRange(new Range(0, 1));
						}
						chart.getAxisSet().adjustRange();

						chart.redraw();
					}

				});
			}

			/**
			 * Convert List<Double> to double[]
			 */
			private double[] toArray(List<Double> list) {
				double[] d = new double[list.size()];
				for (int i = 0; i < list.size(); ++i) {
					d[i] = list.get(i);
				}

				return d;
			}

			@Override
			public void handleFailure() {
				// Request failed, not more data available
				
				super.handleFailure();
			}
		};
		
		trace.sendRequest(req);
	}

	@Override
	public void setFocus() {
		// TODO Auto-generated method stub

	}

}
