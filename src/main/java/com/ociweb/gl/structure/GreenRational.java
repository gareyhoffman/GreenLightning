package com.ociweb.gl.structure;

import com.ociweb.pronghorn.structure.annotations.ProngStruct;
import com.ociweb.pronghorn.structure.annotations.ProngStructWriting;

@ProngStruct
public abstract class GreenRational implements ProngStructWriting {
    public abstract long getNumerator();
    public abstract void setNumerator(long value);

    public abstract long getDenominator();
    public abstract void setDenominator(long value);

    public final double getValue() {
        return (double) getNumerator() / (double) getDenominator();
    }

    public String toString() {
        return String.valueOf(getNumerator()) + "/" + getDenominator() + "=" + getValue();
    }
}
