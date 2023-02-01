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
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.truffle.io.IoLanguage;
import org.truffle.io.ShouldNotBeHereException;
import org.truffle.io.nodes.IoNode;
import org.truffle.io.nodes.expression.FunctionBodyNode;
import org.truffle.io.nodes.functions.date.DateNowFunctionFactory;
import org.truffle.io.nodes.functions.date.DateSecondsSinceFunctionFactory;
import org.truffle.io.nodes.functions.exception.ExceptionRaiseFunctionFactory;
import org.truffle.io.nodes.functions.list.ListAtFunctionFactory;
import org.truffle.io.nodes.functions.list.ListAtPutFunctionFactory;
import org.truffle.io.nodes.functions.list.ListSizeFunctionFactory;
import org.truffle.io.nodes.functions.lobby.LobbyExitFunctionFactory;
import org.truffle.io.nodes.functions.number.NumberFloorFunctionFactory;
import org.truffle.io.nodes.functions.object.ObjectCloneFunctionFactory;
import org.truffle.io.nodes.functions.object.ObjectDoFileFunctionFactory;
import org.truffle.io.nodes.functions.object.ObjectDoStringFunctionFactory;
import org.truffle.io.nodes.functions.object.ObjectGetSlotFunctionFactory;
import org.truffle.io.nodes.functions.object.ObjectHasProtoFunctionFactory;
import org.truffle.io.nodes.functions.object.ObjectIsActivatableFunctionFactory;
import org.truffle.io.nodes.functions.object.ObjectIsNilFunctionFactory;
import org.truffle.io.nodes.functions.object.ObjectPrintFunctionFactory;
import org.truffle.io.nodes.functions.object.ObjectPrintlnFunction;
import org.truffle.io.nodes.functions.object.ObjectPrintlnFunctionFactory;
import org.truffle.io.nodes.functions.object.ObjectProtoFunctionFactory;
import org.truffle.io.nodes.functions.object.ObjectSlotNamesFunctionFactory;
import org.truffle.io.nodes.functions.object.ObjectThisContextFunctionFactory;
import org.truffle.io.nodes.functions.sequence.SequenceAtFunctionFactory;
import org.truffle.io.nodes.functions.sequence.SequenceAtPutFunctionFactory;
import org.truffle.io.nodes.functions.system.SystemRegisterShutdownHookFunctionFactory;
import org.truffle.io.nodes.functions.system.SystemSleepFunctionFactory;
import org.truffle.io.nodes.functions.system.SystemStackTraceFunctionFactory;
import org.truffle.io.nodes.root.IoRootNode;
import org.truffle.io.nodes.slots.ReadArgumentNode;
import org.truffle.io.runtime.IoOptions.IoContextOptions;
import org.truffle.io.runtime.objects.IoBlock;
import org.truffle.io.runtime.objects.IoCall;
import org.truffle.io.runtime.objects.IoCoroutine;
import org.truffle.io.runtime.objects.IoDate;
import org.truffle.io.runtime.objects.IoException;
import org.truffle.io.runtime.objects.IoFalse;
import org.truffle.io.runtime.objects.IoFunction;
import org.truffle.io.runtime.objects.IoInvokable;
import org.truffle.io.runtime.objects.IoList;
import org.truffle.io.runtime.objects.IoLocals;
import org.truffle.io.runtime.objects.IoMessage;
import org.truffle.io.runtime.objects.IoMethod;
import org.truffle.io.runtime.objects.IoNil;
import org.truffle.io.runtime.objects.IoObject;
import org.truffle.io.runtime.objects.IoPrototype;
import org.truffle.io.runtime.objects.IoTrue;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.instrumentation.AllocationReporter;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;

public final class IoState {
    private static final TruffleLogger LOGGER = IoLanguage.getLogger(IoState.class);

    private final IoLanguage language;
    @CompilationFinal
    private Env env;
    private final BufferedReader input;
    private final PrintWriter output;
    private final AllocationReporter allocationReporter;
    private final List<IoInvokable> shutdownHooks = new ArrayList<>();
    public final IoContextOptions options;

    private static final Source BUILTIN_SOURCE = Source.newBuilder(IoLanguage.ID, "", "IO builtin").build();
    private static final String SOURCE_SUFFIX = ".io";
    private static final String LF = System.getProperty("line.separator");

    private final IoObject lobby;
    private final IoObject protos;
    private final IoCoroutine currentCoroutine;

