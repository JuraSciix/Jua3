package jua.runtime;

import jua.interpreter.Address;
import jua.interpreter.InterpreterThread;
import jua.runtime.NativeSupport.NativeFunctionPresent;
import jua.runtime.NativeSupport.ParamsData;
import jua.runtime.heap.ListHeap;
import jua.runtime.heap.StringHeap;
import jua.utils.ObjectSizeAnalyzing;

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
        nfp.add(new _SizeOfFunction());
        nfp.add(new SqrtFunction());
        nfp.add(new StrCharArrayFunction());
        nfp.add(new StrCmpFunction());
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
            if (!args[0].testType(Types.T_STRING)) return false;
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

    static class _SizeOfFunction extends NativeFunctionPresent {

        _SizeOfFunction() {
            super("_sizeof", ParamsData.create().add("val"));
        }

        @Override
        public boolean execute(Address[] args, int argc, Address returnAddress) {
            returnAddress.set(ObjectSizeAnalyzing.analyzeSize(args[0]));
            return true;
        }
    }

    static class SqrtFunction extends NativeFunctionPresent {

        SqrtFunction() {
            super("sqrt", ParamsData.create().add("x"));
        }

        @Override
        public boolean execute(Address[] args, int argc, Address returnAddress) {
            Address doubleVal = new Address();
            if (args[0].doubleVal(doubleVal)) {
                returnAddress.set(Math.sqrt(doubleVal.getDouble()));
                return true;
            }
            return false;
        }
    }

    static class StrCharArrayFunction extends NativeFunctionPresent {

        StrCharArrayFunction() {
            super("str_char_array", ParamsData.create().add("str"));
        }

        @Override
        public boolean execute(Address[] args, int argc, Address returnAddress) {
            Address str = args[0];
            if (!str.hasType(Types.T_STRING)) {
                InterpreterThread.threadError("str_char_array: str must be string");
                return false;
            }
            StringHeap strHeap = str.getStringHeap();
            ListHeap charArray = new ListHeap(strHeap.length());
            for (int i = 0; i < strHeap.length(); i++) {
                charArray.get(i).set(strHeap.subSequence(i, i + 1));
            }
            returnAddress.set(charArray);
            return true;
        }
    }

    static class StrCmpFunction extends NativeFunctionPresent {

        StrCmpFunction() {
            super("str_cmp", ParamsData.create().add("lhs").add("rhs"));
        }

        @Override
        public boolean execute(Address[] args, int argc, Address returnAddress) {
            Address lhs = args[0];
            Address rhs = args[1];
            if (!lhs.hasType(Types.T_STRING)) {
                InterpreterThread.threadError("str_cmp: lhs must be string");
                return false;
            }
            if (!rhs.hasType(Types.T_STRING)) {
                InterpreterThread.threadError("str_cmp: lhs must be string");
                return false;
            }
            returnAddress.set(lhs.getStringHeap().compareTo(rhs.getStringHeap()));
            return true;
        }
    }

    public static Map<String, Address> getNativeConstants() {
        return Collections.emptyMap();
    }

    public static List<Function> getNativeFunctions() {
        return nfp.stream().map(NativeFunctionPresent::build).collect(Collectors.toList());
    }
}
