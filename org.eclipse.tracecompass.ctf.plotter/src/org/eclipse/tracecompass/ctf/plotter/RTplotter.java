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
import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swtchart.Chart;
import org.eclipse.swtchart.ILineSeries;
import org.eclipse.swtchart.ISeries.SeriesType;
import org.eclipse.swtchart.LineStyle;
import org.eclipse.swtchart.Range;
import org.eclipse.swtchart.extensions.charts.InteractiveChart;
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
import org.eclipse.tracecompass.tmf.ui.project.model.TmfProjectElement;
import org.eclipse.tracecompass.tmf.ui.project.model.TmfProjectRegistry;
import org.eclipse.tracecompass.tmf.ui.project.model.TmfTraceElement;
import org.eclipse.tracecompass.tmf.ui.project.model.TmfTraceFolder;
import org.eclipse.tracecompass.tmf.ui.views.TmfView;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.plugin.AbstractUIPlugin;

public class RTplotter extends TmfView {

	private static final String SERIES_NAME = "Series";
	private static final String Y_AXIS_TITLE = "Timestamp";
	private static final String X_AXIS_TITLE = "ID";
	private static final String FIELD = "timestamp_field"; // The name of the field that we want to display on the Y
															// axis
	private static final String VIEW_ID = "org.eclipse.tracecompass.ctf.plotter.RTplot";
	
	IProject project = null;
	private String projectName;
	private Chart chart;
	Job job;

	Action startDrawing;
	Action stopDrawing;
	Action adjustRange;
	Action invertAxis;
	Action clearChart;

	IWorkspace workspace = ResourcesPlugin.getWorkspace();
	IResourceChangeListener listener;

	public RTplotter() {
		super(VIEW_ID);
	}

	@Override
	public void createPartControl(Composite parent) {
		chart = new InteractiveChart(parent, SWT.NONE);

		// Set axis names
		chart.getAxisSet().getXAxis(0).getTitle().setText(X_AXIS_TITLE);
		chart.getAxisSet().getYAxis(0).getTitle().setText(Y_AXIS_TITLE);
		// Set axis default ranges
		chart.getAxisSet().getXAxis(0).setRange(new Range(1, 4294967295d));
		chart.getAxisSet().getYAxis(0).setRange(new Range(100000, 1000000));
		// Set invisible line
		ILineSeries series = (ILineSeries) chart.getSeriesSet().createSeries(SeriesType.LINE, SERIES_NAME);
		series.setLineStyle(LineStyle.NONE);
		chart.getLegend().setVisible(false);

		// Get workspace view containing tracing projects
		IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		IViewPart viewPart = page.findView(IPageLayout.ID_PROJECT_EXPLORER);
		ISelectionProvider selProvider = viewPart.getSite().getSelectionProvider();
		
		// Create a new job for plotting at intervals
		job = new Job("Plotting") {
			protected IStatus run(IProgressMonitor monitor) {
				try {
					
					// Get selected tracing project from the workspace
					ISelection selection = selProvider.getSelection();
					IResource res = null;
					if (selection instanceof IStructuredSelection) {
						IStructuredSelection ss = (IStructuredSelection) selection;
						Object element = ss.getFirstElement();
						if (element instanceof IResource)
							res = (IResource) element;
					}
					
					if (res != null) {
						project = res.getProject();
						
						/*********** This piece of code has been adapted from TraceCompass sources **********/
						TmfProjectElement tmfProject = TmfProjectRegistry.getProject(project, true);
						projectName = tmfProject.getName();
						TmfTraceFolder destinationFolder = tmfProject.getTracesFolder();
						if (destinationFolder.getChildren().size() > 0) {
							
							String ctf_absolute_path = destinationFolder.getChildren().get(0).getLocation().getPath();
							
							final String pathToUse = checkTracePath(ctf_absolute_path);
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
							/*************************************************************************************/
							
							
							// Run on the UI thread the code to render the CTF of the selected tracing project
							Display.getDefault().asyncExec(new Runnable() {
								@Override
								public void run() {
									chart.getTitle().setText("Chart of " + projectName);
									drawTraceSelected(trace);
								}
							});

							// Wait the trace is rendered
							try {
								Thread.sleep(200);
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							// then dispose
							trace.dispose();
						} else {
							Display.getDefault().asyncExec(new Runnable() {
								@Override
								public void run() {
									chart.getTitle().setText("No chart for empty project");
									final double x[] = new double[0];
									final double y[] = new double[0];
									chart.getSeriesSet().getSeries()[0].setXSeries(x);
									chart.getSeriesSet().getSeries()[0].setYSeries(y);
									chart.redraw();
								}
							});
						}
						
					} else {
						if (project == null) {
							Display.getDefault().asyncExec(new Runnable() {
								@Override
								public void run() {
									chart.getTitle().setText("No chart for empty project");
									final double x[] = new double[0];
									final double y[] = new double[0];
									chart.getSeriesSet().getSeries()[0].setXSeries(x);
									chart.getSeriesSet().getSeries()[0].setYSeries(y);
									chart.redraw();
								}
							});
						}						
					}
					
					if (monitor.isCanceled())
						return Status.CANCEL_STATUS;

					return Status.OK_STATUS;
				} finally {
					schedule(1000); // the job start again in 1000 millisecond. Too low interval will cause unresponsive UI.
				}
			}
		};
		job.setPriority(Job.SHORT);
		// job.schedule(); // start as soon as possible

		startDrawing = new Action() {
			public void run() {
				if (job.getState() == Job.NONE) {
					job.schedule();
				}
			}
		};
		startDrawing.setText("start");
		startDrawing.setToolTipText("Start drawing");
		startDrawing.setImageDescriptor(
				PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_OPEN_MARKER));

		stopDrawing = new Action() {
			public void run() {
				while (job.getState() == Job.RUNNING) {

				}
				job.cancel();
			}
		};
		stopDrawing.setText("stop");
		stopDrawing.setToolTipText("Stop drawing");
		stopDrawing.setImageDescriptor(
				PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_ELCL_STOP));

