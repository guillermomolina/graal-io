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

import org.graalvm.options.OptionDescriptors;
import org.truffle.io.nodes.expression.FunctionBodyNode;
import org.truffle.io.nodes.root.EvalRootNode;
import org.truffle.io.parser.IoLanguageNodeVisitor;
import org.truffle.io.runtime.IoOptions;
import org.truffle.io.runtime.IoState;
import org.truffle.io.runtime.interop.IoLanguageView;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.debug.DebuggerTags;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;

@TruffleLanguage.Registration(id = IoLanguage.ID, name = "IO", defaultMimeType = IoLanguage.MIME_TYPE, characterMimeTypes = IoLanguage.MIME_TYPE, contextPolicy = ContextPolicy.SHARED, fileTypeDetectors = FileDetector.class, website = "https://iolanguage.org/")
@ProvidedTags({ StandardTags.CallTag.class, StandardTags.ExpressionTag.class, StandardTags.RootTag.class,
        StandardTags.RootBodyTag.class, StandardTags.ExpressionTag.class, DebuggerTags.AlwaysHalt.class,
        StandardTags.ReadVariableTag.class, StandardTags.WriteVariableTag.class })
public final class IoLanguage extends TruffleLanguage<IoState> {
    public static volatile int counter;

    public static final String ID = "io";
    public static final String MIME_TYPE = "application/x-io";

    public static final TruffleString.Encoding STRING_ENCODING = TruffleString.Encoding.UTF_16;

    private final Assumption singleContext = Truffle.getRuntime().createAssumption("Single IO context.");

    public IoLanguage() {
        counter++;
    }

    @Override
    protected IoState createContext(Env env) {
        return new IoState(this, env, new ArrayList<>(EXTERNAL_BUILTINS));
    }

    @Override
    protected boolean patchContext(IoState context, Env newEnv) {
        context.patchContext(newEnv);
        return true;
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
        IoLanguageNodeVisitor nodeVisitor = new IoLanguageNodeVisitor();
        RootCallTarget main = null;
        if (request.getArgumentNames().isEmpty()) {
            main = nodeVisitor.parseIO(this, source);
        } else {
            throw new NotImplementedException();
        }

        final RootNode evalMain = new EvalRootNode(this, main);
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
    protected Object getLanguageView(IoState context, Object value) {
        return IoLanguageView.create(value);
    }

    @Override
    protected boolean isVisible(IoState context, Object value) {
        // return !InteropLibrary.getFactory().getUncached(value).isNull(value);
        return true;
    }

    private static final LanguageReference<IoLanguage> REFERENCE = LanguageReference.create(IoLanguage.class);

    public static IoLanguage get(Node node) {
        return REFERENCE.get(node);
    }

    private static final List<NodeFactory<? extends FunctionBodyNode>> EXTERNAL_BUILTINS = Collections
            .synchronizedList(new ArrayList<>());

    public static void installBuiltin(NodeFactory<? extends FunctionBodyNode> builtin) {
        EXTERNAL_BUILTINS.add(builtin);
    }

    @Override
    protected void initializeContext(IoState context) {
        context.initialize();
    }

    @Override
    protected void exitContext(IoState context, ExitMode exitMode, int exitCode) {
        context.runShutdownHooks();
    }

    @TruffleBoundary
    public static TruffleLogger getLogger(Class<?> clazz) {
        return TruffleLogger.getLogger(ID, clazz);
    }

    @TruffleBoundary
    public static TruffleLogger getLogger(String name) {
        return TruffleLogger.getLogger(ID, name);
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        OptionDescriptors optionDescriptors = IoOptions.createDescriptors();
        return optionDescriptors;
    }

}
