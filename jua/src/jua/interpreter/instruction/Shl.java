package jua.interpreter.instruction;

import jua.compiler.CodePrinter;
import jua.interpreter.InterpreterState;

public final class Shl implements Instruction {

    @Override
    public int stackAdjustment() { return -1 + -1 + 1; }

    @Override
    public void print(CodePrinter printer) {
        printer.printName("shl");
    }

    @Override
    public void run(InterpreterState state) {
        state.stackShl();
    }
}