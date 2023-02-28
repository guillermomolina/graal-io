/*
 * Copyright (c) 2022, 2023, Guillermo Adrián Molina. All rights reserved.
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

import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.TruffleLanguage.Env;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionValues;
import org.iolanguage.IoLanguage;

@Option.Group(IoLanguage.ID)
public class IoOptions {
    public static final String IO_LIB_PATH_NAME = "io-lib-path";
    public static final String IO_LIB_PATH_HELP = "Path ':'-separated list of directories prefixed to the default lib search path.";
    @Option(name = IO_LIB_PATH_NAME, category = OptionCategory.USER, help = IO_LIB_PATH_HELP) //
    public static final OptionKey<String> IoLibPath = new OptionKey<>("./io");

    public static final String IO_NUMBER_LEGACY_FORMAT_NAME = "io-number-legacy-format";
    public static final String IO_NUMBER_LEGACY_FORMAT_HELP = "Boolean ':'-Show numbers as original io does.";
    @Option(name = IO_NUMBER_LEGACY_FORMAT_NAME, category = OptionCategory.USER, help = IO_NUMBER_LEGACY_FORMAT_HELP) //
    public static final OptionKey<Boolean> IoNumberLegacyFormat = new OptionKey<>(false);

    private IoOptions() { // no instances
    }

    public static OptionDescriptors createDescriptors() {
        return new IoOptionsOptionDescriptors();
    }

    public static final class IoStateOptions {
        public final String[] libPath;
        public final boolean numberLegacyFormat;

        public IoStateOptions(final Env env) {
            final OptionValues options = env.getOptions();
            libPath = options.get(IoLibPath).isEmpty() ? new String[0] : options.get(IoLibPath).split(":");
            numberLegacyFormat = options.get(IoNumberLegacyFormat);
        }
    }
}
