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
package de.unijena.bioinf.IsotopePatternAnalysis;

import de.unijena.bioinf.ChemistryBase.algorithm.ParameterHelper;
import de.unijena.bioinf.ChemistryBase.algorithm.Parameterized;
import de.unijena.bioinf.ChemistryBase.algorithm.Scored;
import de.unijena.bioinf.ChemistryBase.chem.*;
import de.unijena.bioinf.ChemistryBase.chem.utils.IsotopicDistribution;
import de.unijena.bioinf.ChemistryBase.data.DataDocument;
import de.unijena.bioinf.ChemistryBase.ms.*;
import de.unijena.bioinf.ChemistryBase.ms.utils.SimpleMutableSpectrum;
import de.unijena.bioinf.ChemistryBase.ms.utils.SimpleSpectrum;
import de.unijena.bioinf.IsotopePatternAnalysis.extraction.ExtractAll;
import de.unijena.bioinf.IsotopePatternAnalysis.extraction.PatternExtractor;
import de.unijena.bioinf.IsotopePatternAnalysis.generation.FastIsotopePatternGenerator;
import de.unijena.bioinf.IsotopePatternAnalysis.generation.IsotopePatternGenerator;
import de.unijena.bioinf.IsotopePatternAnalysis.scoring.IsotopePatternScorer;
import de.unijena.bioinf.IsotopePatternAnalysis.scoring.LogNormDistributedIntensityScorer;
import de.unijena.bioinf.IsotopePatternAnalysis.scoring.MassDeviationScorer;
import de.unijena.bioinf.IsotopePatternAnalysis.util.PiecewiseLinearFunctionIntensityDependency;
import de.unijena.bioinf.MassDecomposer.Chemistry.DecomposerCache;

import java.util.*;

import static de.unijena.bioinf.ChemistryBase.ms.utils.Spectrums.addOffset;
import static de.unijena.bioinf.ChemistryBase.ms.utils.Spectrums.normalize;

public class IsotopePatternAnalysis implements Parameterized {

    public static final String ANALYZER_NAME = "IsotopePatternAnalysis";

    private List<IsotopePatternScorer> isotopePatternScorers;
    private double cutoff;
    private double intensityOffset;
    private DecomposerCache decomposer;
    private PatternExtractor patternExtractor;
    private IsotopicDistribution isotopicDistribution;
    private IsotopePatternGenerator patternGenerator;
    private MutableMeasurementProfile defaultProfile;

    @Override
    public <G, D, L> void importParameters(ParameterHelper helper, DataDocument<G, D, L> document, D dictionary) {
        if (document.hasKeyInDictionary(dictionary, "patternScorers")) {
            final Iterator<G> scorers = document.iteratorOfList(document.getListFromDictionary(dictionary, "patternScorers"));
            while (scorers.hasNext()) {
                getIsotopePatternScorers().add((IsotopePatternScorer) helper.unwrap(document, scorers.next()));
            }
        }
        if (document.hasKeyInDictionary(dictionary, "cutoff"))
            setCutoff(document.getDoubleFromDictionary(dictionary, "cutoff"));
        if (document.hasKeyInDictionary(dictionary, "intensityOffset"))
            setIntensityOffset(document.getDoubleFromDictionary(dictionary, "intensityOffset"));
        if (document.hasKeyInDictionary(dictionary, "patternExtractor"))
            setPatternExtractor((PatternExtractor) helper.unwrap(document, document.getFromDictionary(dictionary, "patternExtractor")));
        if (document.hasKeyInDictionary(dictionary, "isotopes"))
            setIsotopicDistribution((IsotopicDistribution) helper.unwrap(document, document.getFromDictionary(dictionary, "isotopes")));
    }

    @Override
    public <G, D, L> void exportParameters(ParameterHelper helper, DataDocument<G, D, L> document, D dictionary) {
        exportParameters(helper, document, dictionary, true);
    }


    public <G, D, L> void exportParameters(ParameterHelper helper, DataDocument<G, D, L> document, D dictionary, boolean withProfile) {
        document.addToDictionary(dictionary, "cutoff", cutoff);
        if (withProfile && defaultProfile!=null) document.addToDictionary(dictionary, "default", helper.wrap(document, defaultProfile));
        // export isotope distribution for relevant elements
        final ChemicalAlphabet alphabet;
        if (defaultProfile != null) {
            alphabet = defaultProfile.getFormulaConstraints().getChemicalAlphabet();
        } else {
            alphabet = ChemicalAlphabet.getExtendedAlphabet();
        }
        final IsotopicDistribution dist = isotopicDistribution.subset(alphabet.getElements());
        document.addToDictionary(dictionary, "isotopes", helper.wrap(document, dist));
        final L scorers = document.newList();
        for (IsotopePatternScorer scorer : isotopePatternScorers)
            document.addToList(scorers, helper.wrap(document, scorer));
        document.addListToDictionary(dictionary, "patternScorers", scorers);
        document.addToDictionary(dictionary, "patternExtractor", helper.wrap(document, patternExtractor));
        document.addToDictionary(dictionary, "intensityOffset", intensityOffset);

    }

