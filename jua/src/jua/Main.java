package jua;

import jua.compiler.Program;
import jua.compiler.JuaCompiler;
import jua.runtime.RuntimeErrorException;

import java.io.File;
import java.io.IOException;

public class Main {

    public static final String NAME = "Jua";
    // todo: Разделить версию на мажорную и минорную
    public static final String VERSION = "1.95.209";

    // todo: Мне лень сейчас обработкой исключений заниматься..
    public static void main(String[] args) throws IOException {
        try {
            Options.bind(args);
        } catch (IllegalArgumentException e) {
            error("unrecognized option: " + e.getMessage());
        } catch (Throwable t) {
            error("can't parse console arguments: " + t);
        }

        // todo: Работа с несколькими файлами одновременно

        File file = testTargetFile();
        Program result = JuaCompiler.compileFile(file);

        if (result == null) return;
        if (Options.disassembler()) {
            result.print();
            if (Options.stop()) {
                return;
            }
        }
        try {
            result.toThread().run();
        } catch (RuntimeErrorException e) {
            // todo: Починить вывод который влад сломал
            e.printStackTrace();
        }
    }

    private static File testTargetFile() {
        String filename = Options.filename();

        if (filename == null) {
            error("main file not specified.");
            throw new ThreadDeath(); // avoiding warnings
        }
        File file = new File(filename);

        if (!file.isFile()) {
            error("main file not found.");
        }
        return file;
    }

    private static void error(String message) {
        System.err.printf("Error: %s%n", message);
        System.exit(1);
    }
}
