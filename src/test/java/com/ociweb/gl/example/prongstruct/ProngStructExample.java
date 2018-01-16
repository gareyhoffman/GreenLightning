package com.ociweb.gl.example.prongstruct;

import com.ociweb.gl.api.*;
import com.ociweb.gl.impl.stage.CallableTypeSafeMethod;

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
        String topic = "big time value types";
        final ProngStructBehavior behavior = new ProngStructBehavior(runtime, topic);
        runtime.registerListener(behavior)
                .addSubscription(topic, Big.class, new CallableTypeSafeMethod<Big>() {
                    @Override
                    public boolean method(CharSequence title, Big data) {
                        return behavior.onStruct(title, data);
                    }
                });
    }
}