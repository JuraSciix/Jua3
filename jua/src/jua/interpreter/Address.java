package jua.interpreter;

import jua.runtime.ValueType;
import jua.runtime.heap.Heap;
import jua.runtime.heap.MapHeap;
import jua.runtime.heap.StringHeap;
import jua.util.Conversions;

import static jua.interpreter.InterpreterThread.threadError;
import static jua.runtime.ValueType.*;

public final class Address {

    public static Address allocateCopy(Address source) {
        Address copy = new Address();
        copy.set(source);
        return copy;
    }

    public static Address[] allocateMemory(int count, int start) {
        if (!(0xFFFF >= count && count >= start && start >= 0)) {
            throw new IllegalArgumentException("count: " + count + ", start: " + start);
        }

        Address[] memory = new Address[count];

        for (int i = start; i < count; i++) {
            memory[i] = new Address();
        }

        return memory;
    }

    public static void arraycopy(Address[] src, int srcOffset, Address[] dst, int dstOffset, int count) {
        if (src == null) {
            throw new IllegalArgumentException("Source memory is null");
        }

        if (dst == null) {
            throw new IllegalArgumentException("Destination memory is null");
        }

        if (srcOffset < 0 || dstOffset < 0 || count < 0 || (srcOffset + count) >= src.length || (dstOffset + count) >= dst.length) {
            String message = String.format(
                    "Memory (length, offset): source (%d, %d), destination (%d, %d). Count: %d",
                    src.length, srcOffset, dst.length, dstOffset, count
            );
            throw new IllegalArgumentException(message);
        }

        for (int i = 0; i < count; i++) {
            src[srcOffset + i].quickSet(dst[dstOffset + i]);
        }
    }

    /** Тип текущего значения. */
    private byte type;

    private long l;
    private double d;
    private Heap a;

    /** Возвращает тип текущего значения. */
    public byte getType() { return type; }

    /** Возвращает название типа текущего значения. */
    public String getTypeName() { return ValueType.getTypeName(type); }

    /** Возвращает {@code true}, если текущее значение считается скалярным, иначе {@code false}. */
    public boolean isScalar() { return isTypeScalar(type); }

    /* * * * * * * * * * * * * * * * * * * *
     *               ГЕТТЕРЫ               *
     * * * * * * * * * * * * * * * * * * * */

    public long getLong() { return l; }

    public double getDouble() { return d; }

    public boolean getBoolean() { return Conversions.l2b(l); }

    public StringHeap getStringHeap() { return (StringHeap) a; }

    public MapHeap getMapHeap() { return (MapHeap) a; }

    /* * * * * * * * * * * * * * * * * * * *
     *           ПРЕОБРАЗОВАНИЯ            *
     * * * * * * * * * * * * * * * * * * * */

    /** Преобразовывает значение в целочисленное 64-битное. */
    public boolean longVal(Address dst) {
        switch (type) {
            case NULL:
                dst.set(0L);
                return true;
            case LONG:
                dst.set(getLong());
                return true;
            case DOUBLE:
                dst.set((long) getDouble());
                return true;
            case BOOLEAN:
                dst.set(l & 1L);
                return true;
            default:
                badTypeConversion(LONG);
                return false;
        }
    }

    /** Преобразовывает значение в вещественное 64-битное. */
    public boolean doubleVal(Address dst) {
        switch (type) {
            case NULL:
                dst.set(0.0);
                return true;
            case LONG:
                dst.set((double) getLong());
                return true;
            case DOUBLE:
                dst.set(getDouble());
                return true;
            case BOOLEAN:
                dst.set((double) (l & 1L));
                return true;
            default:
                badTypeConversion(DOUBLE);
                return false;
        }
    }

    /** Преобразовывает значение в логическое. */
    public boolean booleanVal(Address dst) {
        switch (type) {
            case NULL:
                dst.set(false);
                return true;
            case LONG:
            case BOOLEAN:
                dst.set(Conversions.l2b(getLong()));
                return true;
            case DOUBLE:
                dst.set(getDouble() != 0.0);
                return true;
            case STRING:
                dst.set(getStringHeap().nonEmpty());
                return true;
            case MAP:
                dst.set(getMapHeap().nonEmpty());
                return true;
            default:
                badTypeConversion(BOOLEAN);
                return false;
        }
    }

    public boolean stringVal(Address dst) {
        switch (type) {
            case NULL:
                dst.set(new StringHeap().appendNull());
                return true;
            case LONG:
                dst.set(new StringHeap().append(getLong()));
                return true;
            case DOUBLE:
                dst.set(new StringHeap().append(getDouble()));
                return true;
            case BOOLEAN:
                dst.set(new StringHeap().append(getBoolean()));
                return true;
            case STRING:
                dst.set(getStringHeap());
                return true;
            default:
                badTypeConversion(STRING);
                return false;
        }
    }

