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
package org.iolanguage.runtime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;

import org.graalvm.polyglot.Context;
import org.iolanguage.IoLanguage;
import org.iolanguage.ShouldNotBeHereException;
import org.iolanguage.nodes.IoNode;
import org.iolanguage.nodes.functions.FunctionBodyNode;
import org.iolanguage.nodes.functions.block.BlockPassStopsFunctionFactory;
import org.iolanguage.nodes.functions.block.BlockSetPassStopsFunctionFactory;
import org.iolanguage.nodes.functions.date.DateNowFunctionFactory;
import org.iolanguage.nodes.functions.date.DateSecondsSinceFunctionFactory;
import org.iolanguage.nodes.functions.exception.ExceptionRaiseFunctionFactory;
import org.iolanguage.nodes.functions.list.ListAppendFunctionFactory;
import org.iolanguage.nodes.functions.list.ListAtFunctionFactory;
import org.iolanguage.nodes.functions.list.ListAtPutFunctionFactory;
import org.iolanguage.nodes.functions.list.ListSizeFunctionFactory;
import org.iolanguage.nodes.functions.lobby.LobbyExitFunctionFactory;
import org.iolanguage.nodes.functions.map.MapAtFunctionFactory;
import org.iolanguage.nodes.functions.map.MapAtPutFunctionFactory;
import org.iolanguage.nodes.functions.number.NumberAddFunctionFactory;
import org.iolanguage.nodes.functions.number.NumberAsLowercaseFunctionFactory;
import org.iolanguage.nodes.functions.number.NumberAsUppercaseFunctionFactory;
import org.iolanguage.nodes.functions.number.NumberFloorFunctionFactory;
import org.iolanguage.nodes.functions.object.ObjectCloneFunctionFactory;
import org.iolanguage.nodes.functions.object.ObjectDoFileFunctionFactory;
import org.iolanguage.nodes.functions.object.ObjectDoStringFunctionFactory;
import org.iolanguage.nodes.functions.object.ObjectGetSlotFunctionFactory;
import org.iolanguage.nodes.functions.object.ObjectHasProtoFunctionFactory;
import org.iolanguage.nodes.functions.object.ObjectIsActivatableFunctionFactory;
import org.iolanguage.nodes.functions.object.ObjectIsNilFunctionFactory;
import org.iolanguage.nodes.functions.object.ObjectPrintFunctionFactory;
import org.iolanguage.nodes.functions.object.ObjectPrintlnFunction;
import org.iolanguage.nodes.functions.object.ObjectPrintlnFunctionFactory;
import org.iolanguage.nodes.functions.object.ObjectProtoFunctionFactory;
import org.iolanguage.nodes.functions.object.ObjectRemoveSlotFunctionFactory;
import org.iolanguage.nodes.functions.object.ObjectSlotNamesFunctionFactory;
import org.iolanguage.nodes.functions.object.ObjectThisContextFunctionFactory;
import org.iolanguage.nodes.functions.object.ObjectWriteFunctionFactory;
import org.iolanguage.nodes.functions.object.ObjectWritelnFunctionFactory;
import org.iolanguage.nodes.functions.sequence.SequenceAppendSeqFunctionFactory;
import org.iolanguage.nodes.functions.sequence.SequenceAtFunctionFactory;
import org.iolanguage.nodes.functions.sequence.SequenceAtPutFunctionFactory;
import org.iolanguage.nodes.functions.sequence.SequenceEncodingFunctionFactory;
import org.iolanguage.nodes.functions.sequence.SequenceItemTypeFunctionFactory;
import org.iolanguage.nodes.functions.sequence.SequenceSetEncodingFunctionFactory;
import org.iolanguage.nodes.functions.sequence.SequenceSetItemTypeFunctionFactory;
import org.iolanguage.nodes.functions.sequence.SequenceSetSizeFunctionFactory;
import org.iolanguage.nodes.functions.sequence.SequenceSizeFunctionFactory;
import org.iolanguage.nodes.functions.sequence.SequenceSplitFunctionFactory;
import org.iolanguage.nodes.functions.system.SystemRegisterShutdownHookFunctionFactory;
import org.iolanguage.nodes.functions.system.SystemSleepFunctionFactory;
import org.iolanguage.nodes.functions.system.SystemStackTraceFunctionFactory;
import org.iolanguage.nodes.root.IoRootNode;
import org.iolanguage.nodes.slots.ReadArgumentNode;
import org.iolanguage.runtime.IoOptions.IoStateOptions;
import org.iolanguage.runtime.objects.IoBaseObject;
import org.iolanguage.runtime.objects.IoBigInteger;
import org.iolanguage.runtime.objects.IoBlock;
import org.iolanguage.runtime.objects.IoCall;
import org.iolanguage.runtime.objects.IoCoroutine;
import org.iolanguage.runtime.objects.IoDate;
import org.iolanguage.runtime.objects.IoException;
import org.iolanguage.runtime.objects.IoFalse;
import org.iolanguage.runtime.objects.IoFunction;
import org.iolanguage.runtime.objects.IoInvokable;
import org.iolanguage.runtime.objects.IoList;
import org.iolanguage.runtime.objects.IoLocals;
import org.iolanguage.runtime.objects.IoMap;
import org.iolanguage.runtime.objects.IoMessage;
import org.iolanguage.runtime.objects.IoMethod;
import org.iolanguage.runtime.objects.IoNil;
import org.iolanguage.runtime.objects.IoObject;
import org.iolanguage.runtime.objects.IoPrototype;
import org.iolanguage.runtime.objects.IoSequence;
import org.iolanguage.runtime.objects.IoTrue;