		adjustRange = new Action() {
			public void run() {
				chart.getAxisSet().getXAxis(0).setRange(new Range(1, 4294967295d));
				chart.getAxisSet().getYAxis(0).setRange(new Range(100000, 1000000));
			}
		};
		adjustRange.setText("Adjust");
		adjustRange.setToolTipText("Adjust range");
		adjustRange.setImageDescriptor(
				AbstractUIPlugin.imageDescriptorFromPlugin("org.eclipse.tracecompass.ctf.plotter", "icons/adjust.jpg"));

		invertAxis = new Action() {
			public void run() {
				if (chart.getOrientation() == SWT.HORIZONTAL) {
					chart.setOrientation(SWT.VERTICAL);
				} else {
					chart.setOrientation(SWT.HORIZONTAL);
				}

			}
		};
		invertAxis.setText("Invert");
		invertAxis.setToolTipText("Invert axis");
		invertAxis.setImageDescriptor(
				PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_ELCL_SYNCED));
		
		clearChart = new Action() {
			public void run() {
				final double x[] = new double[0];
				final double y[] = new double[0];
				chart.getSeriesSet().getSeries()[0].setXSeries(x);
				chart.getSeriesSet().getSeries()[0].setYSeries(y);
				chart.redraw();
			}
		};
		clearChart.setText("clear");
		clearChart.setToolTipText("Clear");
		clearChart.setImageDescriptor(
				PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_ETOOL_CLEAR));

		IActionBars bars = getViewSite().getActionBars();
		bars.getToolBarManager().add(startDrawing);
		bars.getToolBarManager().add(stopDrawing);
		bars.getToolBarManager().add(adjustRange);
		bars.getToolBarManager().add(invertAxis);
		bars.getToolBarManager().add(clearChart);
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
//			private double maxY = -Double.MAX_VALUE;
//			private double minY = Double.MAX_VALUE;
//			private double maxX = -Double.MAX_VALUE;
//			private double minX = Double.MAX_VALUE;

			@Override
			public void handleData(ITmfEvent data) {
				// Called for each event
				super.handleData(data);

				ITmfEventField field = data.getContent().getField(FIELD);
				if (field != null) {
					Double yValue = ((Long) field.getValue()).doubleValue();
//					minY = Math.min(minY, yValue);
//					maxY = Math.max(maxY, yValue);
					yValues.add(yValue);

					double xValue = ((Long) data.getContent().getField("ID_field").getValue()).doubleValue();
					xValues.add(xValue);
//					minX = Math.min(minX, xValue);
//					maxX = Math.max(maxX, xValue);
				}
			}

			@Override
			public void handleSuccess() {
				// Request successful, not more data available
				super.handleSuccess();

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
//						if (!xValues.isEmpty() && !yValues.isEmpty()) {
//							chart.getAxisSet().getXAxis(0).setRange(new Range(0, x[x.length - 1]));
//							chart.getAxisSet().getYAxis(0).setRange(new Range(minY, maxY));
//						} else {
//							chart.getAxisSet().getXAxis(0).setRange(new Range(0, 1));
//							chart.getAxisSet().getYAxis(0).setRange(new Range(0, 1));
//						}
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
	public void dispose() {
		while (job.getState() == Job.RUNNING) {

		}
		job.cancel();
		super.dispose();
	}

	@Override
	public void setFocus() {
		// TODO Auto-generated method stub

	}

}
