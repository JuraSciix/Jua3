package jua.runtime;

import jua.compiler.Types;
import jua.interpreter.Address;
import jua.interpreter.InterpreterThread;
import jua.runtime.NativeSupport.NativeFunctionPresent;
import jua.runtime.NativeSupport.ParamsData;
import jua.runtime.heap.ListHeap;
import jua.runtime.heap.StringHeap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class NativeStdlib {

    /** nativeFunctionPresents */
    private static final ArrayList<NativeFunctionPresent> nfp = new ArrayList<>();

    static {
        nfp.add(new PrintFunction());
        nfp.add(new PrintlnFunction());
        nfp.add(new TypeofFunction());
        nfp.add(new TimeFunction());
        nfp.add(new PanicFunction());
        nfp.add(new StrCodePointsFunction());
    }

    static class PrintFunction extends NativeFunctionPresent {

        PrintFunction() {
            super("print", ParamsData.create().add("value"));
        }

        @Override
        public boolean execute(Address[] args, int argc, Address returnAddress) {
            Address buffer = new Address();
            boolean error = !args[0].stringVal(buffer);
            if (error) return false;
            System.out.print(buffer.getStringHeap().toString());
            returnAddress.setNull();
            return true;
        }
    }

    static class PrintlnFunction extends NativeFunctionPresent {

        PrintlnFunction() {
            super("println", ParamsData.create().optional("value", ""));
        }

        @Override
        public boolean execute(Address[] args, int argc, Address returnAddress) {
            Address buffer = new Address();
            boolean error = !args[0].stringVal(buffer);
            if (error) return false;
            System.out.println(buffer.getStringHeap().toString());
            returnAddress.setNull();
            return true;
        }
    }

    static class TypeofFunction extends NativeFunctionPresent {

        TypeofFunction() {
            super("typeof", ParamsData.create().add("value"));
        }

        @Override
        public boolean execute(Address[] args, int argc, Address returnAddress) {
            returnAddress.set(new StringHeap(args[0].getTypeName()));
            return true;
        }
    }

    static class TimeFunction extends NativeFunctionPresent {

        TimeFunction() {
            super("time", ParamsData.create());
        }

        @Override
        public boolean execute(Address[] args, int argc, Address returnAddress) {
            returnAddress.set(System.nanoTime() / 1E9);
            return true;
        }
    }

    static class PanicFunction extends NativeFunctionPresent {

        PanicFunction() {
            super("panic", ParamsData.create().add("msg"));
        }

        @Override
        public boolean execute(Address[] args, int argc, Address returnAddress) {
            Address msg = new Address();
            if (!args[0].stringVal(msg)) return false;
            InterpreterThread.threadError(msg.getStringHeap().toString());
            returnAddress.setNull();
            return false;
        }
    }

    static class StrCodePointsFunction extends NativeFunctionPresent {

        StrCodePointsFunction() {
            super("str_code_points", ParamsData.create().add("str"));
        }

        @Override
        public boolean execute(Address[] args, int argc, Address returnAddress) {
            if (!args[0].testType(ValueType.STRING)) return false;
            StringHeap str = args[0].getStringHeap();
            ListHeap chars = new ListHeap(str.length());
            for (int i = 0; i < str.length(); ) {
                int cp = str.codePointAt(i);
                chars.get(i).set(cp);
                i += Character.charCount(cp);
            }
            returnAddress.set(chars);
            return true;
        }
    }

    public static Map<String, Types.Type> getNativeConstants() {
        return Collections.emptyMap();
    }

    public static List<Function> getNativeFunctions() {
        return nfp.stream().map(NativeFunctionPresent::build).collect(Collectors.toList());
    }
}