    public boolean mapValue(Address dst) {
        if (type == MAP) {
            dst.set(getMapHeap());
            return true;
        }
        badTypeConversion(MAP);
        return false;
    }

    public boolean testType(byte type) {
        if (type == this.type) {
            return true;
        }
        badTypeConversion(type);
        return false;
    }

    private void badTypeConversion(byte type) {
        threadError("Cannot convert %s to %s", getTypeName(), ValueType.getTypeName(type));
    }

    public boolean isNull() {
        return type == NULL;
    }

    /* * * * * * * * * * * * * * * * * * * *
     *               СЕТТЕРЫ               *
     * * * * * * * * * * * * * * * * * * * */

    public void set(long _l) {
        type = LONG;
        l = _l;
    }

    public void set(boolean b) {
        type = BOOLEAN;
        l = Conversions.b2l(b);
    }

    public void set(double _d) {
        type = DOUBLE;
        d = _d;
    }

    public void set(StringHeap s) {
        type = STRING;
        a = s;
    }

    public void set(MapHeap m) {
        type = MAP;
        a = m;
    }

    public void quickSet(Address source) {
        type = source.type;
        l = source.l;
        d = source.d;
        a = source.a;
    }

    public void set(Address source) {
        switch (source.type) {
            case NULL:
                setNull();
                return;
            case LONG:
                set(source.getLong());
                return;
            case DOUBLE:
                set(source.getDouble());
                return;
            case BOOLEAN:
                set(source.getBoolean());
                return;
            case STRING:
                set(source.getStringHeap().copy());
                return;
            case MAP:
                set(source.getMapHeap().copy());
                return;
            default:
                throw new AssertionError(source.type);
        }
    }

    public void slowSet(Address source) {
        switch (source.type) {
            case NULL:
                setNull();
                return;
            case LONG:
                set(source.getLong());
                return;
            case DOUBLE:
                set(source.getDouble());
                return;
            case BOOLEAN:
                set(source.getBoolean());
                return;
            case STRING:
                set(source.getStringHeap().deepCopy());
                return;
            case MAP:
                set(source.getMapHeap().deepCopy());
                return;
            default:
                throw new AssertionError(source.type);
        }
    }

    public void setNull() {
        type = NULL;
    }

    public void reset() {
        type = UNDEFINED;
        a = null; // В помощь GC
    }

    public boolean hasType(byte type) {
        return getType() == type;
    }

    /* * * * * * * * * * * * * * * * * * * *
     *          БИНАРНЫЕ ОПЕРАЦИИ          *
     * * * * * * * * * * * * * * * * * * * */

    public boolean add(Address rhs, Address result) {
        int union = getTypeUnion(type, rhs.type);

        if (getTypeUnion(LONG, LONG) == union) {
            result.set(getLong() + rhs.getLong());
            return true;
        }

        if (getTypeUnion(LONG, DOUBLE) == union) {
            result.set(getLong() + rhs.getDouble());
            return true;
        }

        if (getTypeUnion(DOUBLE, LONG) == union) {
            result.set(getDouble() + rhs.getLong());
            return true;
        }

        if (getTypeUnion(DOUBLE, DOUBLE) == union) {
            result.set(getDouble() + rhs.getDouble());
            return true;
        }

        if (getTypeUnion(STRING, STRING) == union) {
            if (this == result) {
                getStringHeap().append(rhs.getStringHeap());
            } else {
                result.set(new StringHeap().append(getStringHeap()).append(getStringHeap()));
            }
            return true;
        }

        if (type == STRING) {
            Address tmp = new Address();
            if (!rhs.stringVal(tmp)) {
                return false;
            }
            result.set(new StringHeap().append(getStringHeap()).append(tmp.getStringHeap()));
            return true;
        }

        if (rhs.type == STRING) {
            Address tmp = new Address();
            if (!stringVal(tmp)) {
                return false;
            }
            result.set(new StringHeap().append(tmp.getStringHeap()).append(rhs.getStringHeap()));
            return true;
        }

        return binaryOperatorError("+", rhs);
    }

