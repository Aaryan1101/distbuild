public class TestBuild {
    public static void main(String[] args) {
        System.out.println("Hello from distributed build!");
        
        // Test compilation with some logic
        int result = 0;
        for (int i = 1; i <= 10; i++) {
            result += i;
        }
        
        System.out.println("Sum of 1-10: " + result);
        System.out.println("Build successful!");
    }
}
