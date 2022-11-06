package jua.interpreter.instruction;

import jua.compiler.CodePrinter;
import jua.interpreter.InterpreterState;
import jua.runtime.heap.LongOperand;

// todo: Переименовать инструкцию в iconst
public final class Push implements Instruction {

    private final short value;

    public Push(short value) {
        this.value = value;
    }

    @Override
    public void print(CodePrinter printer) {
        printer.printName("push");
        printer.print(value);
    }

    @Override
    public int run(InterpreterState state) {
        state.pushStack(value);
        return NEXT;
    }
}
