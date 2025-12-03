import java.util.ArrayList;

public class Stack<T> {
    private ArrayList<T> items = new ArrayList<>();

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public void push(T item) {
        // Add item to the top of the stack
        items.add(item);
    }

    public T pop() {
        if (isEmpty()) {
            throw new RuntimeException("Pop from empty stack");
        }
        return items.remove(items.size() - 1);
    }

    public T peek() {
        if (isEmpty()) {
            throw new RuntimeException("Peek from empty stack");
        }
        return items.get(items.size() - 1);
    }

    public int size() {
        return items.size();
    }
}
