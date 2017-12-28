package com.ociweb.gl.example.prongstruct;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.PubSubMethodListener;
import com.ociweb.gl.api.TimeListener;

public class ProngStructBehavior implements PubSubMethodListener, TimeListener {
    private final GreenCommandChannel cmd;
    private final String topic;
    private final Big writeData = new com.ociweb.gl.example.prongstruct.BigStruct();

    ProngStructBehavior(GreenRuntime runtime, String topic) {
        cmd = runtime.newCommandChannel();
        this.topic = topic;
        cmd.ensureDynamicMessaging();
        writeData.getGreenRational().setDenominator(1000);
    }

    @Override
    public void timeEvent(long time, int iteration) {
        writeData.change();
        cmd.publishTopic(topic, writeData::write);
    }

    boolean onStruct(CharSequence topic, Big data) {
        System.out.println(topic);
        System.out.println(data);
        return true;
    }
}
