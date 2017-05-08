package org.fossasia.pslab.communication.analogChannel;

import android.util.Log;

import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AnalogInputSource {

    private static String TAG = "AnalogInputSource";

    private double gainValues[], range[];
    private boolean gainEnabled = false, inverted = false, calibrationReady = false;
    private double gain = 0;
    public int gainPGA, CHOSA;
    private int inversion = 1;
    private int defaultOffsetCode = 0;
    private int scaling = 1;
    private String channelName;
    public PolynomialFunction calPoly10;
    public PolynomialFunction calPoly12;
    public PolynomialFunction voltToCode10;
    private PolynomialFunction voltToCode12;
    private List<Double> adc_shifts = new ArrayList<>();
    private List<PolynomialFunction> polynomials = new ArrayList<>(); //list of maps

    public AnalogInputSource(String channelName) {
        AnalogConstants analogConstants = new AnalogConstants();
        this.channelName = channelName;
        range = analogConstants.inputRanges.get(channelName);
        gainValues = analogConstants.gains;
        this.CHOSA = analogConstants.picADCMultiplex.get(channelName);

        calPoly10 = new PolynomialFunction(new double[]{0., 3.3 / 1023, 0.});
        calPoly12 = new PolynomialFunction(new double[]{0., 3.3 / 4095, 0.});

        if (range[1] - range[0] < 0) {
            inverted = true;
            inversion = -1;
        }
        if (channelName.equals("CH1")) {
            gainEnabled = true;
            gainPGA = 1;
            gain = 0;
        } else if (channelName.equals("CH2")) {
            gainEnabled = true;
            gainPGA = 2;
            gain = 0;
        }
        gain = 0;
        regenerateCalibration();
    }

    public Boolean setGain(int index) {
        if (!gainEnabled) {
            Log.e(channelName, "Analog gain is not available");
            return false;
        }
        gain = gainValues[index];
        regenerateCalibration();
        return true;
    }

    boolean inRange(double val) {
        double sum = voltToCode12.value(val);
        if (sum >= 50 && sum <= 4095) {
            return true;
        }
        return false;
    }

    boolean conservativeInRange(double val) {
        double solution = voltToCode12.value(val);
        if (solution >= 50 && solution <= 4000) {
            return true;
        }
        return false;
    }

    List<Double> loadCalibrationTable(double[] table, double slope, double intercept) {
        for (int i = 0; i < table.length; i++) {
            adc_shifts.add(table[i] * slope - intercept);
        }
        return adc_shifts;
    }

    public void ignoreCalibration() {
        calibrationReady = false;
    }

    public void loadPolynomials(List<double[]> polys)
    {
        for (int i = 0; i < polys.size(); i++) {
            polynomials.add(new PolynomialFunction(polys.get(i)));
        }
    }

    public void regenerateCalibration() {
        double A, B, intercept, slope;
        B = range[1];
        A = range[0];
        if (gain != -1) {
            gain = gainValues[(int) gain];
            B /= gain;
            A /= gain;
        }
        slope = B - A;
        intercept = A;
        if (!calibrationReady && gain == 8) {
            calPoly10 = new PolynomialFunction(new double[]{0., slope / 1023, intercept});
            calPoly12 = new PolynomialFunction(new double[]{0., slope / 4095, intercept});
        }//else cases need to be worked on!!!

        voltToCode10 = new PolynomialFunction(new double[]{0., 1023. / slope, -1023 * intercept / slope});
        voltToCode12 = new PolynomialFunction(new double[]{0., 4095., -4095 * intercept / slope});
    }

    double cal12(double RAW) {
        double avg_shifts = (adc_shifts.get((int) Math.floor(RAW)) + adc_shifts.get((int) Math.ceil(RAW))) / 2;
        RAW -= 4095 * avg_shifts / 3.3;
        return (polynomials.get((int) gain).value(RAW));

    }

    double cal10(double RAW) {
        RAW *= 4095 / 1023;
        double avg_shifts = (adc_shifts.get((int) Math.floor(RAW)) + adc_shifts.get((int) Math.ceil(RAW))) / 2;
        RAW -= 4095 * avg_shifts / 3.3;
        return (polynomials.get((int) gain).value(RAW));
    }
}