    public static <G, D, L> IsotopePatternAnalysis loadFromProfile(DataDocument<G, D, L> document, G value) {
        final ParameterHelper helper = ParameterHelper.getParameterHelper();
        final D dict = document.getDictionary(value);
        if (!document.hasKeyInDictionary(dict, ANALYZER_NAME))
            throw new IllegalArgumentException("No field 'IsotopePatternAnalysis' in profile");
        final IsotopePatternAnalysis analyzer = (IsotopePatternAnalysis)helper.unwrap(document,
                document.getFromDictionary(dict, ANALYZER_NAME));
        if (document.hasKeyInDictionary(dict, "profile")) {
            final MeasurementProfile prof = ((MeasurementProfile) helper.unwrap(document, document.getFromDictionary(dict, "profile")));
            if (analyzer.defaultProfile==null) analyzer.defaultProfile=new MutableMeasurementProfile(prof);
            else analyzer.defaultProfile = new MutableMeasurementProfile(MutableMeasurementProfile.merge(prof, analyzer.defaultProfile));
        }
        return analyzer;
    }

    public <G, D, L> void writeToProfile(DataDocument<G, D, L> document, G value) {
        final ParameterHelper helper = ParameterHelper.getParameterHelper();
        final D dict = document.getDictionary(value);
        final D fpa = document.newDictionary();
        exportParameters(helper, document, fpa);
        document.addToDictionary(fpa, "$name", ANALYZER_NAME );
        document.addDictionaryToDictionary(dict, ANALYZER_NAME, fpa);
        if (document.hasKeyInDictionary(dict, "profile")) {
            final MeasurementProfile otherProfile = (MeasurementProfile) helper.unwrap(document, document.getFromDictionary(dict, "profile"));
            if (!otherProfile.equals(defaultProfile)) {
                if (defaultProfile != null) {
                    final D profDict = document.newDictionary();
                    new MutableMeasurementProfile(defaultProfile).exportParameters(helper, document, profDict);
                    document.addDictionaryToDictionary(fpa, "default", profDict);
                }
            }
        } else if (defaultProfile!=null){
            final D profDict = document.newDictionary();
            new MutableMeasurementProfile(defaultProfile).exportParameters(helper, document, profDict);
            document.addDictionaryToDictionary(dict, "profile", profDict);
        }
    }

    public IsotopePatternAnalysis() {
        this.isotopePatternScorers = new ArrayList<IsotopePatternScorer>();
        this.decomposer = new DecomposerCache();
        this.patternExtractor = new ExtractAll();
        this.isotopicDistribution = PeriodicTable.getInstance().getDistribution();
        this.cutoff = 0.01d;
        this.intensityOffset = 0d;
        this.patternGenerator = new FastIsotopePatternGenerator(isotopicDistribution, Normalization.Sum(1d));
    }

    public static IsotopePatternAnalysis defaultAnalyzer() {
        final PeriodicTable T = PeriodicTable.getInstance();
        final IsotopePatternAnalysis analyzer = new IsotopePatternAnalysis();
        double offset = 1.323d;
        analyzer.intensityOffset = 0d;
        analyzer.isotopePatternScorers.add(new MassDeviationScorer(new PiecewiseLinearFunctionIntensityDependency(new double[]{0.15, 0.05}, new double[]{
                1.0, 1.5
        })));
        analyzer.isotopePatternScorers.add(new LogNormDistributedIntensityScorer(new PiecewiseLinearFunctionIntensityDependency(new double[]{1.0, 0.3, 0.15, 0.03}, new double[]{
                0.7, 0.6, 0.8, 0.5
        })));
        final FormulaConstraints constr =  new FormulaConstraints(new ChemicalAlphabet(T.getAllByName("C", "H",
                "N", "O", "P", "S", "Cl", "Na")), null);
        constr.setUpperbound(T.getByName("Cl"), 1);
        constr.setUpperbound(T.getByName("Na"), 1);
        constr.setUpperbound(T.getByName("P"), 3);
        constr.setUpperbound(T.getByName("S"), 3);
        constr.setUpperbound(T.getByName("N"), 10);
        constr.setUpperbound(T.getByName("O"), 25);

        analyzer.defaultProfile = new MutableMeasurementProfile(new Deviation(10), new Deviation(5), new Deviation(5), new Deviation(2.5), constr, 0.008d, 0.02d);
        return analyzer;
    }

