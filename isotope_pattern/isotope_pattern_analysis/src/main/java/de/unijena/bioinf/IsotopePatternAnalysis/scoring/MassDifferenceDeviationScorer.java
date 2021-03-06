/*
 *  This file is part of the SIRIUS library for analyzing MS and MS/MS data
 *
 *  Copyright (C) 2013-2015 Kai Dührkop
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with SIRIUS.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.unijena.bioinf.IsotopePatternAnalysis.scoring;

import de.unijena.bioinf.ChemistryBase.algorithm.ParameterHelper;
import de.unijena.bioinf.ChemistryBase.data.DataDocument;
import de.unijena.bioinf.ChemistryBase.ms.*;
import de.unijena.bioinf.IsotopePatternAnalysis.util.IntensityDependency;
import de.unijena.bioinf.IsotopePatternAnalysis.util.LinearIntensityDependency;
import org.apache.commons.math3.special.Erf;

/**
 * scores the m/z of the first peak and the m/z difference between either all other peaks and the first peak.
 * Peaks with relative intensity below 10% are scored with doubled standard deviation
 *
 *
 */
public class MassDifferenceDeviationScorer implements IsotopePatternScorer {

    private final static double root2 = Math.sqrt(2d);
    protected IntensityDependency dependency;

    public MassDifferenceDeviationScorer() {
        this(2d);
    }

    public MassDifferenceDeviationScorer(double lowestIntensityAccuracy) {
        this(new LinearIntensityDependency(0.1d, 1d, lowestIntensityAccuracy));
    }

    public MassDifferenceDeviationScorer(IntensityDependency dependency) {
        this.dependency = dependency;
    }

    @Override
    public void score(double[] scores, Spectrum<Peak> measured, Spectrum<Peak> theoretical, Normalization norm, Ms2Experiment experiment, MeasurementProfile profile) {
        final double mz0 = measured.getMzAt(0);
        final double thMz0 = theoretical.getMzAt(0);
        double score = 0d;
        for (int i=1; i < measured.size(); ++i) {
            final double mz = measured.getMzAt(i) - mz0;
            final double thMz = theoretical.getMzAt(i) - thMz0;
            final double intensity = measured.getIntensityAt(i);
            final double sd = profile.getStandardMassDifferenceDeviation().absoluteFor(measured.getMzAt(i)) * dependency.getValueAt(intensity);
            score += Math.log(Erf.erfc(Math.abs(thMz - mz)/(root2*sd)));
            scores[i] += score;
        }
    }

    @Override
    public <G, D, L> void importParameters(ParameterHelper helper, DataDocument<G, D, L> document, D dictionary) {
        this.dependency = (IntensityDependency)helper.unwrap(document, document.getFromDictionary(dictionary, "intensityDependency"));
    }

    @Override
    public <G, D, L> void exportParameters(ParameterHelper helper, DataDocument<G, D, L> document, D dictionary) {
        document.addToDictionary(dictionary, "intensityDependency", helper.wrap(document,dependency));
    }
}
