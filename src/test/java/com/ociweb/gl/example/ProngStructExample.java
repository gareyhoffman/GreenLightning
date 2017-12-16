package com.ociweb.gl.example;

import com.ociweb.gl.api.*;
import com.ociweb.gl.structure.GreenRational;

class ProngStructBehavior implements PubSubMethodListener, StartupListener {
    private final GreenCommandChannel cmd;

    ProngStructBehavior(GreenRuntime runtime) {
        cmd = runtime.newCommandChannel();
        cmd.ensureDynamicMessaging();
    }

    @Override
    public void startup() {
        cmd.publishTopic("topic");
    }

    boolean onRational(CharSequence topic, GreenRational data) {
        return true;
    }
}

public class ProngStructExample implements GreenApp {

    public static void main(String[] args) {
        GreenRuntime.run(new ProngStructExample(),args);
    }

    @Override
    public void declareConfiguration(Builder builder) {

    }

    @Override
    public void declareBehavior(GreenRuntime runtime) {
        ProngStructBehavior behavior = new ProngStructBehavior(runtime);
        runtime.registerListener(behavior)
                .addSubscription("topic", GreenRational.class, behavior::onRational);
    }
}