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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

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

import org.graalvm.polyglot.Context;
import org.truffle.io.IOLanguage;
import org.truffle.io.ShouldNotBeHereException;
import org.truffle.io.builtins.IOBuiltinNode;
import org.truffle.io.builtins.IOListSizeBuiltinFactory;
import org.truffle.io.builtins.IOLobbyExitBuiltinFactory;
import org.truffle.io.builtins.IOObjectCloneBuiltinFactory;
import org.truffle.io.builtins.IOObjectHasProtoBuiltinFactory;
import org.truffle.io.builtins.IOObjectIsActivatableBuiltinFactory;
import org.truffle.io.builtins.IOObjectIsNilBuiltinFactory;
import org.truffle.io.builtins.IOObjectPrintlnBuiltin;
import org.truffle.io.builtins.IOObjectPrintlnBuiltinFactory;
import org.truffle.io.builtins.IOObjectProtoBuiltinFactory;
import org.truffle.io.builtins.IOObjectSlotNamesBuiltinFactory;
import org.truffle.io.builtins.date.IODateNowBuiltinFactory;
import org.truffle.io.builtins.date.IODateSecondsSinceBuiltinFactory;
import org.truffle.io.builtins.system.IOSystemRegisterShutdownHookBuiltinFactory;
import org.truffle.io.builtins.system.IOSystemSleepBuiltinFactory;
import org.truffle.io.builtins.system.IOSystemStackTraceBuiltinFactory;
import org.truffle.io.nodes.expression.IOExpressionNode;
import org.truffle.io.nodes.root.IORootNode;
import org.truffle.io.nodes.variables.IOReadArgumentNode;
import org.truffle.io.runtime.objects.IOBlock;
import org.truffle.io.runtime.objects.IODate;
import org.truffle.io.runtime.objects.IOFunction;
import org.truffle.io.runtime.objects.IOInvokable;
import org.truffle.io.runtime.objects.IOList;
import org.truffle.io.runtime.objects.IOMethod;
import org.truffle.io.runtime.objects.IONil;
import org.truffle.io.runtime.objects.IOObject;
import org.truffle.io.runtime.objects.IOPrototype;

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
    private final List<IOInvokable> shutdownHooks = new ArrayList<>();

    private static final Source BUILTIN_SOURCE = Source.newBuilder(IOLanguage.ID, "", "IO builtin").build();

    private final IOObject lobby;
    private final IOObject protos;

    public IOState(IOLanguage language, TruffleLanguage.Env env,
            List<NodeFactory<? extends IOBuiltinNode>> externalBuiltins) {
        this.env = env;
        this.input = new BufferedReader(new InputStreamReader(env.in()));
        this.output = new PrintWriter(env.out(), true);
        this.language = language;
        this.allocationReporter = env.lookup(AllocationReporter.class);

        this.protos = cloneObject();
        this.lobby = cloneObject(protos);
        setupLobby();
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
     * The default default, i.e., the output for the {@link IOObjectPrintlnBuiltin}. To
     * allow unit
     * testing, we do not use {@link System#out} directly.
     */
    public PrintWriter getOutput() {
        return output;
    }

    public IOObject getLobby() {
        return lobby;
    }

    private void setupLobby() {
        IOPrototype.OBJECT.setPrototype(lobby);

        IOObjectUtil.putProperty(protos, IOSymbols.OBJECT, IOPrototype.OBJECT);
        IOObjectUtil.putProperty(protos, IOSymbols.NUMBER, IOPrototype.NUMBER);
        IOObjectUtil.putProperty(protos, IOSymbols.SEQUENCE, IOPrototype.SEQUENCE);
        IOObjectUtil.putProperty(protos, IOSymbols.LIST, IOPrototype.LIST);
        IOObjectUtil.putProperty(protos, IOSymbols.DATE, IOPrototype.DATE);
        IOObjectUtil.putProperty(protos, IOSymbols.SYSTEM, IOPrototype.SYSTEM);

        IOObjectUtil.putProperty(lobby, IOSymbols.LOBBY, lobby);
        IOObjectUtil.putProperty(lobby, IOSymbols.PROTOS, protos);
        installBuiltin(IOLobbyExitBuiltinFactory.getInstance(), lobby, "Lobby");
    }

    private void installBuiltins() {
        // installBuiltin(IOObjectReadlnBuiltinFactory.getInstance());
        installBuiltin(IOObjectPrintlnBuiltinFactory.getInstance());
        installBuiltin(IOObjectCloneBuiltinFactory.getInstance());
        installBuiltin(IOObjectHasProtoBuiltinFactory.getInstance());
        installBuiltin(IOObjectIsActivatableBuiltinFactory.getInstance());
        installBuiltin(IOObjectIsNilBuiltinFactory.getInstance());
        installBuiltin(IOObjectProtoBuiltinFactory.getInstance());
        installBuiltin(IOObjectSlotNamesBuiltinFactory.getInstance());
        installBuiltin(IOListSizeBuiltinFactory.getInstance(), IOPrototype.LIST, "List");
        installBuiltin(IODateSecondsSinceBuiltinFactory.getInstance(), IOPrototype.DATE, "Date");
        installBuiltin(IODateNowBuiltinFactory.getInstance(), IOPrototype.DATE, "Date");
        installBuiltin(IOSystemSleepBuiltinFactory.getInstance(), IOPrototype.SYSTEM, "System");
        installBuiltin(IOSystemStackTraceBuiltinFactory.getInstance(), IOPrototype.SYSTEM, "System");
        installBuiltin(IOSystemRegisterShutdownHookBuiltinFactory.getInstance(), IOPrototype.SYSTEM, "System");
    }

    public void installBuiltin(NodeFactory<? extends IOBuiltinNode> factory) {
        installBuiltin(factory, IOPrototype.OBJECT, "Object");
    }

    public void installBuiltin(NodeFactory<? extends IOBuiltinNode> factory, final IOObject target,
            final String targetName) {
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
        String functionName = targetName + "_" + name;
        IOFunction function = createFunction(rootNode.getCallTarget(), IOSymbols.fromJavaString(functionName));
        IOObjectUtil.putProperty(target, IOSymbols.fromJavaString(name), function);
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
            return IOPrototype.SEQUENCE;
        } else if (obj instanceof TruffleString) {
            return IOPrototype.SEQUENCE;
        } else if (interop.fitsInLong(obj)) {
            return IOPrototype.NUMBER;
        } else if (interop.fitsInDouble(obj)) {
            return IOPrototype.NUMBER;
        } else if (interop.isNull(obj)) {
            return IONil.SINGLETON.getPrototype();
        } else {
            return IOPrototype.OBJECT;
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
        if (a instanceof Long || a instanceof Double || a instanceof String || a instanceof TruffleString
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
        throw new ShouldNotBeHereException("Value is not a truffle value.");
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
    public void registerShutdownHook(IOInvokable func) {
        shutdownHooks.add(func);
    }

    /**
     * Run registered shutdown hooks. This method is designed to be executed in
     * {@link TruffleLanguage#exitContext(Object, TruffleLanguage.ExitMode, int)}.
     */
    public void runShutdownHooks() {
        InteropLibrary interopLibrary = InteropLibrary.getUncached();
        for (IOInvokable shutdownHook : shutdownHooks) {
            try {
                interopLibrary.execute(shutdownHook);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw new ShouldNotBeHereException("Shutdown hook is not executable!", e);
            }
        }
    }

    public IOObject cloneObject() {
        return cloneObject(IOPrototype.OBJECT);
    }

    public IOObject cloneObject(IOObject prototype) {
        allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
        IOObject object = new IOObject(prototype);
        allocationReporter.onReturnValue(object, 0, AllocationReporter.SIZE_UNKNOWN);
        return object;
    }

    public IOList createList(final Object[] data) {
        allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
        IOList list = new IOList(data);
        allocationReporter.onReturnValue(list, 0, AllocationReporter.SIZE_UNKNOWN);
        return list;
    }

    public IODate createDate() {
        allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
        IODate date = new IODate();
        allocationReporter.onReturnValue(date, 0, AllocationReporter.SIZE_UNKNOWN);
        return date;
    }

    public IOMethod createMethod(RootCallTarget callTarget, final TruffleString[] parameters,
            final MaterializedFrame frame) {
        allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
        IOMethod method = new IOMethod(callTarget, parameters, frame);
        allocationReporter.onReturnValue(method, 0, AllocationReporter.SIZE_UNKNOWN);
        return method;
    }

    public IOFunction createFunction(RootCallTarget callTarget, final TruffleString name) {
        allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
        IOFunction function = new IOFunction(callTarget, name);
        allocationReporter.onReturnValue(function, 0, AllocationReporter.SIZE_UNKNOWN);
        return function;
    }

    public IOBlock createBlock(final IOExpressionNode blockNode, final MaterializedFrame frame) {
        allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
        IOBlock block = new IOBlock(blockNode, frame);
        allocationReporter.onReturnValue(block, 0, AllocationReporter.SIZE_UNKNOWN);
        return block;
    }

}