public final class IoState {
    private static final TruffleLogger LOGGER = IoLanguage.getLogger(IoState.class);

    private final IoLanguage language;
    @CompilationFinal
    private Env env;
    private final BufferedReader input;
    private final PrintWriter output;
    private final AllocationReporter allocationReporter;
    private final List<IoInvokable> shutdownHooks = new ArrayList<>();
    public final IoStateOptions options;

    private static final Source BUILTIN_SOURCE = Source.newBuilder(IoLanguage.ID, "", "IO builtin").build();
    private static final String SOURCE_SUFFIX = ".io";
    private static final String LF = System.getProperty("line.separator");

    private final IoBaseObject lobby;
    private final IoBaseObject coreProtos;
    private final IoCoroutine currentCoroutine;

    public IoState(IoLanguage language, TruffleLanguage.Env env,
            List<NodeFactory<? extends FunctionBodyNode>> externalBuiltins) {
        this.env = env;
        this.input = new BufferedReader(new InputStreamReader(env.in()));
        this.output = new PrintWriter(env.out(), true);
        this.language = language;
        this.allocationReporter = env.lookup(AllocationReporter.class);

        this.coreProtos = cloneObject();
        this.lobby = cloneObject(coreProtos);
        this.currentCoroutine = createCoroutine();
        this.options = new IoStateOptions(env);
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

    public IoBaseObject getLobby() {
        return lobby;
    }

    public IoStateOptions getStateOptions() {
        return options;
    }

    public IoCoroutine getCurrentCoroutine() {
        return currentCoroutine;
    }

    private void setupLobby() {
        IoPrototype.OBJECT.setPrototype(lobby);

        IoObjectUtil.put(coreProtos, Symbols.OBJECT, IoPrototype.OBJECT);
        IoObjectUtil.put(coreProtos, Symbols.NUMBER, IoPrototype.NUMBER);
        IoObjectUtil.put(coreProtos, Symbols.SEQUENCE, IoPrototype.SEQUENCE);
        IoObjectUtil.put(coreProtos, Symbols.LIST, IoPrototype.LIST);
        IoObjectUtil.put(coreProtos, Symbols.DATE, IoPrototype.DATE);
        IoObjectUtil.put(coreProtos, Symbols.SYSTEM, IoPrototype.SYSTEM);
        IoObjectUtil.put(coreProtos, Symbols.CALL, IoPrototype.CALL);
        IoObjectUtil.put(coreProtos, Symbols.MESSAGE, IoPrototype.MESSAGE);
        IoObjectUtil.put(coreProtos, Symbols.BLOCK, IoPrototype.BLOCK);
        IoObjectUtil.put(coreProtos, Symbols.COROUTINE, IoPrototype.COROUTINE);
        IoObjectUtil.put(coreProtos, Symbols.EXCEPTION, IoPrototype.EXCEPTION);
        IoObjectUtil.put(coreProtos, Symbols.MAP, IoPrototype.MAP);

        IoObjectUtil.put(coreProtos, Symbols.NIL, IoNil.SINGLETON);
        IoObjectUtil.put(coreProtos, Symbols.TRUE, IoTrue.SINGLETON);
        IoObjectUtil.put(coreProtos, Symbols.FALSE, IoFalse.SINGLETON);

        IoObjectUtil.put(lobby, Symbols.LOBBY, lobby);
        IoBaseObject protos = cloneObject();
        IoObjectUtil.put(protos, Symbols.constant("Core"), coreProtos);
        IoObjectUtil.put(protos, Symbols.constant("Addons"), cloneObject());
        IoObjectUtil.put(lobby, Symbols.constant("Protos"), protos);
    }

    private void installBuiltins() {
        installBuiltin(ObjectCloneFunctionFactory.getInstance());
        installBuiltin(ObjectDoFileFunctionFactory.getInstance());
        installBuiltin(ObjectDoStringFunctionFactory.getInstance());
        installBuiltin(ObjectGetSlotFunctionFactory.getInstance());
        installBuiltin(ObjectRemoveSlotFunctionFactory.getInstance());
        installBuiltin(ObjectHasProtoFunctionFactory.getInstance());
        installBuiltin(ObjectIsActivatableFunctionFactory.getInstance());
        installBuiltin(ObjectIsNilFunctionFactory.getInstance());
        installBuiltin(ObjectPrintFunctionFactory.getInstance());
        installBuiltin(ObjectPrintlnFunctionFactory.getInstance());
        installBuiltin(ObjectProtoFunctionFactory.getInstance());
        // installBuiltin(ObjectReadlnFunctionFactory.getInstance());
        installBuiltin(ObjectSlotNamesFunctionFactory.getInstance());
        installBuiltin(ObjectThisContextFunctionFactory.getInstance());
        installBuiltin(ObjectWriteFunctionFactory.getInstance());
        installBuiltin(ObjectWritelnFunctionFactory.getInstance());
        installBuiltin(ListSizeFunctionFactory.getInstance(), IoPrototype.LIST, "List");
        installBuiltin(ListAppendFunctionFactory.getInstance(), IoPrototype.LIST, "List");
        installBuiltin(ListAtFunctionFactory.getInstance(), IoPrototype.LIST, "List");
        installBuiltin(ListAtPutFunctionFactory.getInstance(), IoPrototype.LIST, "List");
        installBuiltin(SequenceAppendSeqFunctionFactory.getInstance(), IoPrototype.SEQUENCE, "Sequence");
        installBuiltin(SequenceAtFunctionFactory.getInstance(), IoPrototype.SEQUENCE, "Sequence");
        installBuiltin(SequenceAtPutFunctionFactory.getInstance(), IoPrototype.SEQUENCE, "Sequence");
        installBuiltin(SequenceEncodingFunctionFactory.getInstance(), IoPrototype.SEQUENCE, "Sequence");
        installBuiltin(SequenceSetEncodingFunctionFactory.getInstance(), IoPrototype.SEQUENCE, "Sequence");
        installBuiltin(SequenceItemTypeFunctionFactory.getInstance(), IoPrototype.SEQUENCE, "Sequence");
        installBuiltin(SequenceSetItemTypeFunctionFactory.getInstance(), IoPrototype.SEQUENCE, "Sequence");
        installBuiltin(SequenceSizeFunctionFactory.getInstance(), IoPrototype.SEQUENCE, "Sequence");
        installBuiltin(SequenceSetSizeFunctionFactory.getInstance(), IoPrototype.SEQUENCE, "Sequence");
        installBuiltin(SequenceSplitFunctionFactory.getInstance(), IoPrototype.SEQUENCE, "Sequence");
        installBuiltin(DateSecondsSinceFunctionFactory.getInstance(), IoPrototype.DATE, "Date");
        installBuiltin(DateNowFunctionFactory.getInstance(), IoPrototype.DATE, "Date");
        installBuiltin(NumberAsLowercaseFunctionFactory.getInstance(), IoPrototype.NUMBER, "Number");
        installBuiltin(NumberAsUppercaseFunctionFactory.getInstance(), IoPrototype.NUMBER, "Number");
        installBuiltin(NumberFloorFunctionFactory.getInstance(), IoPrototype.NUMBER, "Number");
        installBuiltin(NumberAddFunctionFactory.getInstance(), IoPrototype.NUMBER, "Number");
        installBuiltin(SystemSleepFunctionFactory.getInstance(), IoPrototype.SYSTEM, "System");
        installBuiltin(SystemStackTraceFunctionFactory.getInstance(), IoPrototype.SYSTEM, "System");
        installBuiltin(SystemRegisterShutdownHookFunctionFactory.getInstance(), IoPrototype.SYSTEM, "System");
        installBuiltin(LobbyExitFunctionFactory.getInstance(), lobby, "Lobby");
        installBuiltin(ExceptionRaiseFunctionFactory.getInstance(), IoPrototype.EXCEPTION, "Exception");
        installBuiltin(BlockPassStopsFunctionFactory.getInstance(), IoPrototype.BLOCK, "Block");
        installBuiltin(BlockSetPassStopsFunctionFactory.getInstance(), IoPrototype.BLOCK, "Block");
        installBuiltin(MapAtFunctionFactory.getInstance(), IoPrototype.MAP, "Map");
        installBuiltin(MapAtPutFunctionFactory.getInstance(), IoPrototype.MAP, "Map");
    }

    public void installBuiltin(NodeFactory<? extends FunctionBodyNode> factory) {
        installBuiltin(factory, IoPrototype.OBJECT, "Object");
    }

    public void installBuiltin(NodeFactory<? extends FunctionBodyNode> factory, final IoBaseObject target,
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
        IoObjectUtil.put(target, Symbols.fromJavaString(name), function);
    }

    public void initialize() {
        Path rootPath = null;
        for (String path : options.libPath) {
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
                IoCall call = createCall(null, getLobby(), null, null, shutdownHook, getCurrentCoroutine());
                Object[] arguments = new Object[1];
                arguments[0] = call;
                interopLibrary.execute(shutdownHook, arguments);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw new ShouldNotBeHereException("Shutdown hook is not executable!", e);
            }
        }
    }

