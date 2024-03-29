/*******************************************************************************
 * Copyright (c) 2008, 2019 SWTChart project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 * yoshitaka - initial API and implementation
 *******************************************************************************/
package org.eclipse.swtchart.internal.compress;

import java.util.ArrayList;

/**
 * A compressor for bar series data.
 */
public class CompressBarSeries extends Compress {

	/*
	 * @see Compress#addNecessaryPlots(ArrayList, ArrayList, ArrayList)
	 */
	@Override
	protected void addNecessaryPlots(ArrayList<Double> xList, ArrayList<Double> yList, ArrayList<Integer> indexList) {

		double prevX = xSeries[0];
		double maxY = Double.NaN;
		int prevIndex = 0;
		for(int i = 0; i < xSeries.length && i < ySeries.length; i++) {
			if(xSeries[i] >= config.getXLowerValue()) {
				if(isInSameGridXAsPrevious(xSeries[i])) {
					if(maxY < ySeries[i]) {
						maxY = ySeries[i];
					}
				} else {
					if(!Double.isNaN(maxY)) {
						addToList(xList, yList, indexList, prevX, maxY, prevIndex);
					}
					prevX = xSeries[i];
					maxY = ySeries[i];
					prevIndex = i;
				}
			}
			if(xSeries[i] > config.getXUpperValue()) {
				break;
			}
		}
		addToList(xList, yList, indexList, prevX, maxY, prevIndex);
	}

	/**
	 * Checks if the given x coordinate is in the same grid as previous.
	 * 
	 * @param x
	 *            the X coordinate
	 * @return true if the given coordinate is in the same grid as previous
	 */
	private boolean isInSameGridXAsPrevious(double x) {

		int xGridIndex = (int)((x - config.getXLowerValue()) / (config.getXUpperValue() - config.getXLowerValue()) * config.getWidthInPixel());
		boolean isInSameGridAsPrevious = (xGridIndex == previousXGridIndex);
		previousXGridIndex = xGridIndex;
		return isInSameGridAsPrevious;
	}
}
