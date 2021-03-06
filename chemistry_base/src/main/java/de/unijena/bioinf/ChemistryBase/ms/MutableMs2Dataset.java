package de.unijena.bioinf.ChemistryBase.ms;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Created by ge28quv on 05/07/17.
 */
public class MutableMs2Dataset implements Ms2Dataset {

    private List<MutableMs2Experiment> experiments;
    String profile; //todo enum
    double isolationWindowWidth;
    private IsolationWindow isolationWindow;
    private MeasurementProfile measurementProfile;
    private DatasetStatistics statistics;

    public MutableMs2Dataset() {
        experiments = new ArrayList<>();
        profile = "default";
    }

    /**
     *
     * @param experiments
     * @param profile profile which is used for Sirius. MUST EXIST! But cannot be tested at this moment //todo find better solution to set profile. e.g. move MutableMs2Dataset
     * @param isolationWindowWidth maximum isolation window width. will be used to bound isolation window estimation.
     * @param measurementProfile
     */
    public MutableMs2Dataset(List<Ms2Experiment> experiments, String profile, double isolationWindowWidth, MeasurementProfile measurementProfile) {
        this.experiments = new ArrayList<>();
        for (Ms2Experiment experiment : experiments) {
            this.experiments.add(new MutableMs2Experiment(experiment));
        }
        this.profile = profile;
        this.isolationWindowWidth = isolationWindowWidth;
        this.measurementProfile = measurementProfile; //todo do we need profile and Measurement profile!?
    }

    public MutableMs2Dataset(Ms2Dataset dataset) {
        this.experiments = new ArrayList<>();
        for (Ms2Experiment experiment : dataset.getExperiments()) {
            this.experiments.add(new MutableMs2Experiment(experiment));
        }
        this.profile = dataset.getProfile();
        this.isolationWindowWidth = dataset.getIsolationWindowWidth();
        this.measurementProfile = new MutableMeasurementProfile(dataset.getMeasurementProfile());
        this.isolationWindow = dataset.getIsolationWindow();
        this.setDatasetStatistics(dataset.getDatasetStatistics());
    }


    public IsolationWindow getIsolationWindow() {
        return isolationWindow;
    }

    public void setIsolationWindow(IsolationWindow isolationWindow) {
        this.isolationWindow = isolationWindow;
    }


    public List<MutableMs2Experiment> getExperiments() {
        return experiments;
    }

    public void setExperiments(List<Ms2Experiment> experiments) {
        this.experiments = new ArrayList<>();
        for (Ms2Experiment experiment : experiments) {
            this.experiments.add(new MutableMs2Experiment(experiment));
        }
    }


    public MeasurementProfile getMeasurementProfile() {
        return measurementProfile;
    }

    public void setMeasurementProfile(MeasurementProfile measurementProfile) {
        this.measurementProfile = measurementProfile;
    }


    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }


    public double getIsolationWindowWidth() {
        return isolationWindowWidth;
    }

    public void setIsolationWindowWidth(double isolationWindowWidth) {
        this.isolationWindowWidth = isolationWindowWidth;
    }


    @Override
    public Iterator<Ms2Experiment> iterator() {
        return new Iterator<Ms2Experiment>() {
            private int index;
            @Override
            public boolean hasNext() {
                return index < experiments.size();
            }

            @Override
            public Ms2Experiment next() {
                if (index < experiments.size())
                    return experiments.get(index++);
                else
                    throw new NoSuchElementException();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

        };
    }

    public void setDatasetStatistics(DatasetStatistics statistics){
        this.statistics = statistics;
    }

    @Override
    public DatasetStatistics getDatasetStatistics() {
        return statistics;
    }
}
