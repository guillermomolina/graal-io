package org.iolanguage.runtime.objects;

import com.oracle.truffle.api.interop.TruffleObject;

public interface IoObject extends TruffleObject {

    public IoObject getPrototype();

    public void setPrototype(final IoObject prototype);

    public String toString(int depth);
}