    public boolean sub(Address rhs, Address result) {
        int union = getTypeUnion(type, rhs.type);

        if (getTypeUnion(LONG, LONG) == union) {
            result.set(getLong() - rhs.getLong());
            return true;
        }

        if (getTypeUnion(LONG, DOUBLE) == union) {
            result.set((double) getLong() - rhs.getDouble());
            return true;
        }

        if (getTypeUnion(DOUBLE, LONG) == union) {
            result.set(getDouble() - (double) rhs.getLong());
            return true;
        }

        if (getTypeUnion(DOUBLE, DOUBLE) == union) {
            result.set(getDouble() - rhs.getDouble());
            return true;
        }

        return binaryOperatorError("-", rhs);
    }

    public boolean mul(Address rhs, Address result) {
        int union = getTypeUnion(type, rhs.type);

        if (getTypeUnion(LONG, LONG) == union) {
            result.set(getLong() * rhs.getLong());
            return true;
        }

        if (getTypeUnion(LONG, DOUBLE) == union) {
            result.set((double) getLong() * rhs.getDouble());
            return true;
        }

        if (getTypeUnion(DOUBLE, LONG) == union) {
            result.set(getDouble() * (double) rhs.getLong());
            return true;
        }

        if (getTypeUnion(DOUBLE, DOUBLE) == union) {
            result.set(getDouble() * rhs.getDouble());
            return true;
        }

        return binaryOperatorError("*", rhs);
    }

    public boolean div(Address rhs, Address result) {
        int union = getTypeUnion(type, rhs.type);

        if (getTypeUnion(LONG, LONG) == union) {
            if (rhs.getLong() == 0L) {
                threadError("integer division by zero");
                return false;
            }
            result.set(getLong() / rhs.getLong());
            return true;
        }

        if (getTypeUnion(LONG, DOUBLE) == union) {
            result.set((double) getLong() / rhs.getDouble());
            return true;
        }

        if (getTypeUnion(DOUBLE, LONG) == union) {
            result.set(getDouble() / (double) rhs.getLong());
            return true;
        }

        if (getTypeUnion(DOUBLE, DOUBLE) == union) {
            result.set(getDouble() / rhs.getDouble());
            return true;
        }

        return binaryOperatorError("/", rhs);
    }

    public boolean rem(Address rhs, Address result) {
        int union = getTypeUnion(type, rhs.type);

        if (getTypeUnion(LONG, LONG) == union) {
            if (rhs.getLong() == 0L) {
                threadError("modulo by zero");
                return false;
            }
            result.set(getLong() % rhs.getLong());
            return true;
        }

        if (getTypeUnion(LONG, DOUBLE) == union) {
            if (rhs.getDouble() == 0.0) {
                threadError("modulo by zero");
                return false;
            }
            result.set(getLong() % rhs.getDouble());
            return true;
        }

        if (getTypeUnion(DOUBLE, LONG) == union) {
            if (rhs.getLong() == 0L) {
                threadError("modulo by zero");
                return false;
            }
            result.set(getDouble() % rhs.getLong());
            return true;
        }

        if (getTypeUnion(DOUBLE, DOUBLE) == union) {
            if (rhs.getDouble() == 0.0) {
                threadError("modulo by zero");
                return false;
            }
            result.set(getDouble() % rhs.getDouble());
            return true;
        }

        return binaryOperatorError("%", rhs);
    }

    public boolean shl(Address rhs, Address result) {
        if (getTypeUnion(LONG, LONG) == getTypeUnion(type, rhs.type)) {
            result.set(getLong() << rhs.getLong());
            return true;
        }

        return binaryOperatorError("<<", rhs);
    }

    public boolean shr(Address rhs, Address result) {
        if (getTypeUnion(LONG, LONG) == getTypeUnion(type, rhs.type)) {
            result.set(getLong() >> rhs.getLong());
            return true;
        }

        return binaryOperatorError(">>", rhs);
    }

    public boolean and(Address rhs, Address result) {
        int union = getTypeUnion(type, rhs.type);

        if (getTypeUnion(LONG, LONG) == union) {
            result.set(getLong() & rhs.getLong());
            return true;
        }

        if (getTypeUnion(BOOLEAN, BOOLEAN) == union) {
            result.set(getBoolean() & rhs.getBoolean());
            return true;
        }

        return binaryOperatorError("&", rhs);
    }

    public boolean or(Address rhs, Address result) {
        int union = getTypeUnion(type, rhs.type);

        if (getTypeUnion(LONG, LONG) == union) {
            result.set(getLong() | rhs.getLong());
            return true;
        }

        if (getTypeUnion(BOOLEAN, BOOLEAN) == union) {
            result.set(getBoolean() | rhs.getBoolean());
            return true;
        }

        return binaryOperatorError("|", rhs);
    }

