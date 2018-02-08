package cn.apisium.pcraft;

public class ObjectProxy {
    private Object obj;
    ObjectProxy (Object obj) { this.obj = obj; }
    public Object get () { return obj;}
}
