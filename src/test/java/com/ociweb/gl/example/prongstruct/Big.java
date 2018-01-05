package com.ociweb.gl.example.prongstruct;

import com.ociweb.gl.structure.GreenRational;
import com.ociweb.pronghorn.structure.annotations.ProngProperty;
import com.ociweb.pronghorn.structure.annotations.ProngStruct;
import com.ociweb.pronghorn.structure.annotations.ProngStructFormatter;
import com.ociweb.pronghorn.structure.annotations.ProngStructWriting;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

class SuperDuper {
    public SuperDuper() {
    }

    public SuperDuper(SuperDuper rhs) {
    }

    @Override
    public int hashCode() {
        return 0;
    }

    public void clear() {
    }

    public void assignFrom(SuperDuper rhs) {
    }

    public void toStringProperties(ProngStructFormatter sb) {

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !(o instanceof Big)) return false;
        return true;
    }

    public void readExternal(ObjectInput input) throws IOException, ClassNotFoundException {
    }

    public void writeExternal(ObjectOutput output) throws IOException {
    }
}

@ProngStruct
public abstract class Big extends SuperDuper implements ProngStructWriting {

    public Big() {

    }

    public Big(Big rhs) {
        super(rhs);
    }

    // Primitives
    @ProngProperty(nullable=true)
    abstract boolean getBoolean();
    @ProngProperty(nullable=true)
    abstract void setBoolean(boolean value);
    abstract boolean isBooleanNull();
    @ProngProperty(nullable=true)
    abstract byte getByte();
    abstract void setByte(byte value);
    abstract boolean isByteNull();
    abstract short getShort();
    @ProngProperty(nullable=true)
    abstract void setShort(short value);
    abstract boolean isShortNull();
    abstract int getInt();
    abstract void setInt(int value);
    abstract long getLong();
    abstract void setLong(long value);
    abstract char getChar();
    abstract void setChar(char value);
    abstract float getFloat();
    abstract void setFloat(float value);
    abstract double getDouble();
    abstract void setDouble(double value);

    // Boxed
    @ProngProperty(nullable=true)
    abstract Boolean getOBoolean();
    @ProngProperty(nullable=true)
    abstract void setOBoolean(Boolean value);
    @ProngProperty(nullable=true)
    abstract Byte getOByte();
    @ProngProperty(nullable=true)
    abstract void setOByte(Byte value);
    @ProngProperty(nullable=true)
    abstract Short getOShort();
    @ProngProperty(nullable=true)
    abstract void setOShort(Short value);
    abstract Integer getOInt();
    @ProngProperty(nullable=true)
    abstract void setOInt(Integer value);
    @ProngProperty(nullable=true)
    abstract Long getOLong();
    @ProngProperty(nullable=true)
    abstract void setOLong(Long value);
    abstract Character getOChar();
    abstract void setOChar(Character value);
    abstract Float getOFloat();
    @ProngProperty(nullable=true)
    abstract void setOFloat(Float value);
    @ProngProperty(nullable=true)
    abstract Double getODouble();
    abstract void setODouble(Double value);

    // String
    abstract CharSequence getString1();
    abstract void setString1(CharSequence value);
    @ProngProperty(nullable=true)
    abstract CharSequence getString2();
    abstract void setString2(CharSequence value);
    abstract CharSequence getString3();
    @ProngProperty(nullable=true)
    abstract void setString3(CharSequence value);
    @ProngProperty(nullable=true)
    abstract CharSequence getString4();
    @ProngProperty(nullable=true)
    abstract void setString4(CharSequence value);

    // Structs
    abstract GreenRational getGreenRational();
    @ProngProperty(nullable=true)
    abstract Big getBigStruct();
    abstract void setBigStruct(Big value);
    @ProngProperty(nullable=true)
    abstract Big getBigStruct2();
    @ProngProperty(nullable=true)
    abstract void setBigStruct2(Big value);

    void change() {
        this.setBoolean(!this.getBoolean());
        this.setByte((byte)(this.getByte() + 1));
        this.setShort((short)(this.getShort() + 1));
        this.setInt((int)(this.getInt() + 1));
        this.setLong((long)(this.getLong() + 1));
        this.setChar((char)(this.getChar() + 1));
        this.setFloat((float)(this.getFloat() + 1.0));
        this.setDouble((double)(this.getDouble() + 1.0));
        this.getGreenRational().setNumerator(this.getGreenRational().getNumerator() + 3);
    }
}
