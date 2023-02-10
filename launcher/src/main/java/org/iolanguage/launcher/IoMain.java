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
package org.iolanguage.launcher;

import jline.console.UserInterruptException;

import java.io.EOFException;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.UUID;

import org.graalvm.launcher.AbstractLanguageLauncher;
import org.graalvm.nativeimage.ProcessProperties;
import org.graalvm.options.OptionCategory;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.PolyglotException.StackFrame;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.SourceSection;

public final class IoMain extends AbstractLanguageLauncher {
    private static final String LF = System.getProperty("line.separator");

    public static final String SHORT_HELP = "usage: io [option] ... [-c cmd | file | -] [arg] ...\n" +
            "Try `io -h' for more information.";

    public static void main(String[] args) {
        new IoMain().launch(args);
    }

    private static final String LANGUAGE_ID = "io";

    private final boolean stdinIsInteractive = System.console() != null;
    private boolean ignoreEnv = false;
    private static long startupWallClockTime = -1;
    private static long startupNanoTime = -1;
    private ArrayList<String> programArgs = null;
    private ArrayList<String> origArgs = null;
    private String commandString = null;
    private String inputFile = null;
    private boolean verboseFlag = false;
    private VersionAction versionAction = VersionAction.None;
    private List<String> relaunchArgs;
    private boolean wantsExperimental = false;
    private boolean quietFlag = false;
    private String warnOptions = null;

