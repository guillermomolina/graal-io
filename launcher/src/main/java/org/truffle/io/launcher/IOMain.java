/*
 * Copyright (c) 2022, 2023, Guillermo Adrián Molina. All rights reserved.
 */
/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.truffle.io.launcher;

import java.io.EOFException;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.graalvm.launcher.AbstractLanguageLauncher;
import org.graalvm.options.OptionCategory;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.PolyglotException.StackFrame;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.SourceSection;

import jline.console.UserInterruptException;

public final class IOMain extends AbstractLanguageLauncher {

    private static final String LF = System.getProperty("line.separator");

    public static final String SHORT_HELP = "usage: io [option] ... [-c cmd | file | -] [arg] ...\n" +
            "Try `io -h' for more information.";

    public static void main(String[] args) {
        new IOMain().launch(args);
    }

    private static final String LANGUAGE_ID = "io";

    private final boolean stdinIsInteractive = System.console() != null;
    private static long startupWallClockTime = -1;
    private static long startupNanoTime = -1;
    private ArrayList<String> programArgs = null;
    private ArrayList<String> origArgs = null;
    private String commandString = null;
    private String inputFile = null;
    private boolean verboseFlag = false;

    protected static void setStartupTime() {
        if (IOMain.startupNanoTime == -1) {
            IOMain.startupNanoTime = System.nanoTime();
        }
        if (IOMain.startupWallClockTime == -1) {
            IOMain.startupWallClockTime = System.currentTimeMillis();
        }
    }

    private void evalNonInteractive(Context context, ConsoleHandler consoleHandler) throws IOException {
        // We need to setup the terminal even when not running the REPL because code may request
        // input from the terminal.

        Source src;
        if (commandString != null) {
            src = Source.newBuilder(getLanguageId(), commandString, "<string>").build();
        } else {
            src = Source.newBuilder(getLanguageId(), readAllLines(inputFile), "<internal>").internal(true).build();
        }
        context.eval(src);
    }

    private static String readAllLines(String fileName) throws IOException {
        // fix line feeds for non unix os
        StringBuilder outFile = new StringBuilder();
        for (String line : Files.readAllLines(Paths.get(fileName), Charset.defaultCharset())) {
            outFile.append(line).append(LF);
        }
        return outFile.toString();
    }

    @Override
    protected String getLanguageId() {
        return LANGUAGE_ID;
    }

    private static void printFileNotFoundException(NoSuchFileException e) {
        String reason = e.getReason();
        if (reason == null) {
            reason = "No such file or directory";
        }
        System.err.println(IOMain.class.getCanonicalName() + ": can't open file '" + e.getFile() + "': " + reason);
    }

    private static void printIoLikeStackTrace(PolyglotException e) {
        // If we're running through the launcher and an exception escapes to here,
        // we didn't go through the Python code to print it. That may be because
        // it's an exception from another language. In this case, we still would
        // like to print it like a Python exception.
        ArrayList<String> stack = new ArrayList<>();
        for (StackFrame frame : e.getPolyglotStackTrace()) {
            if (frame.isGuestFrame()) {
                StringBuilder sb = new StringBuilder();
                SourceSection sourceSection = frame.getSourceLocation();
                String rootName = frame.getRootName();
                if (sourceSection != null) {
                    sb.append("  ");
                    String path = sourceSection.getSource().getPath();
                    if (path != null) {
                        sb.append("File ");
                    }
                    sb.append('"');
                    sb.append(sourceSection.getSource().getName());
                    sb.append("\", line ");
                    sb.append(sourceSection.getStartLine());
                    sb.append(", in ");
                    sb.append(rootName);
                    stack.add(sb.toString());
                }
            }
        }
        System.err.println("Traceback (most recent call last):");
        ListIterator<String> listIterator = stack.listIterator(stack.size());
        while (listIterator.hasPrevious()) {
            System.err.println(listIterator.previous());
        }
        System.err.println(e.getMessage());
    }

    @Override
    protected void printHelp(OptionCategory maxCategory) {
        System.out.println("usage: io [option] ... (-c cmd | file) [arg] ...\n");
    }

    @Override
    protected List<String> preprocessArguments(List<String> givenArgs, Map<String, String> polyglotOptions) {
        ArrayList<String> unrecognized = new ArrayList<>();
        programArgs = new ArrayList<>();
        origArgs = new ArrayList<>();
        for (Iterator<String> argumentIterator = givenArgs.iterator(); argumentIterator.hasNext();) {
            String arg = argumentIterator.next();
            origArgs.add(arg);
            if (arg.startsWith("-")) {
                if (arg.length() == 1) {
                    // Lone dash should just be skipped
                    continue;
                }
                String remainder = arg.substring(1);
                while (!remainder.isEmpty()) {
                    char option = remainder.charAt(0);
                    remainder = remainder.substring(1);
                    switch (option) {
                        case 'v':
                            verboseFlag = true;
                            break;
                        default:
                            throw abort(String.format("Unknown option -%c\n", option) + SHORT_HELP, 2);
                    }
                }
            } else {
                // Not an option, has to be a file name
                inputFile = arg;
                programArgs.add(arg);
            }

            if (inputFile != null || commandString != null) {
                while (argumentIterator.hasNext()) {
                    String a = argumentIterator.next();
                    programArgs.add(a);
                    origArgs.add(a);
                }
                break;
            }
        }
        return unrecognized;
    }

