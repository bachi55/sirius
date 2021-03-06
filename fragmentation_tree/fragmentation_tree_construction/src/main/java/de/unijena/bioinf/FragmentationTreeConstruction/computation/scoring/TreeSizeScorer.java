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
package de.unijena.bioinf.FragmentationTreeConstruction.computation.scoring;

import de.unijena.bioinf.ChemistryBase.algorithm.ParameterHelper;
import de.unijena.bioinf.ChemistryBase.data.DataDocument;
import de.unijena.bioinf.FragmentationTreeConstruction.model.ProcessedInput;
import de.unijena.bioinf.FragmentationTreeConstruction.model.ProcessedPeak;
import de.unijena.bioinf.FragmentationTreeConstruction.model.Scoring;

import java.util.List;

public class TreeSizeScorer implements PeakScorer {


    public final static class TreeSizeBonus {
        public final double score;

        public TreeSizeBonus(double score) {
            this.score = score;
        }
    }

    public double fastReplace(final ProcessedInput processedInput, final TreeSizeBonus newBonus) {
        // fast replace of peak scores. Dirty hack. be careful what you are doing!
        final Scoring scoring = processedInput.getAnnotationOrThrow(Scoring.class);
        final TreeSizeBonus oldBonus = processedInput.getAnnotation(TreeSizeBonus.class, defaultBonus);
        final double diff = newBonus.score - oldBonus.score;
        if (Math.abs(diff) > 1e-12) {
            final double[] xs = scoring.getPeakScores();
            for (int i=0, n = xs.length-1; i<n; ++i) {
                xs[i] += diff;
            }
        }
        processedInput.setAnnotation(TreeSizeBonus.class, newBonus);
        return diff;

    }

    private TreeSizeBonus defaultBonus;

    public TreeSizeScorer() {
    }

    public TreeSizeScorer(double treeSizeScore) {
        this.defaultBonus = new TreeSizeBonus(treeSizeScore);
    }

    public double getTreeSizeScore() {
        return defaultBonus.score;
    }

    @Deprecated
    public void setTreeSizeScore(double treeSizeScore) {
        this.defaultBonus = new TreeSizeBonus(treeSizeScore);
    }

    @Override
    public void score(List<ProcessedPeak> peaks, ProcessedInput input, double[] scores) {
        final double bonus = input.getAnnotation(TreeSizeBonus.class, defaultBonus).score;
        for (int i=0; i < peaks.size(); ++i) {
            scores[i] += bonus;
        }
    }

    @Override
    public <G, D, L> void importParameters(ParameterHelper helper, DataDocument<G, D, L> document, D dictionary) {
        defaultBonus = new TreeSizeBonus(document.getDoubleFromDictionary(dictionary, "score"));
    }

    @Override
    public <G, D, L> void exportParameters(ParameterHelper helper, DataDocument<G, D, L> document, D dictionary) {
        document.addToDictionary(dictionary, "score", defaultBonus.score);
    }
}
