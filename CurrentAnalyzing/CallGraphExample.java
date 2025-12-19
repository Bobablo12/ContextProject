public class CallGraphExample {
 
    public static void main(String[] args) {
        methodA();
    }

    /**
     * Fits the given element into this range by returning the given element or, if out of bounds, the range minimum if
     * below, or the range maximum if above.
     *
     * <pre>{@code
     * Range<Integer> range = Range.between(16, 64);
     * range.fit(-9) -->  16
     * range.fit(0)  -->  16
     * range.fit(15) -->  16
     * range.fit(16) -->  16
     * range.fit(17) -->  17
     * ...
     * range.fit(63) -->  63
     * range.fit(64) -->  64
     * range.fit(99) -->  64
     * }</pre>
     * @param element the element to check for, not null
     * @return the minimum, the element, or the maximum depending on the element's location relative to the range
     * @throws NullPointerException if {@code element} is {@code null}
     * @since 3.10
     */
    public static void methodA() {
        System.out.println("Inside methodA");
        methodB();
        methodC();
        methodC();
    }

    /**
     * Fits the given element into this range by returning the given element or, if out of bounds, the range minimum if
     * below, or the range maximum if above.
     *
     * <pre>{@code
     * Range<Integer> range = Range.between(16, 64);
     * range.fit(-9) -->  16
     * range.fit(0)  -->  16
     * range.fit(15) -->  16
     * range.fit(16) -->  16
     * range.fit(17) -->  17
     * ...
     * range.fit(63) -->  63
     * range.fit(64) -->  64
     * range.fit(99) -->  64
     * }</pre>
     * @param element the element to check for, not null
     * @return the minimum, the element, or the maximum depending on the element's location relative to the range
     * @throws NullPointerException if {@code element} is {@code null}
     * @since 3.10
     */
    public static void methodB() {
        System.out.println("Inside methodB");
        methodC();
        methodA();
    }
 
    public static void methodC() {
        System.out.println("Inside methodC");
    }
}