    public IoBaseObject cloneObject() {
        return cloneObject(IoPrototype.OBJECT);
    }

    public IoBaseObject cloneObject(IoBaseObject prototype) {
        allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
        IoBaseObject object = new IoObject(prototype);
        allocationReporter.onReturnValue(object, 0, AllocationReporter.SIZE_UNKNOWN);
        return object;
    }

    public IoList createList(final String[] strings) {
        TruffleString[] stringsTS = new TruffleString[strings.length];
        for(int i = 0; i < strings.length; i++) {
            stringsTS[i] = Symbols.constant(strings[i]);
        }
        return createList(stringsTS);
    }

    public IoList createList(final Object[] data) {
        allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
        IoList list = new IoList(data);
        allocationReporter.onReturnValue(list, 0, AllocationReporter.SIZE_UNKNOWN);
        return list;
    }

    public IoBigInteger createBigInteger(long value) {
        allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
        IoBigInteger biginteger = new IoBigInteger(value);
        allocationReporter.onReturnValue(biginteger, 0, AllocationReporter.SIZE_UNKNOWN);
        return biginteger;
    }

    public IoBigInteger createBigInteger(BigInteger value) {
        allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
        IoBigInteger biginteger = new IoBigInteger(value);
        allocationReporter.onReturnValue(biginteger, 0, AllocationReporter.SIZE_UNKNOWN);
        return biginteger;
    }