    public double getIntensityOffset() {
        return intensityOffset;
    }

    public void setIntensityOffset(double intensityOffset) {
        this.intensityOffset = intensityOffset;
    }

    public IsotopePatternGenerator getPatternGenerator() {
        return patternGenerator;
    }

    public void setPatternGenerator(IsotopePatternGenerator patternGenerator) {
        this.patternGenerator = patternGenerator;
    }

    public List<IsotopePattern> extractPatterns(Ms2Experiment experiment, double targetMz, boolean allowAdducts) {
        final List<IsotopePattern> patterns = new ArrayList<IsotopePattern>();
        for (Spectrum spec : experiment.getMs1Spectra()) {
            patterns.addAll(patternExtractor.extractPattern(getDefaultProfile(), spec, targetMz, allowAdducts));
        }
        return patterns;
    }

    public List<IsotopePattern> extractPatterns(Ms2Experiment experiment) {
        final List<IsotopePattern> patterns = new ArrayList<IsotopePattern>();
        for (Spectrum spec : experiment.getMs1Spectra()) {
            patterns.addAll(patternExtractor.extractPattern(getDefaultProfile(), spec));
        }
        return patterns;
    }

    private MeasurementProfile getProfile(MeasurementProfile measurementProfile) {
        if (measurementProfile==null) return defaultProfile;
        return MutableMeasurementProfile.merge(defaultProfile, measurementProfile);
    }

    public List<IsotopePattern> deisotope(Ms2Experiment experiment, double targetMz, MeasurementProfile profile) {
        final List<IsotopePattern> patterns = extractPatterns(experiment, targetMz, false);
        final ArrayList<IsotopePattern> results = new ArrayList<IsotopePattern>();
        for (IsotopePattern pattern : patterns)
            results.add(deisotopeSingle(experiment, pattern, profile));
        return results;
    }

    public List<IsotopePattern> deisotope(Ms2Experiment experiment, double targetMz) {
        return deisotope(experiment, targetMz, getDefaultProfile());
    }

    public List<IsotopePattern> deisotope(Ms2Experiment experiment, MeasurementProfile profile) {
        // search for monoisotopic mass
        final List<IsotopePattern> patterns = extractPatterns(experiment);
        final ArrayList<IsotopePattern> results = new ArrayList<IsotopePattern>();
        for (IsotopePattern pattern : patterns)
            results.add(deisotopeSingle(experiment, pattern, profile));
        return results;
    }

    public List<IsotopePattern> deisotope(Ms2Experiment experiment) {
        return deisotope(experiment, getDefaultProfile());
    }


    private IsotopePattern deisotopeSingle(Ms2Experiment experiment, IsotopePattern pattern, MeasurementProfile profile) {
        final PrecursorIonType ionization = experiment.getPrecursorIonType();
        if (ionization.isIonizationUnknown()) {
            // try different ionization types
            final List<Scored<MolecularFormula>> ionFormulas = new ArrayList<Scored<MolecularFormula>>();
            final int charge = ionization.getCharge();
            final Iterable<Ionization> ionModes = PeriodicTable.getInstance().getKnownIonModes(charge);
            for (Ionization ion : ionModes) {
                final List<MolecularFormula> formulas = decomposer.getDecomposer(profile.getFormulaConstraints().getChemicalAlphabet()).decomposeToFormulas(ion.subtractFromMass(pattern.getMonoisotopicMass()), profile.getAllowedMassDeviation(), profile.getFormulaConstraints());
                final double[] scores = scoreFormulas(pattern.getPattern(), formulas, experiment, profile);
                for (int k=0; k < formulas.size(); ++k) {
                    if (!Double.isInfinite(scores[k]))
                        ionFormulas.add(new Scored<MolecularFormula>(formulas.get(k).add(ion.getAtoms()), scores[k]));
                }
            }
            Collections.sort(ionFormulas, Collections.reverseOrder());
            return new IsotopePattern(pattern.getPattern(), ionFormulas);
        } else {
            // use given ionization
            final List<Scored<MolecularFormula>> neutralFormulas = new ArrayList<Scored<MolecularFormula>>();
            final List<MolecularFormula> formulas = decomposer.getDecomposer(profile.getFormulaConstraints().getChemicalAlphabet()).decomposeToFormulas(ionization.precursorMassToNeutralMass(pattern.getMonoisotopicMass()), profile.getAllowedMassDeviation(), profile.getFormulaConstraints());
            final double[] scores = scoreFormulas(pattern.getPattern(), formulas, experiment, profile);
            for (int k=0; k < formulas.size(); ++k) {
                neutralFormulas.add(new Scored<MolecularFormula>(formulas.get(k), scores[k]));
            }
            Collections.sort(neutralFormulas, Scored.<MolecularFormula>desc());
            return new IsotopePattern(pattern.getPattern(), neutralFormulas);
        }
    }

