package com.ociweb.gl.example.prongstruct;

import com.ociweb.pronghorn.structure.annotations.ProngProperty;
import com.ociweb.pronghorn.structure.annotations.ProngStruct;
/*
// Must be interface or abstract class
@ProngStruct()
class PostProcessTest2 {
}
*/

@ProngStruct()
interface PostProcessTestContainer {
    PostProcessTest getMember();
}

@ProngStruct(/*suffix = ""*/)
interface PostProcessTest {
// Property must have identical types on getter and setter
    //double getSomething();
    //void setSomething(float value);

// Primitives cannot have any combination of nullable
    //@ProngProperty(nullable = true)
    //int getInt1();
    //@ProngProperty(nullable = true)
    //void setInt2(int value);
    //@ProngProperty(nullable = true)
    //int getInt3();
    //@ProngProperty(nullable = true)
    //void setInt3(int value);

// Boxed types must have at least one nullable annotation
    //Integer getBInt1();
    //void setBInt2(Integer value);
    //int getBInt3();
    //void setBInt3(Integer value);

// ProngStructs cannot be recursive on non-null init
    //PostProcessTest getObject1();
    //PostProcessTestContainer getObject2();
}
