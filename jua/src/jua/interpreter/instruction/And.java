package jua.interpreter.instruction;

import jua.interpreter.InterpreterState;
import jua.runtime.heap.Operand;
import jua.compiler.CodePrinter;

public enum And implements Instruction {

    INSTANCE;

    @Override
    public void print(CodePrinter printer) {
        printer.printName("and");
    }

    @Override
    public int run(InterpreterState state) {
        state.stackAnd();
        return NEXT;
    }
}