    protected static void setStartupTime() {
        if (IoMain.startupNanoTime == -1) {
            IoMain.startupNanoTime = System.nanoTime();
        }
        if (IoMain.startupWallClockTime == -1) {
            IoMain.startupWallClockTime = System.currentTimeMillis();
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
        System.err.println(IoMain.class.getCanonicalName() + ": can't open file '" + e.getFile() + "': " + reason);
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
        System.out.println("usage: io [option] ... (-c cmd | file) [arg] ...\n" +
                "Options and arguments (and corresponding environment variables):\n" +
                "-c cmd : program passed in as string (terminates option list)\n" +
                // "-d : debug output from parser; also IODEBUG=x\n" +
                "-E     : ignore IO* environment variables (such as IOPATH)\n" +
                "-h     : print this help message and exit (also --help)\n" +
                "-i     : inspect interactively after running script; forces a prompt even\n" +
                "         if stdin does not appear to be a terminal; also IOINSPECT=x\n" +
                // "-Q arg : division options: -Qold (default), -Qwarn, -Qwarnall, -Qnew\n"
                // +
                "-q     : don't print version and copyright messages on interactive startup\n" +
                "-I     : don't add user site and script directory to sys.path; also IONOUSERSITE\n" +
                "-s     : don't add user site directory to sys.path; also IONOUSERSITE\n" +
                "-S     : don't imply 'import site' on initialization\n" +
                // "-t : issue warnings about inconsistent tab usage (-tt: issue errors)\n"
                // +
                "-u     : unbuffered binary stdout and stderr; also IOUNBUFFERED=x\n" +
                "-v     : verbose (trace import statements); also IOVERBOSE=x\n" +
                "         can be supplied multiple times to increase verbosity\n" +
                "-V     : print the Io version number and exit (also --version)\n" +
                "         when given twice, print more information about the build\n" +
                "-W arg : warning control; arg is action:message:category:module:lineno\n" +
                "         also IOWARNINGS=arg\n" +
                // "-x : skip first line of source, allowing use of non-Unix forms of
                // #!cmd\n" +
                "file   : program read from script file\n" +
                "-      : program read from stdin\n" +
                "arg ...: arguments passed to program in sys.argv[1:]\n" +
                "\n" +
                "Other environment variables:\n" +
                "IO_LIB_PATH  : ':'-separated list of directories prefixed to the\n" +
                "               default lib search path.  The result is sys.path.\n");
    }

    private void addRelaunchArg(String arg) {
        if (relaunchArgs == null) {
            relaunchArgs = new ArrayList<>();
        }
        relaunchArgs.add(arg);
    }


    private static void print(String string) {
        System.err.println(string);
    }

    @Override
    protected List<String> preprocessArguments(List<String> givenArgs, Map<String, String> polyglotOptions) {
        ArrayList<String> unrecognized = new ArrayList<>();
        List<String> defaultEnvironmentArgs = getDefaultEnvironmentArgs();
        ArrayList<String> inputArgs = new ArrayList<>(defaultEnvironmentArgs);
        inputArgs.addAll(givenArgs);
        List<String> arguments = new ArrayList<>(inputArgs);
        programArgs = new ArrayList<>();
        origArgs = new ArrayList<>();
        List<String> subprocessArgs = new ArrayList<>();
        programArgs = new ArrayList<>();
        origArgs = new ArrayList<>();
        for (Iterator<String> argumentIterator = arguments.iterator(); argumentIterator.hasNext();) {
            String arg = argumentIterator.next();
            origArgs.add(arg);
            if (arg.startsWith("-")) {
                if (arg.length() == 1) {
                    // Lone dash should just be skipped
                    continue;
                }

                if (wantsExperimental) {
                    switch (arg) {
                        case "-debug-java":
                            if (!isAOT()) {
                                subprocessArgs.add("agentlib:jdwp=transport=dt_socket,server=y,address=8000,suspend=y");
                                inputArgs.remove("-debug-java");
                            }
                            continue;
                        case "-debug-perf":
                            unrecognized.add("--engine.TraceCompilation");
                            unrecognized.add("--engine.TraceCompilationDetails");
                            unrecognized.add("--engine.TraceInlining");
                            unrecognized.add("--engine.TraceSplitting");
                            unrecognized.add("--engine.TraceCompilationPolymorphism");
                            unrecognized.add("--engine.TraceAssumptions");
                            unrecognized.add("--engine.TraceTransferToInterpreter");
                            unrecognized.add("--engine.TracePerformanceWarnings=all");
                            unrecognized.add("--engine.CompilationFailureAction=Print");
                            inputArgs.remove("-debug-perf");
                            continue;
                        case "-dump":
                            subprocessArgs.add("Dgraal.Dump=");
                            inputArgs.add("--engine.BackgroundCompilation=false");
                            inputArgs.remove("-dump");
                            continue;
                    }
                }

                if (arg.startsWith("--")) {
                    // Long options
                    switch (arg) {
                        // --help gets passed through as unrecognized
                        case "--version":
                            versionAction = VersionAction.PrintAndExit;
                            continue;
                        case "--show-version":
                            versionAction = VersionAction.PrintAndContinue;
                            continue;
                        case "--experimental-options":
                        case "--experimental-options=true":
                            /*
                             * This is the default Truffle experimental option flag. We also use it
                             * for our custom launcher options
                             */
                            wantsExperimental = true;
                            addRelaunchArg(arg);
                            unrecognized.add(arg);
                            continue;
                        default:
                            // possibly a polyglot argument
                            unrecognized.add(arg);
                            continue;
                    }
                }

                String remainder = arg.substring(1);
                shortOptionLoop: while (!remainder.isEmpty()) {
                    char option = remainder.charAt(0);
                    remainder = remainder.substring(1);
                    switch (option) {
                        case 'E':
                            ignoreEnv = true;
                            break;
                        case '?':
                        case 'h':
                            unrecognized.add("--help");
                            break;
                        case 'q':
                            quietFlag = true;
                            break;
                        case 'v':
                            verboseFlag = true;
                            break;
                        case 'V':
                            versionAction = VersionAction.PrintAndExit;
                            break;
                        case 'W':
                            if (warnOptions == null) {
                                warnOptions = "";
                            } else {
                                warnOptions += ",";
                            }
                            warnOptions += getShortOptionParameter(argumentIterator, remainder, 'W');
                            break shortOptionLoop;
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

    private String getShortOptionParameter(Iterator<String> argumentIterator, String remainder, char option) {
        String ret;
        if (remainder.isEmpty()) {
            if (!argumentIterator.hasNext()) {
                throw abort(String.format("Argument expected for the -%c option\n", option) + SHORT_HELP, 2);
            }
            ret = argumentIterator.next();
        } else {
            ret = remainder;
        }
        origArgs.add(ret);
        return ret;
    }

    @Override
    protected void launch(Builder contextBuilder) {
        IoMain.setStartupTime();

        // prevent the use of System.out/err - they are PrintStreams which suppresses exceptions
        contextBuilder.out(new FileOutputStream(FileDescriptor.out));
        contextBuilder.err(new FileOutputStream(FileDescriptor.err));
        if (!ignoreEnv) {
            String io_lib_path = System.getenv("IO_LIB_PATH");
            if (io_lib_path != null) {
                contextBuilder.option("io.io-lib-path", io_lib_path);
            }
            verboseFlag = verboseFlag || System.getenv("IO_VERBOSE") != null;

            String envWarnOptions = System.getenv("IO_WARNINGS");
            if (envWarnOptions != null && !envWarnOptions.isEmpty()) {
                if (warnOptions == null) {
                    warnOptions = envWarnOptions;
                } else {
                    warnOptions = envWarnOptions + "," + warnOptions;
                }
            }
            String encoding = System.getenv("IO_ENCODING");
            if (encoding != null) {
                contextBuilder.option("io.encoding", encoding);
            }
        }
        if (warnOptions == null || warnOptions.isEmpty()) {
            warnOptions = "";
        }

        if (relaunchArgs != null) {
            Iterator<String> it = origArgs.iterator();
            while (it.hasNext()) {
                if (relaunchArgs.contains(it.next())) {
                    it.remove();
                }
            }
        }

        ConsoleHandler consoleHandler = createConsoleHandler(System.in, System.out);
        contextBuilder.arguments(getLanguageId(), programArgs.toArray(new String[programArgs.size()]));
        contextBuilder.in(consoleHandler.createInputStream());


        if (verboseFlag) {
            contextBuilder.option("log.io.level", "FINE");
        }

        int rc = 1;
        try (Context context = contextBuilder.build()) {
            runVersionAction(versionAction, context.getEngine());

            if (!quietFlag && (verboseFlag || (commandString == null && inputFile == null && stdinIsInteractive))) {
                //print("Python " + evalInternal(context, "import sys; sys.version + ' on ' + sys.platform").asString());
                print("Type \"help\", \"copyright\", \"credits\" or \"license\" for more information.");
            }
            consoleHandler.setContext(context);

            if (commandString != null || inputFile != null || !stdinIsInteractive) {
                try {
                    evalNonInteractive(context, consoleHandler);
                    rc = 0;
                    /*} catch (PolyglotException e) {
                    if (!e.isExit()) {
                        printIoLikeStackTrace(e);
                    } else {
                        rc = e.getExitStatus();
                    } */
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

    private static enum State {
        NORMAL,
        SINGLE_QUOTE,
        DOUBLE_QUOTE,
        ESCAPE_SINGLE_QUOTE,
        ESCAPE_DOUBLE_QUOTE,
    }

    private static List<String> getDefaultEnvironmentArgs() {
        String pid;
        if (isAOT()) {
            pid = String.valueOf(ProcessProperties.getProcessID());
        } else {
            pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        }
        String uuid = UUID.randomUUID().toString();
        String envArgsOpt = System.getenv("GRAAL_IO_ARGS");
        ArrayList<String> envArgs = new ArrayList<>();
        if (envArgsOpt != null) {
            State s = State.NORMAL;
            StringBuilder sb = new StringBuilder();
            for (char x : envArgsOpt.toCharArray()) {
                if (s == State.NORMAL && Character.isWhitespace(x)) {
                    addArgument(pid, uuid, envArgs, sb);
                } else {
                    if (x == '"') {
                        if (s == State.NORMAL) {
                            s = State.DOUBLE_QUOTE;
                        } else if (s == State.DOUBLE_QUOTE) {
                            s = State.NORMAL;
                        } else if (s == State.ESCAPE_DOUBLE_QUOTE) {
                            s = State.DOUBLE_QUOTE;
                            sb.append(x);
                        }
                    } else if (x == '\'') {
                        if (s == State.NORMAL) {
                            s = State.SINGLE_QUOTE;
                        } else if (s == State.SINGLE_QUOTE) {
                            s = State.NORMAL;
                        } else if (s == State.ESCAPE_SINGLE_QUOTE) {
                            s = State.SINGLE_QUOTE;
                            sb.append(x);
                        }
                    } else if (x == '\\') {
                        if (s == State.SINGLE_QUOTE) {
                            s = State.ESCAPE_SINGLE_QUOTE;
                        } else if (s == State.DOUBLE_QUOTE) {
                            s = State.ESCAPE_DOUBLE_QUOTE;
                        }
                    } else {
                        sb.append(x);
                    }
                }
            }
            addArgument(pid, uuid, envArgs, sb);
        }
        return envArgs;
    }

    private static void addArgument(String pid, String uuid, ArrayList<String> envArgs, StringBuilder sb) {
        if (sb.length() > 0) {
            String arg = sb.toString().replace("$UUID$", uuid).replace("$$", pid).replace("\\$", "$");
            envArgs.add(arg);
            sb.setLength(0);
        }
    }

}
