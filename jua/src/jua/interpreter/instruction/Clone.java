package jua.interpreter.instruction;

import jua.compiler.CodePrinter;
import jua.interpreter.InterpreterState;

public enum Clone implements Instruction {

    INSTANCE;

    @Override
    public void print(CodePrinter printer) {
        printer.printName("clone");
    }

    @Override
    public int run(InterpreterState state) {
        state.stackClone();
        return NEXT;
    }
}