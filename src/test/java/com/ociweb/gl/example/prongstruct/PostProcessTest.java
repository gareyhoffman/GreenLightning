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

// ProngStructs cannot be recursive on non-null init
    //PostProcessTest getObject1();
    //PostProcessTestContainer getObject2();
}
