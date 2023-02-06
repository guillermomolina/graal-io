package org.truffle.io.runtime.objects;

import com.oracle.truffle.api.interop.TruffleObject;

public interface IoTruffleObject extends TruffleObject {

    public IoObject getPrototype();

    public void setPrototype(final IoObject prototype);
}
