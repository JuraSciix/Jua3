package jua.interpreter.instruction;

import jua.compiler.CodePrinter;
import jua.interpreter.InterpreterState;

public final class Dup2 implements Instruction {

    @Override
    public int stackAdjustment() {
        return -1 + -1 + 1 + 1 + 1 + 1;
    }

    @Override
    public void print(CodePrinter printer) {
        printer.printName("dup2");
    }

    @Override
    public void run(InterpreterState state) {
        state.dup2();
    }
}