/*
 * Copyright (c) 2022, 2023, Guillermo Adrián Molina. All rights reserved.
 */
/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.iolanguage.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;

import org.graalvm.polyglot.Context;
import org.graalvm.shadowed.org.jline.reader.EndOfFileException;
import org.graalvm.shadowed.org.jline.reader.History;
import org.graalvm.shadowed.org.jline.reader.LineReader;
import org.graalvm.shadowed.org.jline.reader.LineReaderBuilder;
import org.graalvm.shadowed.org.jline.reader.UserInterruptException;
import org.graalvm.shadowed.org.jline.reader.impl.DefaultParser;
import org.graalvm.shadowed.org.jline.reader.impl.history.DefaultHistory;
import org.graalvm.shadowed.org.jline.terminal.Terminal;
import org.graalvm.shadowed.org.jline.terminal.TerminalBuilder;

public class JLineConsoleHandler extends ConsoleHandler {
    private final History history = new DefaultHistory();
    private final InputStream in;
    private final OutputStream out;
    private final Terminal terminal;
    private LineReader reader;

    private final boolean noPrompt;
    private String prompt;
    private int currentLine;

    public JLineConsoleHandler(InputStream inStream, OutputStream outStream, boolean noPrompt) throws IOException {
        this.noPrompt = noPrompt;
        this.in = inStream;
        this.out = outStream;
        this.terminal = terminal();
    }

    @Override
    public void setContext(Context context) {
        reader = LineReaderBuilder.builder().terminal(terminal).history(history).parser(new ParserWithCustomDelimiters()).build();
    }

    @Override
    public String readLine() {
        try {
            currentLine++;
            return reader.readLine(prompt);
        } catch (UserInterruptException ex) {
            // interrupted by ctrl-c
            return "";
        } catch (EndOfFileException ex) {
            // interrupted by ctrl-d
            return null;
        } catch (Throwable ex) {
            if (ex.getCause() instanceof InterruptedIOException || ex.getCause() instanceof InterruptedException) {
                // seen this with ctrl-c
                return "";
            }
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void setPrompt(String prompt) {
        this.prompt = noPrompt ? "" : prompt != null ? prompt : "";
    }

    @Override
    public String getPrompt() {
        return prompt;
    }

    public void clearHistory() {
        try {
            history.purge();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void addToHistory(String input) {
        history.add(input);
    }

    public String[] getHistory() {
        String[] result = new String[history.size()];
        for (int i = 0; i < history.size(); i++) {
            result[i] = history.get(i);
        }
        return result;
    }

    @Override
    public int getCurrentLineIndex() {
        return currentLine;
    }

    private Terminal terminal() throws IOException {
        return TerminalBuilder.builder().jna(false).streams(in, out).system(true).signalHandler(Terminal.SignalHandler.SIG_IGN).build();
    }

    public class ParserWithCustomDelimiters extends DefaultParser {

        private char[] delimiters = {'(', ','};

        public String getDelimiters() {
            return new String(delimiters);
        }

        public void setDelimiters(String delimiters) {
            this.delimiters = delimiters.toCharArray();
        }

        @Override
        public boolean isDelimiterChar(CharSequence buffer, int pos) {
            char c = buffer.charAt(pos);
            if (Character.isWhitespace(c)) {
                return true;
            }
            for (char delimiter : delimiters) {
                if (c == delimiter) {
                    return true;
                }
            }
            return false;
        }
    }
}
