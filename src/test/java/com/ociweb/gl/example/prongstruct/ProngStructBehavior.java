package com.ociweb.gl.example.prongstruct;

import com.ociweb.gl.api.*;
import com.ociweb.pronghorn.pipe.ChannelWriter;

public class ProngStructBehavior implements PubSubMethodListener, TimeListener {
    private final GreenCommandChannel cmd;
    private final String topic;
    private final Big writeData = new com.ociweb.gl.example.prongstruct.BigStruct();

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
                writeData.channelWrite(writer);
            }
        });
    }

    boolean onStruct(CharSequence topic, Big data) {
        System.out.println(topic);
        System.out.println(data);
        return true;
    }
}
