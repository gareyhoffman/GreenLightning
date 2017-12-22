package com.ociweb.gl.example;

import com.ociweb.gl.api.*;
import com.ociweb.gl.structure.GreenRational;
import com.ociweb.gl.structure.GreenRationalStruct;
import com.ociweb.pronghorn.structure.annotations.ProngProperty;
import com.ociweb.pronghorn.structure.annotations.ProngStruct;

@ProngStruct
abstract class Big {
    abstract byte getByte();
    abstract void setByte(byte value);

    abstract boolean getBoolean();
    abstract void setBoolean(boolean value);

    abstract char getChar();
    abstract void setChar(char value);

    abstract short getShort();
    abstract void setShort(short value);

    abstract int getInt();
    abstract void setInt(int value);

    abstract long getLong();
    abstract void setLong(long value);

    abstract float getFloat();
    abstract void setFloat(float value);

    abstract double getDouble();
    abstract void setDouble(double value);

    @ProngProperty(nullable=true)
    abstract Byte getOByte();
    @ProngProperty(nullable=true)
    abstract void setOByte(Byte value);

    @ProngProperty(nullable=true)
    abstract Boolean getOBoolean();
    @ProngProperty(nullable=true)
    abstract void setOBoolean(Boolean value);

    @ProngProperty(nullable=true)
    abstract Character getOChar();
    @ProngProperty(nullable=true)
    abstract void setOChar(Character value);

    @ProngProperty(nullable=true)
    abstract Short getOShort();
    @ProngProperty(nullable=true)
    abstract void setOShort(Short value);

    @ProngProperty(nullable=true)
    abstract Integer getOInt();
    @ProngProperty(nullable=true)
    abstract void setOInt(Integer value);

    @ProngProperty(nullable=true)
    abstract Long getOLong();
    @ProngProperty(nullable=true)
    abstract void setOLong(Long value);

    @ProngProperty(nullable=true)
    abstract Float getOFloat();
    abstract void setOFloat(Float value);

    abstract Double getODouble();
    @ProngProperty(nullable=true)
    abstract void setODouble(Double value);

    abstract GreenRational getRational1();
    abstract void setRational1(GreenRational rational);

    @ProngProperty(nullable=true)
    abstract GreenRational getRational2();
    @ProngProperty(nullable=true)
    abstract void setRational2(GreenRational rational);

    @ProngProperty(nullable=true)
    abstract GreenRational getRational3();
    abstract void setRational3(GreenRational rational);

    abstract GreenRational getRational4();
    @ProngProperty(nullable=true)
    abstract void setRational4(GreenRational rational);
}

class ProngStructBehavior implements PubSubMethodListener, TimeListener {
    private final GreenCommandChannel cmd;
    private final GreenRational writeData = new GreenRationalStruct();

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