    public double[] scoreFormulas(SimpleSpectrum extractedSpectrum, double summedUpIntensities, List<MolecularFormula> formulas, Ms2Experiment experiment, MeasurementProfile profile) {
        //if summedUpIntensities <= 0 use intensity sum of pattern to normalize
        if (summedUpIntensities<=0) {
            summedUpIntensities = 0;
            for (int i = 0; i < extractedSpectrum.size(); i++) {
                summedUpIntensities += extractedSpectrum.getIntensityAt(i);
            }
        }
        //final PatternGenerator generator = new PatternGenerator(isotopicDistribution, extractedSpectrum.getIonization(), Normalization.Sum(summedUpIntensities));
        final SimpleMutableSpectrum spec = new SimpleMutableSpectrum(extractedSpectrum);

        normalize(spec, Normalization.Sum(summedUpIntensities));
        if (intensityOffset != 0d) {
            addOffset(spec, 0d, intensityOffset);
            normalize(spec, Normalization.Sum(summedUpIntensities));
        }

        if (spec.getIntensityAt(0) < cutoff){
            //intensity of first peak is below cutoff, cannot score
            double[] scores = new double[formulas.size()];
            Arrays.fill(scores, Double.NEGATIVE_INFINITY);
            return scores;
        }
        while (spec.getIntensityAt(spec.size()-1) < cutoff) spec.removePeakAt(spec.size()-1);
        normalize(spec, Normalization.Sum(summedUpIntensities));
        final Spectrum<Peak> measuredSpectrum = new SimpleSpectrum(spec);
        final double[] scores = new double[formulas.size()];
        int k=0;
        for (MolecularFormula f : formulas) {
            f = experiment.getPrecursorIonType().neutralMoleculeToMeasuredNeutralMolecule(f);
            final Spectrum<Peak> theoreticalSpectrum = patternGenerator.simulatePattern(f, experiment.getPrecursorIonType().getIonization());
            if (theoreticalSpectrum.size() < spec.size()) {
                // TODO: Just a Workaround!!! Find something better
                final SimpleMutableSpectrum workaround = new SimpleMutableSpectrum(measuredSpectrum);
                while (theoreticalSpectrum.size() < workaround.size()) workaround.removePeakAt(workaround.size()-1);
                normalize(workaround, Normalization.Sum(1));
                double score = 0d;
                for (IsotopePatternScorer scorer : isotopePatternScorers){
                    score += scorer.score(workaround, theoreticalSpectrum, Normalization.Sum(summedUpIntensities), experiment, profile);
                }
            } else {
                double score = 0d;
                for (IsotopePatternScorer scorer : isotopePatternScorers) {
                    final double s = scorer.score(measuredSpectrum, theoreticalSpectrum, Normalization.Sum(summedUpIntensities), experiment, profile);
                    if (Double.isInfinite(s)) {
                        score = s;
                        break;
                    } else score += s;
                }
                scores[k++] = score;
            }
        }
        return scores;
    }

    public double[] scoreFormulas(SimpleSpectrum extractedSpectrum, List<MolecularFormula> formulas, Ms2Experiment experiment,MeasurementProfile profile) {
        return scoreFormulas(extractedSpectrum, 1, formulas, experiment,profile);
    }

    public MutableMeasurementProfile getDefaultProfile() {
        return defaultProfile;
    }

    public void setDefaultProfile(MutableMeasurementProfile defaultProfile) {
        this.defaultProfile = defaultProfile;
    }

    public List<IsotopePatternScorer> getIsotopePatternScorers() {
        return isotopePatternScorers;
    }

    public void setIsotopePatternScorers(List<IsotopePatternScorer> isotopePatternScorers) {
        this.isotopePatternScorers = isotopePatternScorers;
    }

    public double getCutoff() {
        return cutoff;
    }

    public void setCutoff(double cutoff) {
        this.cutoff = cutoff;
    }

    public DecomposerCache getDecomposer() {
        return decomposer;
    }

    public void setDecomposer(DecomposerCache decomposer) {
        this.decomposer = decomposer;
    }

    public PatternExtractor getPatternExtractor() {
        return patternExtractor;
    }

    public void setPatternExtractor(PatternExtractor patternExtractor) {
        this.patternExtractor = patternExtractor;
    }

    public IsotopicDistribution getIsotopicDistribution() {
        return isotopicDistribution;
    }

    public void setIsotopicDistribution(IsotopicDistribution isotopicDistribution) {
        this.isotopicDistribution = isotopicDistribution;
    }
}