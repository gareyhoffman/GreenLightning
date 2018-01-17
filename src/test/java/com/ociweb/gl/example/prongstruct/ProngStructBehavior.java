package com.ociweb.gl.example.prongstruct;

import com.ociweb.gl.api.*;
import com.ociweb.pronghorn.pipe.ChannelWriter;

public class ProngStructBehavior implements PubSubMethodListener, TimeListener {
    private final GreenCommandChannel cmd;
    private final String topic;
    private final BigStruct writeData = new BigStruct();

    ProngStructBehavior(GreenRuntime runtime, String topic) {
        cmd = runtime.newCommandChannel();
        this.topic = topic;
        cmd.ensureDynamicMessaging();
        writeData.getGreenRationalCCR().setDenominator(1000);
    }

    @Override
    public void timeEvent(long time, int iteration) {
        writeData.change();
        cmd.publishTopic(topic, new Writable() {
            @Override
            public void write(ChannelWriter writer) {
                writer.write(writeData);
            }
        });
    }

    boolean onStruct(CharSequence topic, Big data) {
        System.out.println(topic);
        System.out.println(data);
        return true;
    }
}
