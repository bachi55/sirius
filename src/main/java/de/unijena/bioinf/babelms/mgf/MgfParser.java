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
package de.unijena.bioinf.babelms.mgf;

import de.unijena.bioinf.ChemistryBase.chem.Charge;
import de.unijena.bioinf.ChemistryBase.chem.Ionization;
import de.unijena.bioinf.ChemistryBase.chem.PeriodicTable;
import de.unijena.bioinf.ChemistryBase.ms.*;
import de.unijena.bioinf.babelms.Parser;
import de.unijena.bioinf.babelms.SpectralParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MgfParser extends SpectralParser implements Parser<Ms2Experiment> {

    @Override
    public Iterator<Ms2Spectrum<Peak>> parseSpectra(final BufferedReader reader) throws IOException {
        return new Iterator<Ms2Spectrum<Peak>>() {
            MutableMs2Spectrum spec = readNext(reader);
            @Override
            public boolean hasNext() {
                return spec!=null;
            }

            @Override
            public Ms2Spectrum<Peak> next() {
                final MutableMs2Spectrum o = spec;
                try {
                    spec = readNext(reader);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return o;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    private MutableMs2Spectrum prototype;

    public MgfParser() {
        this.prototype = new MutableMs2Spectrum();
    }

    public void setCurrentLevel(int level) {
        prototype.setMsLevel(level);
    }

    public void setIonization(Ionization ion) {
        prototype.setIonization(ion);
    }

    private MutableMs2Spectrum readNext(BufferedReader reader) throws IOException {
        String line;
        boolean reading=false;
        MutableMs2Spectrum spec = null;
        while ((line=reader.readLine())!=null) {
            if (!reading && line.startsWith("BEGIN IONS")) {
                spec = new MutableMs2Spectrum(prototype);
                reading=true;
            } else if (reading && line.startsWith("END IONS")) {
                return spec;
            } else if (reading) {
                if (Character.isDigit(line.charAt(0))) {
                    final String[] parts = line.split("\\s+");
                    spec.addPeak(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
                } else {
                    final int i = line.indexOf('=');
                    if (i>=0) handleKeyword(spec, line.substring(0, i), line.substring(i+1));
                }
            } else {
                final int i = line.indexOf('=');
                if (i>=0) handleKeyword(prototype, line.substring(0, i), line.substring(i+1));
            }
        }
        return null;
    }

    private static Pattern CHARGE_PATTERN = Pattern.compile("(\\d+)([+-])?");

    private void handleKeyword(MutableMs2Spectrum spec, String keyword, String value) throws IOException {
        if (keyword.equals("PEPMASS")) {
            spec.setPrecursorMz(Double.parseDouble(value));
        } else if (keyword.equals("CHARGE")) {
            final Matcher m = CHARGE_PATTERN.matcher(value);
            m.find();
            int charge = ("-".equals(m.group(2))) ? -Integer.parseInt(m.group(1)) : Integer.parseInt(m.group(1));
            if (charge==0) charge=1;
            if (spec.getIonization()==null || spec.getIonization().getCharge() != charge)
                spec.setIonization(new Charge(charge));
        } else if (keyword.startsWith("ION")) {
            final Ionization ion = PeriodicTable.getInstance().ionByName(value);
            if (ion==null) throw new IOException("Unknown ion '" + value +"'");
            else spec.setIonization(ion);
        } else if (keyword.contains("LEVEL")) {
            spec.setMsLevel(Integer.parseInt(value));
        } else {

        }
    }

    private final ArrayDeque<Ms2Spectrum<Peak>> buffer = new ArrayDeque<Ms2Spectrum<Peak>>();

    @Override
    public Ms2Experiment parse(BufferedReader reader) throws IOException {
        final Iterator<Ms2Spectrum<Peak>> iter = parseSpectra(reader);
        while (iter.hasNext()) buffer.addLast(iter.next());
        if (buffer.isEmpty()) return null;
        final MutableMs2Experiment exp = new MutableMs2Experiment();
        exp.setMs2Spectra(new ArrayList<Ms2Spectrum<Peak>>());
        exp.setMs1Spectra(new ArrayList<Spectrum<Peak>>());
        exp.setIonMass(buffer.peekFirst().getPrecursorMz());
        while (!buffer.isEmpty() && Math.abs(buffer.peekFirst().getPrecursorMz()-exp.getIonMass()) < 0.002) {
            final Ms2Spectrum<Peak> spec = buffer.pollFirst();
            if (spec.getMsLevel()==1) exp.getMs1Spectra().add(spec);
            else exp.getMs2Spectra().add(spec);
        }
        return exp;
    }
}