    public IoState(IoLanguage language, TruffleLanguage.Env env,
            List<NodeFactory<? extends FunctionBodyNode>> externalBuiltins) {
        this.env = env;
        this.input = new BufferedReader(new InputStreamReader(env.in()));
        this.output = new PrintWriter(env.out(), true);
        this.language = language;
        this.allocationReporter = env.lookup(AllocationReporter.class);

        this.protos = cloneObject();
        this.lobby = cloneObject(protos);
        this.currentCoroutine = createCoroutine();
        this.options = new IoContextOptions(env);
        setupLobby();
        installBuiltins();
        for (NodeFactory<? extends FunctionBodyNode> builtin : externalBuiltins) {
            installBuiltin(builtin);
        }
    }

    /**
     * Patches the {@link IoState} to use a new {@link Env}. The method is called
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
     * The default default, i.e., the output for the {@link ObjectPrintlnFunction}. To
     * allow unit
     * testing, we do not use {@link System#out} directly.
     */
    public PrintWriter getOutput() {
        return output;
    }

    public IoObject getLobby() {
        return lobby;
    }

    public IoCoroutine getCurrentCoroutine() {
        return currentCoroutine;
    }

    private void setupLobby() {
        IoPrototype.OBJECT.setPrototype(lobby);

        DynamicObjectLibrary lib = DynamicObjectLibrary.getUncached();
        IoObjectUtil.put(lib, protos, Symbols.OBJECT, IoPrototype.OBJECT);
        IoObjectUtil.put(lib, protos, Symbols.NUMBER, IoPrototype.NUMBER);
        IoObjectUtil.put(lib, protos, Symbols.SEQUENCE, IoPrototype.SEQUENCE);
        IoObjectUtil.put(lib, protos, Symbols.LIST, IoPrototype.LIST);
        IoObjectUtil.put(lib, protos, Symbols.DATE, IoPrototype.DATE);
        IoObjectUtil.put(lib, protos, Symbols.SYSTEM, IoPrototype.SYSTEM);
        IoObjectUtil.put(lib, protos, Symbols.CALL, IoPrototype.CALL);
        IoObjectUtil.put(lib, protos, Symbols.MESSAGE, IoPrototype.MESSAGE);
        IoObjectUtil.put(lib, protos, Symbols.BLOCK, IoPrototype.BLOCK);
        IoObjectUtil.put(lib, protos, Symbols.COROUTINE, IoPrototype.COROUTINE);
        IoObjectUtil.put(lib, protos, Symbols.EXCEPTION, IoPrototype.EXCEPTION);

        IoObjectUtil.put(lib, protos, Symbols.NIL, IoNil.SINGLETON);
        IoObjectUtil.put(lib, protos, Symbols.TRUE, IoTrue.SINGLETON);
        IoObjectUtil.put(lib, protos, Symbols.FALSE, IoFalse.SINGLETON);

        IoObjectUtil.put(lib, lobby, Symbols.LOBBY, lobby);
        IoObjectUtil.put(lib, lobby, Symbols.PROTOS, protos);

        IoObjectUtil.put(lib, lobby, Symbols.PROTOS, protos);
    }

    private void installBuiltins() {
        installBuiltin(ObjectCloneFunctionFactory.getInstance());
        installBuiltin(ObjectDoFileFunctionFactory.getInstance());
        installBuiltin(ObjectDoStringFunctionFactory.getInstance());
        installBuiltin(ObjectGetSlotFunctionFactory.getInstance());
        installBuiltin(ObjectHasProtoFunctionFactory.getInstance());
        installBuiltin(ObjectIsActivatableFunctionFactory.getInstance());
        installBuiltin(ObjectIsNilFunctionFactory.getInstance());
        installBuiltin(ObjectPrintFunctionFactory.getInstance());
        installBuiltin(ObjectPrintlnFunctionFactory.getInstance());
        installBuiltin(ObjectProtoFunctionFactory.getInstance());
        // installBuiltin(ObjectReadlnFunctionFactory.getInstance());
        installBuiltin(ObjectSlotNamesFunctionFactory.getInstance());
        installBuiltin(ObjectThisContextFunctionFactory.getInstance());
        installBuiltin(ListSizeFunctionFactory.getInstance(), IoPrototype.LIST, "List");
        installBuiltin(ListAtFunctionFactory.getInstance(), IoPrototype.LIST, "List");
        installBuiltin(ListAtPutFunctionFactory.getInstance(), IoPrototype.LIST, "List");
        installBuiltin(SequenceAtFunctionFactory.getInstance(), IoPrototype.SEQUENCE, "Sequence");
        installBuiltin(SequenceAtPutFunctionFactory.getInstance(), IoPrototype.SEQUENCE, "Sequence");
        installBuiltin(DateSecondsSinceFunctionFactory.getInstance(), IoPrototype.DATE, "Date");
        installBuiltin(DateNowFunctionFactory.getInstance(), IoPrototype.DATE, "Date");
        installBuiltin(NumberFloorFunctionFactory.getInstance(), IoPrototype.NUMBER, "Number");
        installBuiltin(SystemSleepFunctionFactory.getInstance(), IoPrototype.SYSTEM, "System");
        installBuiltin(SystemStackTraceFunctionFactory.getInstance(), IoPrototype.SYSTEM, "System");
        installBuiltin(SystemRegisterShutdownHookFunctionFactory.getInstance(), IoPrototype.SYSTEM, "System");
        installBuiltin(LobbyExitFunctionFactory.getInstance(), lobby, "Lobby");
        installBuiltin(ExceptionRaiseFunctionFactory.getInstance(), IoPrototype.EXCEPTION, "Exception");
    }

