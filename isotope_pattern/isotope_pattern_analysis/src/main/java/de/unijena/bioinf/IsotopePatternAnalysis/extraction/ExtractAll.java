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
package de.unijena.bioinf.IsotopePatternAnalysis.extraction;

import com.google.common.collect.Range;
import de.unijena.bioinf.ChemistryBase.algorithm.ParameterHelper;
import de.unijena.bioinf.ChemistryBase.chem.ChemicalAlphabet;
import de.unijena.bioinf.ChemistryBase.chem.Element;
import de.unijena.bioinf.ChemistryBase.chem.Isotopes;
import de.unijena.bioinf.ChemistryBase.chem.PeriodicTable;
import de.unijena.bioinf.ChemistryBase.chem.utils.IsotopicDistribution;
import de.unijena.bioinf.ChemistryBase.data.DataDocument;
import de.unijena.bioinf.ChemistryBase.ms.Deviation;
import de.unijena.bioinf.ChemistryBase.ms.MeasurementProfile;
import de.unijena.bioinf.ChemistryBase.ms.Peak;
import de.unijena.bioinf.ChemistryBase.ms.Spectrum;
import de.unijena.bioinf.ChemistryBase.ms.utils.SimpleMutableSpectrum;
import de.unijena.bioinf.ChemistryBase.ms.utils.SimpleSpectrum;
import de.unijena.bioinf.ChemistryBase.ms.utils.Spectrums;
import de.unijena.bioinf.IsotopePatternAnalysis.IsotopePattern;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public class ExtractAll implements PatternExtractor {
    @Override
    public List<IsotopePattern> extractPattern(MeasurementProfile profile, Spectrum<Peak> spectrum) {
        final SimpleMutableSpectrum byInt = new SimpleMutableSpectrum(spectrum);
        final SimpleSpectrum byMz = new SimpleSpectrum(spectrum);
        Spectrums.sortSpectrumByDescendingIntensity(byInt);
        final BitSet allreadyUsed = new BitSet(byInt.size());
        final Deviation window = getIsotopeDeviation(profile);
        final SimpleMutableSpectrum buffer = new SimpleMutableSpectrum();
        final ArrayList<IsotopePattern> candidates = new ArrayList<IsotopePattern>();
        for (int k=0; k < byInt.size(); ++k) {
            final int mzIndex = Spectrums.mostIntensivePeakWithin(byMz, byInt.getMzAt(k),  window);
            if (allreadyUsed.get(mzIndex)) continue;
            final double monomz = byMz.getMzAt(mzIndex);
            buffer.addPeak(byMz.getMzAt(mzIndex), byMz.getIntensityAt(mzIndex));
            int j=mzIndex+1;
            eachPatternPos:
            for (int f=1; f <= 10; ++f) {
                boolean found=false;
                for (; j < byMz.size(); ++j) {
                    final double mz = byMz.getMzAt(j);
                    final double expectedMass = monomz + f;
                    if (window.inErrorWindow(expectedMass, mz) && !allreadyUsed.get(j)) {
                        buffer.addPeak(byMz.getMzAt(j), byMz.getIntensityAt(j));
                        allreadyUsed.set(j);
                        found = true;
                    } else if (byMz.getMzAt(j) > (expectedMass+0.3)) {
                        if (found) continue eachPatternPos;
                        else break eachPatternPos;
                    }
                }
            }
            // check also positions before
            // TODO: very heuristically approach optimized for metabolomics (=small molecules, restricted set of elements)
            if (true) {//(monomz > 1000) {
                j = mzIndex-1;
                eachPatternPos:
                for (int f=1; f <= 10; ++f) {
                    boolean found=false;
                    for (; j >= 0; --j) {
                        final double mz = byMz.getMzAt(j);
                        final double expectedMass = monomz - f;
                        if (window.inErrorWindow(expectedMass, mz) && !allreadyUsed.get(j) && byMz.getIntensityAt(j)/byMz.getIntensityAt(mzIndex) > 0.33) {
                            buffer.addPeak(byMz.getMzAt(j), byMz.getIntensityAt(j));
                            //do not reserve
                            //allreadyUsed.set(j);
                            found = true;
                        } else if (byMz.getMzAt(j) < (expectedMass-0.3)) {
                            if (found) continue eachPatternPos;
                            else break eachPatternPos;
                        }
                    }
                }
            }
            if (buffer.size() >= 2) {
                candidates.add(new IsotopePattern(new SimpleSpectrum(buffer)));
            }
            for (int x=buffer.size()-1; x >= 0; --x) buffer.removePeakAt(x);
        }
        return candidates;
    }

    @Override
    public List<IsotopePattern> extractPattern(MeasurementProfile profile, Spectrum<Peak> spectrum, double targetMz, boolean allowAdducts) {
        // TODO: implement in more efficient way
        final ChemicalAlphabet stdalphabet = ChemicalAlphabet.getExtendedAlphabet();
        final Spectrum<Peak> massOrderedSpectrum = Spectrums.getMassOrderedSpectrum(spectrum);
        final ArrayList<SimpleSpectrum> patterns = new ArrayList<SimpleSpectrum>();
        final int index = Spectrums.mostIntensivePeakWithin(massOrderedSpectrum, targetMz, profile.getAllowedMassDeviation());
        if (index < 0) return new ArrayList<IsotopePattern>();
        final SimpleMutableSpectrum spec = new SimpleMutableSpectrum();
        spec.addPeak(massOrderedSpectrum.getPeakAt(index));
        // add additional peaks
        for (int k=1; k <= 5; ++k) {
            final Range<Double> nextMz = PeriodicTable.getInstance().getIsotopicMassWindow(stdalphabet, profile.getAllowedMassDeviation(), spec.getMzAt(0), k);
            final double a = nextMz.lowerEndpoint();
            final double b = nextMz.upperEndpoint();
            final double m = a+(b-a)/2d;
            final Deviation dev = Deviation.fromMeasurementAndReference(m, a);
            final int nextIndex = Spectrums.mostIntensivePeakWithin(massOrderedSpectrum, m, dev);
            if (nextIndex < 0) break;
            else {
                if (massOrderedSpectrum.getIntensityAt(nextIndex) > spec.getIntensityAt(spec.size()-1)) {
                    // maybe a new pattern started?
                    patterns.add(new SimpleSpectrum(spec));
                }
                spec.addPeak(massOrderedSpectrum.getPeakAt(nextIndex));
            }
        }
        patterns.add(0, new SimpleSpectrum(spec));
        final ArrayList<IsotopePattern> pats = new ArrayList<IsotopePattern>();
        for (SimpleSpectrum s : patterns) {
            pats.add(new IsotopePattern(s));
        }
        return pats;
    }

    @Override
    public <G, D, L> void importParameters(ParameterHelper helper, DataDocument<G, D, L> document, D dictionary) {
        // nothing
    }

    @Override
    public <G, D, L> void exportParameters(ParameterHelper helper, DataDocument<G, D, L> document, D dictionary) {
        // nothing
    }

    public Deviation getIsotopeDeviation(MeasurementProfile profile) {
        final PeriodicTable pt = PeriodicTable.getInstance();
        double delta = 0d;
        final IsotopicDistribution distr = pt.getDistribution();
        for (Element e : profile.getFormulaConstraints().getChemicalAlphabet()) {
            Isotopes iso = distr.getIsotopesFor(e);
            for (int k=1; k < iso.getNumberOfIsotopes(); ++k) {
                final double diff = iso.getMass(k)-iso.getIntegerMass(k);
                delta = Math.max(delta, Math.abs(diff));
            }
        }
        return new Deviation(2*profile.getAllowedMassDeviation().getPpm(), 3*profile.getAllowedMassDeviation().getAbsolute()+delta);
    }
}