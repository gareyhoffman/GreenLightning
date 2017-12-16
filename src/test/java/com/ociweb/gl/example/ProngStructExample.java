package com.ociweb.gl.example;

import com.ociweb.gl.api.*;
import com.ociweb.gl.structure.GreenRational;
import com.ociweb.gl.structure.GreenRationalExternalizable;

class ProngStructBehavior implements PubSubMethodListener, TimeListener {
    private final GreenCommandChannel cmd;
    private final GreenRationalExternalizable writeData = new GreenRationalExternalizable();

    ProngStructBehavior(GreenRuntime runtime) {
        cmd = runtime.newCommandChannel();
        cmd.ensureDynamicMessaging();
        writeData.setDenominator(1000);
    }

    @Override
    public void timeEvent(long time, int iteration) {
        writeData.setNumerator(writeData.getNumerator() + 1);
        cmd.publishTopic("topic", writeData::write);

    }

    boolean onRational(CharSequence topic, GreenRational data) {
        System.out.println(data);
        return true;
    }
}

public class ProngStructExample implements GreenApp {

    public static void main(String[] args) {
        GreenRuntime.run(new ProngStructExample(),args);
    }

    @Override
    public void declareConfiguration(Builder builder) {
        builder.setTimerPulseRate(1000);
    }

    @Override
    public void declareBehavior(GreenRuntime runtime) {
        ProngStructBehavior behavior = new ProngStructBehavior(runtime);
        runtime.registerListener(behavior)
                .addSubscription("topic", GreenRational.class, behavior::onRational);
    }
}