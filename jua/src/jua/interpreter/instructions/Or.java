package jua.interpreter.instructions;

import jua.interpreter.InterpreterError;
import jua.interpreter.InterpreterState;
import jua.runtime.BooleanOperand;
import jua.runtime.LongOperand;
import jua.runtime.Operand;
import jua.compiler.CodePrinter;

public enum Or implements Instruction {

    INSTANCE;

    @Override
    public void print(CodePrinter printer) {
        printer.printName("or");
    }

    @Override
    public int run(InterpreterState state) {
        Operand rhs = state.popStack();
        Operand lhs = state.popStack();

        if (lhs.isLong() && rhs.isLong()) {
            state.pushStack(new LongOperand(lhs.longValue() | rhs.longValue()));
        } else if (lhs.isBoolean() && rhs.isBoolean()) {
            state.pushStack(new BooleanOperand() {
                @Override
                public boolean booleanValue() {
                    return lhs.booleanValue() | rhs.booleanValue();
                }
            });
        } else {
            throw InterpreterError.binaryApplication("|", lhs.type(), rhs.type());
        }
        return NEXT;
    }
}