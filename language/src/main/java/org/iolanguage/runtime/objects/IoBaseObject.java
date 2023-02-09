package org.iolanguage.runtime.objects;

import com.oracle.truffle.api.interop.TruffleObject;

public interface IoBaseObject extends TruffleObject {

    public IoBaseObject getPrototype();

    public void setPrototype(final IoBaseObject prototype);

    public String toString(int depth);
}
