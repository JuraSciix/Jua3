package jua.compiler;

import jua.Options;
import jua.compiler.utils.ObjectsSize;
import jua.interpreter.Program;
import jua.interpreter.InterpreterRuntime;
import jua.interpreter.InterpreterError;
import jua.interpreter.runtime.*;

import java.util.Arrays;
import java.util.Random;
import java.util.StringJoiner;

import static jua.interpreter.runtime.OperandType.*;

// temporary class
class BuiltInDefinitions {

    private static Random random = null;

    static void init(CodeData codeData) {
        codeData.setConstant("CALLSTACK_LIMIT", new Constant(LongOperand.valueOf(InterpreterRuntime.MAX_CALLSTACK_SIZE), true));
        codeData.setConstant("ARGV", new Constant(new ArrayOperand(Array.fromArray(Options.argv(), StringOperand::valueOf)), true));

        codeData.setFunction("_sizeof", ((env, name, argc) -> {
            checkArgs(name, argc, 1);
            env.pushStack(ObjectsSize.sizeOf(popArgument(env, name, 1, null)));

        }));
        codeData.setFunction("get_stack_trace", ((env, name, argc) -> {
            checkArgs(name, argc, 0, 1);
            long longLimit = (argc == 1) ? popArgument(env, name, 1, INT).intValue() : -1;
            if ((argc == 1) && (longLimit < 0)) {
                error(name, "limit cannot be less than zero.");
            } else if (longLimit > Short.MAX_VALUE) {
                error(name, "limit cannot be greater than Short.MAX_VALUE");
            }
            CallStackElement[] stackTrace = (longLimit >= 0)
                    ? Arrays.copyOf(env.getCallStack(), (int) longLimit)
                    : env.getCallStack();
            env.pushStack(Array.fromArray(stackTrace, element -> {
                Array array = new Array();
                if (element.name != null) { // if not entry point
                    array.add(env.getOperand(element.name));
                    array.add(env.getOperand(element.filename));
                    array.add(env.getOperand(element.line));
                    array.add(env.getOperand(Array.fromArray(element.args, a -> a)));
                }
                return env.getOperand(array);
            }));

        }));
        codeData.setFunction("array_keys", ((env, name, argc) -> {
            checkArgs(name, argc, 1);
            env.pushStack(popArgument(env, name, 1, ARRAY).arrayValue().getKeys());

        }));
        codeData.setFunction("array_values", ((env, name, argc) -> {
            checkArgs(name, argc, 1);
            env.pushStack(popArgument(env, name, 1, ARRAY).arrayValue().getValues());

        }));
        codeData.setFunction("length", ((env, name, argc) -> {
            checkArgs(name, argc, 1);
            Operand val = env.popStack();
            switch (val.type()) {
                case STRING: { env.pushStack(val.stringValue().length()); break; }
                case ARRAY: { env.pushStack(val.arrayValue().count()); break; }
                default: error(name, "expected string or array at first argument.");
            }

        }));
        codeData.setFunction("typeof", ((env, name, argc) -> {
            checkArgs(name, argc, 0, 1);
            if ((argc == 1) && popArgument(env, name, 1, BOOLEAN).booleanValue()) {
                env.pushStack(env.popStack().type().ordinal());
            } else {
                env.pushStack(env.popStack().type().toString());
            }

        }));
        codeData.setFunction("_sizeof_function", ((env, name, argc) -> {
            checkArgs(name, argc, 1);
            String fn = popArgument(env, name, 1, STRING).stringValue();
            RuntimeFunction func = env.getFunctionByName(fn);
            if (func == null) error(name, "function '" + fn + "' not found.");
            env.pushStack(ObjectsSize.sizeOf(func));

        }));
        codeData.setFunction("thread", ((env, name, argc) -> {
            checkArgs(name, argc, 1, 2);
            Operand[] args = (argc == 2) ? popArgument(env, name, 2, ARRAY).arrayValue().values() : new Operand[0];
            String fn = popArgument(env, name, 1, STRING).stringValue();
            RuntimeFunction func = env.getFunctionByName(fn);
            if (func == null) error(name, "function '" + fn + "' not found.");
            if (!(func instanceof ScriptRuntimeFunction)) error(name, "cannot create thread for extern function.");
            InterpreterRuntime newEnv = InterpreterRuntime.copy(env);
            newEnv.setProgram(Program.coroutine(env, args.length).makeFrame());
            for (Operand a: args) newEnv.pushStack(a);
            func.call(newEnv, fn, args.length);
            new Thread(newEnv::run).start();
            env.pushStackNull();

        }));
        codeData.setFunction("random", ((env, name, argc) -> {
            if (random == null) random = new Random();
            long max = (argc >= 2) ? popArgument(env, name, 2, INT).intValue() : Long.MAX_VALUE;
            long min = (argc >= 1) ? popArgument(env, name, 1, INT).intValue() : Long.MIN_VALUE;
            if (min >= max) error(name, "min value cannot be greater than or equal to max value.");
            long rand = random.nextLong();
            env.pushStack((max == 0) ? (min + (rand >>> 1) % -min) : (min == 0) ? (max - ((rand >>> 1) % max)) : (rand + min) % max);

        }));
        codeData.setFunction("nanos_time", ((env, name, argc) -> {
            checkArgs(name, argc, 0);
            env.pushStack(System.nanoTime());

        }));
        codeData.setFunction("time", (env, name, argc) -> {
            checkArgs(name, argc, 0);
            env.pushStack(System.currentTimeMillis());

        });
        codeData.setFunction("defined", ((env, name, argc) -> {
            checkArgs(name, argc, 1);
            env.pushStack(env.getConstantByName(popArgument(env, name, 1, STRING).stringValue()) != null);

        }));
        codeData.setFunction("const_info", ((env, name, argc) -> {
            checkArgs(name, argc, 1);
            String n;
            Constant c = env.getConstantByName(n = popArgument(env, name, 1, STRING).stringValue());
            if (c == null) error(name, "constant '" + n + "' not exists.");
            Array array = new Array();
            array.set(env.getOperand("value"), c.value);
            array.set(env.getOperand("is_extern"), env.getOperand(c.isExtern));
            env.pushStack(array);

        }));
    }

    private static void checkArgs(String name, int argc, int tot) {
        checkArgs(name, argc, tot, tot);
    }

    private static void checkArgs(String name, int argc, int req, int tot) {
        if (argc < req) {
            error(name, "arguments too few. (required " + req + ", got " + argc + ')');
        } else if (argc > tot) {
            error(name, "arguments too many. (total " + tot + ", got " + argc + ')');
        }
    }

    private static Operand popArgument(InterpreterRuntime env, String name, int index, OperandType type) {
        Operand argument = env.popStack();
        OperandType type1 = argument.type();
        if ((type != null) && (type != type1)) {
            unexpectedTypes(name, index, new OperandType[]{type}, type1);
        }
        return argument;
    }

    private static void unexpectedTypes(String name, int index, OperandType[] expected, OperandType got) {
        StringJoiner sj = new StringJoiner(" or ");
        for (OperandType type: expected) sj.add(type.toString());
        error(name, "unexpected type at #" + index + " argument. (expected " + sj + ", got " + got + ')');
    }

    private static void error(String name, String message) {
        throw new InterpreterError(name + ": " + message);
    }
}