    public void installBuiltin(NodeFactory<? extends FunctionBodyNode> factory) {
        installBuiltin(factory, IoPrototype.OBJECT, "Object");
    }

    public void installBuiltin(NodeFactory<? extends FunctionBodyNode> factory, final IoObject target,
            final String targetName) {
        /*
         * The builtin node factory is a class that is automatically generated by the
         * Truffle DLL. The signature returned by the factory reflects the signature of
         * the @Specialization
         *
         * methods in the builtin classes.
         */
        int argumentCount = factory.getExecutionSignature().size();
        IoNode[] argumentNodes = new IoNode[argumentCount];
        /*
         * Builtin functions are like normal functions, i.e., the arguments are passed
         * in as an Object[] array encapsulated in IOArguments. A IOReadArgumentNode
         * extracts a parameter from this array.
         */
        for (int i = 0; i < argumentCount; i++) {
            argumentNodes[i] = new ReadArgumentNode(i);
        }
        /* Instantiate the builtin node. This node performs the actual functionality. */
        FunctionBodyNode builtinBodyNode = factory.createNode((Object) argumentNodes);
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
        IoRootNode rootNode = new IoRootNode(language, new FrameDescriptor(), builtinBodyNode,
                BUILTIN_SOURCE.createUnavailableSection());
        String functionName = targetName + "_" + name;
        IoFunction function = createFunction(rootNode.getCallTarget(), Symbols.fromJavaString(functionName));
        IoObjectUtil.putUncached(target, Symbols.fromJavaString(name), function);
    }

