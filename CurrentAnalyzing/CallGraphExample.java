public class CallGraphExample {
 
    public static void main(String[] args) {
        methodA();
        methodB();
    }
 
    public static void methodA() {
        System.out.println("Inside methodA");
        methodB();
    }
 
    public static void methodB() {
        System.out.println("Inside methodB");
        methodC();
        methodC();
        methodC();
        main(null);
    }
 
    public static void methodC() {
        System.out.println("Inside methodC");
    }
}