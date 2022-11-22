package jua.interpreter.instruction;

import jua.interpreter.InterpreterState;
import jua.compiler.CodePrinter;

/**
 * Данная инструкция прерывает выполнение в текущем потоке.
 */
public final class Halt implements Instruction {

    public static final Halt INSTANCE = new Halt();

    @Override
    public int stackAdjustment() {
        return 0;
    }

    @Override
    public void print(CodePrinter printer) {
        printer.printName("halt");
    }

    @Override
    public int run(InterpreterState state) {
        state.thread().interrupt();
        return 0;
    }
}