    @Override
    protected void launch(Builder contextBuilder) {
        IOMain.setStartupTime();

        // prevent the use of System.out/err - they are PrintStreams which suppresses exceptions
        contextBuilder.out(new FileOutputStream(FileDescriptor.out));
        contextBuilder.err(new FileOutputStream(FileDescriptor.err));

        ConsoleHandler consoleHandler = createConsoleHandler(System.in, System.out);
        contextBuilder.arguments(getLanguageId(), programArgs.toArray(new String[programArgs.size()]));
        contextBuilder.in(consoleHandler.createInputStream());

        if (verboseFlag) {
            contextBuilder.option("log.io.level", "FINE");
        }

        int rc = 1;
        try (Context context = contextBuilder.build()) {
            consoleHandler.setContext(context);

            if (commandString != null || inputFile != null || !stdinIsInteractive) {
                try {
                    evalNonInteractive(context, consoleHandler);
                    rc = 0;
                } catch (PolyglotException e) {
                    if (!e.isExit()) {
                        printIoLikeStackTrace(e);
                    } else {
                        rc = e.getExitStatus();
                    }
                } catch (NoSuchFileException e) {
                    printFileNotFoundException(e);
                }
            }
            if (commandString == null && inputFile == null) {
                rc = readEvalPrint(context, consoleHandler);
            }
        } catch (IOException e) {
            rc = 1;
            e.printStackTrace();
        } finally {
            consoleHandler.setContext(null);
        }
        System.exit(rc);
    }

    private ConsoleHandler createConsoleHandler(InputStream inStream, OutputStream outStream) {
        if (!stdinIsInteractive) {
            return new DefaultConsoleHandler(inStream, outStream, false);
        } else {
            try {
                return new JLineConsoleHandler(inStream, outStream, false);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    public int readEvalPrint(Context context, ConsoleHandler consoleHandler) {
        int lastStatus = 0;
        try {
            while (true) { // processing inputs
                consoleHandler.setPrompt("Io> ");

                try {
                    String input = consoleHandler.readLine();
                    if (input == null) {
                        throw new EOFException();
                    }
                    if (canSkipFromEval(input)) {
                        // nothing to parse
                        continue;
                    }

                    String continuePrompt = "... ";
                    StringBuilder sb = new StringBuilder(input).append('\n');
                    while (true) { // processing subsequent lines while input is incomplete
                        lastStatus = 0;
                        try {
                            context.eval(Source.newBuilder(getLanguageId(), sb.toString(), "<stdin>").interactive(true)
                                    .buildLiteral());
                        } catch (PolyglotException e) {
                            if (e.isIncompleteSource()) {
                                // read more input until we get an empty line
                                consoleHandler.setPrompt(continuePrompt);
                                String additionalInput;
                                boolean isIncompleteCode = false; // this for cases like empty lines
                                                                  // in tripplecode, where the
                                                                  // additional input can be empty,
                                                                  // but we should still continue
                                do {
                                    additionalInput = consoleHandler.readLine();
                                    sb.append(additionalInput).append('\n');
                                    try {
                                        // We try to parse every time, when an additional input is
                                        // added to find out if there is continuation exception or
                                        // other error. If there is other error, we have to stop
                                        // to ask for additional input.
                                        context.parse(Source.newBuilder(getLanguageId(), sb.toString(), "<stdin>")
                                                .interactive(true).buildLiteral());
                                        e = null; // the parsing was ok -> try to eval
                                                  // the code in outer while loop
                                        isIncompleteCode = false;
                                    } catch (PolyglotException pe) {
                                        if (!pe.isIncompleteSource()) {
                                            e = pe;
                                            break;
                                        } else {
                                            isIncompleteCode = true;
                                        }
                                    }
                                } while (additionalInput != null && isIncompleteCode);
                                // Here we can be in these cases:
                                // The parsing of the code with additional code was ok
                                // The parsing of the code with additional code thrown an error,
                                // which is not IncompleteSourceException
                                if (additionalInput == null) {
                                    throw new EOFException();
                                }
                                if (e == null) {
                                    // the last source (with additional input) was parsed correctly,
                                    // so we can execute it.
                                    continue;
                                }
                            }
                            // process the exception from eval or from the last parsing of the input
                            // + additional source
                            if (e.isExit()) {
                                // usually from quit
                                throw new ExitException(e.getExitStatus());
                            } else if (e.isHostException()) {
                                // we continue the repl even though the system may be broken
                                lastStatus = 1;
                                System.out.println(e.getMessage());
                            } else if (e.isInternalError()) {
                                System.err.println("An internal error occurred:");
                                printIoLikeStackTrace(e);

                                // we continue the repl even though the system may be broken
                                lastStatus = 1;
                            } else if (e.isGuestException()) {
                                // drop through to continue REPL and remember last eval was an error
                                lastStatus = 1;
                            }
                        }
                        break;
                    }
                } catch (EOFException e) {
                    System.out.println();
                    return lastStatus;
                } catch (UserInterruptException e) {
                    // interrupted by ctrl-c
                }
            }
        } catch (ExitException e) {
            return e.code;
        }
    }

    private static boolean canSkipFromEval(String input) {
        String[] split = input.split("\n");
        for (String s : split) {
            if (!s.isEmpty() && !s.startsWith("#") && !s.startsWith("//") && !s.startsWith("/*")) {
                return false;
            }
        }
        return true;
    }

    private static final class ExitException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final int code;

        ExitException(int code) {
            this.code = code;
        }
    }
}