    public IoDate createDate() {
        allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
        IoDate date = new IoDate();
        allocationReporter.onReturnValue(date, 0, AllocationReporter.SIZE_UNKNOWN);
        return date;
    }

    public IoSequence createSequence() {
        allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
        IoSequence sequence = new IoSequence();
        allocationReporter.onReturnValue(sequence, 0, AllocationReporter.SIZE_UNKNOWN);
        return sequence;
    }

    public IoMap createMap() {
        allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
        IoMap map = new IoMap();
        allocationReporter.onReturnValue(map, 0, AllocationReporter.SIZE_UNKNOWN);
        return map;
    }

    public IoBlock createBlock(RootCallTarget callTarget, final TruffleString[] argNames, final boolean callSlotIsUsed,
            final IoLocals locals) {
        allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
        IoBlock block = new IoBlock(callTarget, argNames, callSlotIsUsed, locals);
        allocationReporter.onReturnValue(block, 0, AllocationReporter.SIZE_UNKNOWN);
        return block;
    }

    public IoMethod createMethod(RootCallTarget callTarget, final TruffleString[] argNames,
            final boolean callSlotIsUsed) {
        allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
        IoMethod method = new IoMethod(callTarget, argNames, callSlotIsUsed);
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
        if (obj instanceof IoBaseObject) {
            return createLocals((IoBaseObject) obj, frame);
        }
        return createLocals(IoObjectUtil.getPrototype(obj), frame);
    }

    public IoLocals createLocals(final IoBaseObject prototype, final MaterializedFrame frame) {
        allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
        IoLocals locals = new IoLocals(prototype, frame);
        allocationReporter.onReturnValue(locals, 0, AllocationReporter.SIZE_UNKNOWN);
        return locals;
    }

    public IoCall createCall(final IoLocals sender, final Object target, final IoMessage message,
            final IoBaseObject slotContext, final IoInvokable activated, final IoCoroutine coroutine) {
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
