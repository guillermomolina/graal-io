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
package org.truffle.io;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy;
import com.oracle.truffle.api.debug.DebuggerTags;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;

import org.truffle.io.builtins.IOBuiltinNode;
import org.truffle.io.nodes.root.IOEvalRootNode;
import org.truffle.io.nodes.root.IOUndefinedMethodRootNode;
import org.truffle.io.parser.IOLanguageNodeVisitor;
import org.truffle.io.runtime.IOState;
import org.truffle.io.runtime.interop.IOLanguageView;


@TruffleLanguage.Registration(id = IOLanguage.ID, name = "IO", defaultMimeType = IOLanguage.MIME_TYPE, characterMimeTypes = IOLanguage.MIME_TYPE, contextPolicy = ContextPolicy.SHARED, fileTypeDetectors = IOFileDetector.class, //
                website = "https://iolanguage.org/")
@ProvidedTags({StandardTags.CallTag.class, StandardTags.ExpressionTag.class, StandardTags.RootTag.class, StandardTags.RootBodyTag.class, StandardTags.ExpressionTag.class, DebuggerTags.AlwaysHalt.class,
                StandardTags.ReadVariableTag.class, StandardTags.WriteVariableTag.class})
public final class IOLanguage extends TruffleLanguage<IOState> {
    public static volatile int counter;

    public static final String ID = "io";
    public static final String MIME_TYPE = "application/x-io";

    public static final TruffleString.Encoding STRING_ENCODING = TruffleString.Encoding.UTF_16;

    private final Assumption singleContext = Truffle.getRuntime().createAssumption("Single IO context.");

    private final Map<TruffleString, RootCallTarget> undefinedFunctions = new ConcurrentHashMap<>();

    public IOLanguage() {
        counter++;
    }

    @Override
    protected IOState createContext(Env env) {
        return new IOState(this, env, new ArrayList<>(EXTERNAL_BUILTINS));
    }

    @Override
    protected boolean patchContext(IOState context, Env newEnv) {
        context.patchContext(newEnv);
        return true;
    }

    public RootCallTarget getOrCreateUndefinedFunction(TruffleString name) {
        RootCallTarget target = undefinedFunctions.get(name);
        if (target == null) {
            target = new IOUndefinedMethodRootNode(this, name).getCallTarget();
            RootCallTarget other = undefinedFunctions.putIfAbsent(name, target);
            if (other != null) {
                target = other;
            }
        }
        return target;
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

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        Source source = request.getSource();
        IOLanguageNodeVisitor nodeVisitor = new IOLanguageNodeVisitor();
        RootCallTarget main = null;
        if (request.getArgumentNames().isEmpty()) {
            main = nodeVisitor.parseIO(this, source);
        } else {
            throw new NotImplementedException();
        }

        final RootNode evalMain = new IOEvalRootNode(this, main);
        return evalMain.getCallTarget();
    }

    @Override
    protected void initializeMultipleContexts() {
        singleContext.invalidate();
    }

    public boolean isSingleContext() {
        return singleContext.isValid();
    }

    @Override
    protected Object getLanguageView(IOState context, Object value) {
        return IOLanguageView.create(value);
    }

    @Override
    protected boolean isVisible(IOState context, Object value) {
        return !InteropLibrary.getFactory().getUncached(value).isNull(value);
    }

    private static final LanguageReference<IOLanguage> REFERENCE = LanguageReference.create(IOLanguage.class);

    public static IOLanguage get(Node node) {
        return REFERENCE.get(node);
    }

    private static final List<NodeFactory<? extends IOBuiltinNode>> EXTERNAL_BUILTINS = Collections.synchronizedList(new ArrayList<>());

    public static void installBuiltin(NodeFactory<? extends IOBuiltinNode> builtin) {
        EXTERNAL_BUILTINS.add(builtin);
    }

    @Override
    protected void exitContext(IOState context, ExitMode exitMode, int exitCode) {
        context.runShutdownHooks();
    }
}