    public boolean xor(Address rhs, Address result) {
        int union = getTypeUnion(type, rhs.type);

        if (getTypeUnion(LONG, LONG) == union) {
            result.set(getLong() ^ rhs.getLong());
            return true;
        }

        if (getTypeUnion(BOOLEAN, BOOLEAN) == union) {
            result.set(getBoolean() ^ rhs.getBoolean());
            return true;
        }

        return binaryOperatorError("^", rhs);
    }

    private boolean binaryOperatorError(String operator, Address rhs) {
        threadError(
                "Cannot apply binary '%s' with %s and %s", operator, getTypeName(), rhs.getTypeName()
        );
        // Методы бинарных операций возвращают результат этой функции, чтобы сократить число строк =)
        return false;
    }

    /* * * * * * * * * * * * * * * * * * * *
     *          УНАРНЫЕ ОПЕРАЦИИ           *
     * * * * * * * * * * * * * * * * * * * */

    public boolean neg(Address result) { // -x
        if (type == LONG) {
            result.set(-getLong());
            return true;
        }

        if (type == DOUBLE) {
            result.set(-getDouble());
            return true;
        }

        return unaryOperatorError("-");
    }

    public boolean pos(Address result) { // +x
        if (type == LONG) {
            result.set(+getLong());
            return true;
        }

        if (type == DOUBLE) {
            result.set(+getDouble());
            return true;
        }

        return unaryOperatorError("+");
    }

    public boolean not(Address result) { // ~x
        if (type == LONG) {
            result.set(~l);
            return true;
        }

        return unaryOperatorError("~");
    }

    public boolean inc(Address result) { // -x
        if (type == LONG) {
            result.set(getLong() + 1L);
            return true;
        }

        if (type == DOUBLE) {
            result.set(getDouble() + 1.0);
            return true;
        }

        return unaryOperatorError("++");
    }

    public boolean dec(Address result) { // -x
        if (type == LONG) {
            result.set(getLong() - 1L);
            return true;
        }

        if (type == DOUBLE) {
            result.set(getDouble() - 1.0);
            return true;
        }

        return unaryOperatorError("--");
    }

    private boolean unaryOperatorError(String operator) {
        threadError("Cannot apply unary '%s' with %s", operator, getTypeName());
        // Методы унарных операций возвращают результат этой функции, чтобы сократить число строк =)
        return false;
    }

    public int quickCompare(Address rhs, int except) {
        int union = getTypeUnion(type, rhs.type);

        if (getTypeUnion(LONG, LONG) == union) {
            return Long.compare(getLong(), rhs.getLong());
        }

        if (getTypeUnion(LONG, DOUBLE) == union) {
            return Double.compare(getLong(), rhs.getDouble());
        }

        if (getTypeUnion(DOUBLE, LONG) == union) {
            return Double.compare(getDouble(), rhs.getLong());
        }

        if (getTypeUnion(DOUBLE, DOUBLE) == union) {
            if (Double.isNaN(getDouble()) || Double.isNaN(rhs.getDouble()))
                return except;
            else
                return Double.compare(getDouble(), rhs.getDouble());
        }

        if (getTypeUnion(STRING, STRING) == union) {
            return getStringHeap().compare(rhs.getStringHeap());
        }

        if (getTypeUnion(MAP, MAP) == union) {
            return getMapHeap().compare(rhs.getMapHeap(), except);
        }

        if (getTypeUnion(NULL, NULL) == union) {
            return 0;
        }

        return except;
    }

    public int quickConstCompare(short value, int except) {
        if (type == LONG) {
            return Long.compare(l, value);
        }
        if (type == DOUBLE) {
            return Double.compare(d, value);
        }
        return except;
    }

    @Override
    public int hashCode() {
        switch (type) {
            case NULL:    return 0;
            case LONG:    return Long.hashCode(getLong());
            case BOOLEAN: return Boolean.hashCode(getBoolean());
            case DOUBLE:  return Double.hashCode(getDouble());
            case STRING:  return getStringHeap().hashCode();
            case MAP:     return getMapHeap().hashCode();
            default:      throw new AssertionError(type);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true; // Очень маловероятно
        if (o == null || getClass() != o.getClass()) return false;
        return quickCompare((Address) o, Integer.MIN_VALUE) == 0;
    }

    @Override
    public String toString() {
        // Этот метод не связан с рантаймом Jua
        switch (type) {
            case NULL:    return "null";
            case LONG:    return Long.toString(getLong());
            case DOUBLE:  return Double.toString(getDouble());
            case BOOLEAN: return Boolean.toString(getBoolean());
            case STRING:  return getStringHeap().toString();
            case MAP:     return getMapHeap().toString();
            default:      throw new AssertionError(type);
        }
    }
}
