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

package de.unijena.bioinf.ChemistryBase.ms;

import de.unijena.bioinf.ChemistryBase.chem.Ionization;
import de.unijena.bioinf.ChemistryBase.chem.MolecularFormula;

/**
 * An annotated peak after tree computation is done. Can be used as FragmentAnnotation in FTree
 */
public final class AnnotatedPeak {

    /**
     * The molecular formula that is assigned to this peak
     */
    private final MolecularFormula molecularFormula;

    /**
     * the mass of the peak after peak merging
     */
    private final double mass;

    /**
     * the mass of the peak after recalibration
     */
    private final double recalibratedMass;

    /**
     * the relative intensity of the merged peak
     */
    private final double relativeIntensity;

    /**
     * the ionization of the peak
     */
    private final Ionization ionization;

    /**
     * the original peaks that were merged into this peak (maximal one per MS/MS spectrum)
     */
    private final Peak[] originalPeaks;

    /**
     * the collision energies of the original peaks. Should have the same size as the originalPeaks array. The i-th
     * collision energy belongs to the i-th peak.
     */
    private final CollisionEnergy[] collisionEnergies;

    public AnnotatedPeak(MolecularFormula getFormula, double getMass, double recalibratedMass, double relativeIntensity, Ionization ionization, Peak[] originalPeaks, CollisionEnergy[] collisionEnergies) {
        this.molecularFormula = getFormula;
        this.mass = getMass;
        this.recalibratedMass = recalibratedMass;
        this.relativeIntensity = relativeIntensity;
        this.ionization = ionization;
        this.originalPeaks = originalPeaks;
        this.collisionEnergies = collisionEnergies;
    }

    public MolecularFormula getMolecularFormula() {
        return molecularFormula;
    }

    public double getMass() {
        return mass;
    }

    public double getRecalibratedMass() {
        return recalibratedMass;
    }

    public double getRelativeIntensity() {
        return relativeIntensity;
    }

    public Ionization getIonization() {
        return ionization;
    }

    public Peak[] getOriginalPeaks() {
        return originalPeaks;
    }

    public CollisionEnergy[] getCollisionEnergies() {
        return collisionEnergies;
    }

    public AnnotatedPeak withFormula(MolecularFormula newFormula) {
        return new AnnotatedPeak(newFormula, mass, recalibratedMass, relativeIntensity, ionization, originalPeaks, collisionEnergies);
    }

    public AnnotatedPeak withIonization(Ionization ion) {
        return new AnnotatedPeak(molecularFormula, mass, recalibratedMass, relativeIntensity, ion, originalPeaks, collisionEnergies);
    }

    public double getMaximalIntensity() {
        double m = 0d;
        for (Peak p : originalPeaks) {
            if (p!=null) m = Math.max(p.getIntensity(), m);
        }
        return m;
    }
    public double getSumedIntensity() {
        double m = 0d;
        for (Peak p : originalPeaks) {
            if (p!=null) m += p.getIntensity();
        }
        return m;
    }
}
