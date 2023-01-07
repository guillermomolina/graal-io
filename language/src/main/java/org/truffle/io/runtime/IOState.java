/*
 * Copyright (c) 2022, 2023, Guillermo Adrián Molina. All rights reserved.
 */
/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.truffle.io.runtime;

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.truffle.io.IOLanguage;
import org.truffle.io.NotImplementedException;
import org.truffle.io.builtins.IOAddToHostClassPathBuiltinFactory;
import org.truffle.io.builtins.IOBuiltinNode;
import org.truffle.io.builtins.IOCloneBuiltinFactory;
import org.truffle.io.builtins.IODefineFunctionBuiltinFactory;
import org.truffle.io.builtins.IOEvalBuiltinFactory;
import org.truffle.io.builtins.IOExitBuiltinFactory;
import org.truffle.io.builtins.IOGetSizeBuiltinFactory;
import org.truffle.io.builtins.IOHasSizeBuiltinFactory;
import org.truffle.io.builtins.IOImportBuiltinFactory;
import org.truffle.io.builtins.IOIsExecutableBuiltinFactory;
import org.truffle.io.builtins.IOIsInstanceBuiltinFactory;
import org.truffle.io.builtins.IOIsNullBuiltinFactory;
import org.truffle.io.builtins.IOJavaTypeBuiltinFactory;
import org.truffle.io.builtins.IONanoTimeBuiltinFactory;
import org.truffle.io.builtins.IOPrintlnBuiltin;
import org.truffle.io.builtins.IOPrintlnBuiltinFactory;
import org.truffle.io.builtins.IOReadlnBuiltin;
import org.truffle.io.builtins.IOReadlnBuiltinFactory;
import org.truffle.io.builtins.IORegisterShutdownHookBuiltinFactory;
import org.truffle.io.builtins.IOStackTraceBuiltinFactory;
import org.truffle.io.builtins.IOTypeBuiltinFactory;
import org.truffle.io.builtins.IOWrapPrimitiveBuiltinFactory;
import org.truffle.io.nodes.expression.IOExpressionNode;
import org.truffle.io.nodes.root.IORootNode;
import org.truffle.io.nodes.variables.IOReadArgumentNode;
import org.truffle.io.runtime.objects.IOBigNumber;
import org.truffle.io.runtime.objects.IOBlock;
import org.truffle.io.runtime.objects.IOList;
import org.truffle.io.runtime.objects.IOMethod;
import org.truffle.io.runtime.objects.IONil;
import org.truffle.io.runtime.objects.IOObject;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.instrumentation.AllocationReporter;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * The run-time state of IO during execution. The context is created by the
 * {@link IOLanguage}. It is used, for example, by
 * {@link IOBuiltinNode#getContext() builtin functions}.
 * <p>
 * It would be an error to have two different context instances during the
 * execution of one script. However, if two separate scripts run in one Java VM
 * at the same time, they have a different context. Therefore, the context is
 * not a singleton.
 */
public final class IOState {

    private final IOLanguage language;
    @CompilationFinal
    private Env env;
    private final BufferedReader input;
    private final PrintWriter output;
    private final AllocationReporter allocationReporter;
    private final List<IOMethod> shutdownHooks = new ArrayList<>();
    
    private static final Source BUILTIN_SOURCE = Source.newBuilder(IOLanguage.ID, "", "IO builtin").build();


    private final IOObject lobbyObject;
    //private final IOObject globalObject;
   
    public IOState(IOLanguage language, TruffleLanguage.Env env,
            List<NodeFactory<? extends IOBuiltinNode>> externalBuiltins) {
        this.env = env;
        this.input = new BufferedReader(new InputStreamReader(env.in()));
        this.output = new PrintWriter(env.out(), true);
        this.language = language;
        this.allocationReporter = env.lookup(AllocationReporter.class);

        this.lobbyObject = cloneObjectPrototype();
        //this.globalObject = cloneObject();

        installBuiltins();
        for (NodeFactory<? extends IOBuiltinNode> builtin : externalBuiltins) {
            installBuiltin(builtin);
        }
    }

    /**
     * Patches the {@link IOState} to use a new {@link Env}. The method is called
     * during the
     * native image execution as a consequence of
     * {@link Context#create(java.lang.String...)}.
     *
     * @param newEnv the new {@link Env} to use.
     * @see TruffleLanguage#patchContext(Object, Env)
     */
    public void patchContext(Env newEnv) {
        this.env = newEnv;
    }

    /**
     * Return the current Truffle environment.
     */
    public Env getEnv() {
        return env;
    }

    /**
     * Returns the default input, i.e., the source for the {@link IOReadlnBuiltin}.
     * To allow unit
     * testing, we do not use {@link System#in} directly.
     */
    public BufferedReader getInput() {
        return input;
    }

    /**
     * The default default, i.e., the output for the {@link IOPrintlnBuiltin}. To
     * allow unit
     * testing, we do not use {@link System#out} directly.
     */
    public PrintWriter getOutput() {
        return output;
    }

    public IOObject getLobbyObject() {
        return lobbyObject;
    }

    /**
     * Adds all builtin methods. This method
     * lists all
     * {@link IOBuiltinNode builtin implementation classes}.
     */
    private void installBuiltins() {
        IOObjectUtil.putProperty(IOPrototypes.OBJECT, IOSymbols.OBJECT, IOPrototypes.OBJECT);
        IOObjectUtil.putProperty(IOPrototypes.OBJECT, IOSymbols.NUMBER, IOPrototypes.NUMBER);
        IOObjectUtil.putProperty(IOPrototypes.OBJECT, IOSymbols.STRING, IOPrototypes.STRING);
        IOObjectUtil.putProperty(IOPrototypes.OBJECT, IOSymbols.LIST, IOPrototypes.LIST);
        IOObjectUtil.putProperty(IOPrototypes.OBJECT, IOSymbols.METHOD, IOPrototypes.METHOD);
        IOObjectUtil.putProperty(IOPrototypes.OBJECT, IOSymbols.MESSAGE, IOPrototypes.MESSAGE);

        installBuiltin(IOReadlnBuiltinFactory.getInstance());
        installBuiltin(IOPrintlnBuiltinFactory.getInstance());
        installBuiltin(IONanoTimeBuiltinFactory.getInstance());
        installBuiltin(IODefineFunctionBuiltinFactory.getInstance());
        installBuiltin(IOStackTraceBuiltinFactory.getInstance());
        installBuiltin(IOCloneBuiltinFactory.getInstance());
        installBuiltin(IOEvalBuiltinFactory.getInstance());
        installBuiltin(IOImportBuiltinFactory.getInstance());
        installBuiltin(IOGetSizeBuiltinFactory.getInstance());
        installBuiltin(IOHasSizeBuiltinFactory.getInstance());
        installBuiltin(IOIsExecutableBuiltinFactory.getInstance());
        installBuiltin(IOIsNullBuiltinFactory.getInstance());
        installBuiltin(IOWrapPrimitiveBuiltinFactory.getInstance());
        installBuiltin(IOTypeBuiltinFactory.getInstance());
        installBuiltin(IOIsInstanceBuiltinFactory.getInstance());
        installBuiltin(IOJavaTypeBuiltinFactory.getInstance());
        installBuiltin(IOExitBuiltinFactory.getInstance());
        installBuiltin(IORegisterShutdownHookBuiltinFactory.getInstance());
        installBuiltin(IOAddToHostClassPathBuiltinFactory.getInstance());
    }
   
    public void installBuiltin(NodeFactory<? extends IOBuiltinNode> factory) {
        /*
         * The builtin node factory is a class that is automatically generated by the
         * Truffle DLL. The signature returned by the factory reflects the signature of
         * the @Specialization
         *
         * methods in the builtin classes.
         */
        int argumentCount = factory.getExecutionSignature().size();
        IOExpressionNode[] argumentNodes = new IOExpressionNode[argumentCount];
        /*
         * Builtin functions are like normal functions, i.e., the arguments are passed
         * in as an Object[] array encapsulated in IOArguments. A IOReadArgumentNode
         * extracts a parameter from this array.
         */
        for (int i = 0; i < argumentCount; i++) {
            argumentNodes[i] = new IOReadArgumentNode(i);
        }
        /* Instantiate the builtin node. This node performs the actual functionality. */
        IOBuiltinNode builtinBodyNode = factory.createNode((Object) argumentNodes);
        builtinBodyNode.addRootTag();
        /*
         * The name of the builtin function is specified via an annotation on the node
         * class.
         */
        String name = lookupNodeInfo(builtinBodyNode.getClass()).shortName();
        builtinBodyNode.setUnavailableSourceSection();

        /*
         * Wrap the builtin in a RootNode. Truffle requires all AST to start with a
         * RootNode.
         */
        IORootNode rootNode = new IORootNode(language, new FrameDescriptor(), builtinBodyNode,
                BUILTIN_SOURCE.createUnavailableSection());
        IOMethod method = createMethod(rootNode.getCallTarget(), argumentCount - 1, null);
        IOObjectUtil.putProperty(IOPrototypes.OBJECT, IOSymbols.fromJavaString(name), method);
    }


    public static NodeInfo lookupNodeInfo(Class<?> clazz) {
        if (clazz == null) {
            return null;
        }
        NodeInfo info = clazz.getAnnotation(NodeInfo.class);
        if (info != null) {
            return info;
        } else {
            return lookupNodeInfo(clazz.getSuperclass());
        }
    }

    public IOObject getPrototype(Object obj) {
        InteropLibrary interop = InteropLibrary.getFactory().getUncached();
        if (obj instanceof IOObject) {
            return ((IOObject) obj).getPrototype();
        } else if (obj instanceof String) {
            return IOPrototypes.STRING;
        } else if (obj instanceof TruffleString) {
            return IOPrototypes.STRING;
        } else if (obj instanceof IOBigNumber) {
            return IOPrototypes.NUMBER;
        } else if (interop.fitsInLong(obj)) {
            return IOPrototypes.NUMBER;
        } else if (interop.fitsInDouble(obj)) {
            return IOPrototypes.NUMBER;
        } else if (interop.isNull(obj)) {
            return IONil.SINGLETON.getPrototype();
        } else if (interop.isBoolean(obj)) {
            return IOPrototypes.BOOLEAN;
        } else {
            throw new NotImplementedException();
        }
    }

    /*
     * Methods for object creation / object property access.
     */
    public AllocationReporter getAllocationReporter() {
        return allocationReporter;
    }

    /*
     * Methods for language interoperability.
     */
    public static Object fromForeignValue(Object a) {
        if (a instanceof Long || a instanceof IOBigNumber || a instanceof String || a instanceof TruffleString
                || a instanceof Boolean) {
            return a;
        } else if (a instanceof Character) {
            return fromForeignCharacter((Character) a);
        } else if (a instanceof Number) {
            return fromForeignNumber(a);
        } else if (a instanceof TruffleObject) {
            return a;
        } else if (a instanceof IOState) {
            return a;
        }
        throw shouldNotReachHere("Value is not a truffle value.");
    }

    @TruffleBoundary
    private static long fromForeignNumber(Object a) {
        return ((Number) a).longValue();
    }

    @TruffleBoundary
    private static String fromForeignCharacter(char c) {
        return String.valueOf(c);
    }

    public CallTarget parse(Source source) {
        return env.parsePublic(source);
    }

    /**
     * Returns an object that contains bindings that were exported across all used
     * languages. To
     * read or write from this object the {@link TruffleObject interop} API can be
     * used.
     */
    public TruffleObject getPolyglotBindings() {
        return (TruffleObject) env.getPolyglotBindings();
    }

    private static final ContextReference<IOState> REFERENCE = ContextReference.create(IOLanguage.class);

    public static IOState get(Node node) {
        return REFERENCE.get(node);
    }

    /**
     * Register a function as a shutdown hook. Only no-parameter functions are
     * supported.
     *
     * @param func no-parameter function to be registered as a shutdown hook
     */
    @TruffleBoundary
    public void registerShutdownHook(IOMethod func) {
        shutdownHooks.add(func);
    }

    /**
     * Run registered shutdown hooks. This method is designed to be executed in
     * {@link TruffleLanguage#exitContext(Object, TruffleLanguage.ExitMode, int)}.
     */
    public void runShutdownHooks() {
        InteropLibrary interopLibrary = InteropLibrary.getUncached();
        for (IOMethod shutdownHook : shutdownHooks) {
            try {
                interopLibrary.execute(shutdownHook);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw shouldNotReachHere("Shutdown hook is not executable!", e);
            }
        }
    }

    /**
     * Allocate an empty object. All new objects initially have no properties.
     * Properties are added when they are first stored, i.e., the store triggers a
     * shape change of the object.
     */
    public IOObject cloneObject(IOObject prototype) {
        allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
        IOObject object = new IOObject(prototype);
        allocationReporter.onReturnValue(object, 0, AllocationReporter.SIZE_UNKNOWN);
        return object;
    }

    public IOObject cloneObjectPrototype() {
        return cloneObject(IOPrototypes.OBJECT);
    }

    public IOList createList(final Object[] data) {
        allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
        IOList list = new IOList(data);
        allocationReporter.onReturnValue(list, 0, AllocationReporter.SIZE_UNKNOWN);
        return list;
    }

    public IOMethod createMethod(RootCallTarget callTarget, int numArgs, final MaterializedFrame frame) {
        assert numArgs >= 0;
        allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
        IOMethod method = new IOMethod(callTarget, numArgs, frame);
        allocationReporter.onReturnValue(method, 0, AllocationReporter.SIZE_UNKNOWN);
        return method;
    }

    public IOBlock createBlock(final IOExpressionNode blockNode, final MaterializedFrame frame) {
        allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
        IOBlock block = new IOBlock(blockNode, frame);
        allocationReporter.onReturnValue(block, 0, AllocationReporter.SIZE_UNKNOWN);
        return block;
    }

}