    public void initialize() {
        Path rootPath = null;
        for (String path : options.ioLibPath) {
            Path candidate = FileSystems.getDefault().getPath(path, "bootstrap");
            if (Files.exists(candidate)) {
                rootPath = candidate;
                break;
            }
        }

        if (rootPath != null) {
            try {
                Files.list(rootPath).filter(s -> s.toString().endsWith(SOURCE_SUFFIX))
                        .sorted(Comparator.comparing(s -> s.toString()))
                        .forEach(file -> loadBootstrapFile(file));
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    protected void loadBootstrapFile(final Path sourceFile) {
        String sourceName = sourceFile.toAbsolutePath().toString();
        LOGGER.fine("Bootstrap load file: " + sourceName);
        try {
            String sourceCode = readAllLines(sourceName);
            Source src = Source.newBuilder(IoLanguage.ID, sourceCode, "<bootstrap>")
                    .internal(true).build();
            CallTarget callTarget = parse(src);
            DirectCallNode callNode = DirectCallNode.create(callTarget);
            callNode.call(getLobby());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static String readAllLines(String fileName) throws IOException {
        StringBuilder outFile = new StringBuilder();
        for (String line : Files.readAllLines(Paths.get(fileName), Charset.defaultCharset())) {
            outFile.append(line).append(LF);
        }
        return outFile.toString();
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

    public AllocationReporter getAllocationReporter() {
        return allocationReporter;
    }

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
        } else if (a instanceof IoState) {
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

    private static final ContextReference<IoState> REFERENCE = ContextReference.create(IoLanguage.class);

    public static IoState get(Node node) {
        return REFERENCE.get(node);
    }

    /**
     * Register a function as a shutdown hook. Only no-parameter functions are
     * supported.
     *
     * @param func no-parameter function to be registered as a shutdown hook
     */
    @TruffleBoundary
    public void registerShutdownHook(IoInvokable func) {
        shutdownHooks.add(func);
    }

    /**
     * Run registered shutdown hooks. This method is designed to be executed in
     * {@link TruffleLanguage#exitContext(Object, TruffleLanguage.ExitMode, int)}.
     */
    public void runShutdownHooks() {
        InteropLibrary interopLibrary = InteropLibrary.getUncached();
        for (IoInvokable shutdownHook : shutdownHooks) {
            try {
                interopLibrary.execute(shutdownHook);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw new ShouldNotBeHereException("Shutdown hook is not executable!", e);
            }
        }
    }

    public IoObject cloneObject() {
        return cloneObject(IoPrototype.OBJECT);
    }

    public IoObject cloneObject(IoObject prototype) {
        allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
        IoObject object = new IoObject(prototype);
        allocationReporter.onReturnValue(object, 0, AllocationReporter.SIZE_UNKNOWN);
        return object;
    }

    public IoList createList(final Object[] data) {
        allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
        IoList list = new IoList(data);
        allocationReporter.onReturnValue(list, 0, AllocationReporter.SIZE_UNKNOWN);
        return list;
    }

    public IoDate createDate() {
        allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
        IoDate date = new IoDate();
        allocationReporter.onReturnValue(date, 0, AllocationReporter.SIZE_UNKNOWN);
        return date;
    }

    public IoBlock createBlock(RootCallTarget callTarget, final TruffleString[] argNames, final IoLocals locals) {
        allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
        IoBlock block = new IoBlock(callTarget, argNames, locals);
        allocationReporter.onReturnValue(block, 0, AllocationReporter.SIZE_UNKNOWN);
        return block;
    }

    public IoMethod createMethod(RootCallTarget callTarget, final TruffleString[] argNames) {
        allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
        IoMethod method = new IoMethod(callTarget, argNames);
        allocationReporter.onReturnValue(method, 0, AllocationReporter.SIZE_UNKNOWN);
        return method;
    }

    public IoFunction createFunction(RootCallTarget callTarget, final TruffleString name) {
        allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
        IoFunction function = new IoFunction(callTarget, name);
        allocationReporter.onReturnValue(function, 0, AllocationReporter.SIZE_UNKNOWN);
        return function;
    }

    public IoMessage createMessage(final TruffleString name, final IoNode[] argumentNodes) {
        allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
        IoMessage message = new IoMessage(name, argumentNodes);
        allocationReporter.onReturnValue(message, 0, AllocationReporter.SIZE_UNKNOWN);
        return message;
    }

    public IoLocals createLocals(final Object obj, final MaterializedFrame frame) {
        if (obj instanceof IoObject) {
            return createLocals((IoObject) obj, frame);
        }
        return createLocals(IoObjectUtil.getPrototype(obj), frame);
    }

    public IoLocals createLocals(final IoObject prototype, final MaterializedFrame frame) {
        allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
        IoLocals locals = new IoLocals(prototype, frame);
        allocationReporter.onReturnValue(locals, 0, AllocationReporter.SIZE_UNKNOWN);
        return locals;
    }

    public IoCall createCall(final IoLocals sender, final Object target, final IoMessage message,
            final IoObject slotContext, final IoInvokable activated, final IoCoroutine coroutine) {
        allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
        IoCall call = new IoCall(sender, target, message, slotContext, activated, coroutine);
        allocationReporter.onReturnValue(call, 0, AllocationReporter.SIZE_UNKNOWN);
        return call;
    }

    public IoException createException(final TruffleString error, final IoCoroutine coroutine,
            final IoMessage caughtMessage) {
        allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
        IoException exception = new IoException(error, coroutine, caughtMessage);
        allocationReporter.onReturnValue(exception, 0, AllocationReporter.SIZE_UNKNOWN);
        return exception;
    }

    public IoException createException(final TruffleString error, final IoCoroutine coroutine) {
        allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
        IoException exception = new IoException(error, coroutine);
        allocationReporter.onReturnValue(exception, 0, AllocationReporter.SIZE_UNKNOWN);
        return exception;
    }

    public IoCoroutine createCoroutine() {
        allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
        IoCoroutine coroutine = new IoCoroutine();
        allocationReporter.onReturnValue(coroutine, 0, AllocationReporter.SIZE_UNKNOWN);
        return coroutine;
    }
}
