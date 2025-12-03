public class CallGraphExample {
 
    public static void main(String[] args) {
        methodA();
        methodB();
        methodC();
    }
 
    public static void methodA() {
        //Hello
        System.out.println("Inside methodA");
        methodB();
        methodC();
    }
 
    public static void methodB() {
        System.out.println("Inside methodB");
        main(null);
        
    }
 
    public static void methodC() {
        methodB();
        System.out.println("Inside methodC");
    }
}