public class ArrayQueue {
    private int[] data;
    private int front;
    private int rear;
    private int size;
    private int capacity;

    public ArrayQueue(int capacity) {
        this.capacity = capacity;
        this.data = new int[capacity];
        this.front = 0;
        this.rear = -1; // rear is initialized to -1 as no elements are present yet
        this.size = 0;
    }

    // Enqueue operation: Adds an element to the rear of the queue
    public void enqueue(int element) {
        if (isFull()) {
            System.out.println("Queue is full. Cannot enqueue " + element);
            return;
        }
        rear = (rear + 1) % capacity; // Circular increment for rear
        data[rear] = element;
        size++;
        System.out.println(element + " enqueued to queue.");
    }

    // Dequeue operation: Removes and returns the element from the front of the queue
    public int dequeue() {
        if (isEmpty()) {
            System.out.println("Queue is empty. Cannot dequeue.");
            return -1; // Or throw an exception
        }
        int element = data[front];
        front = (front + 1) % capacity; // Circular increment for front
        size--;
        System.out.println(element + " dequeued from queue.");
        return element;
    }

    // Peek operation: Returns the element at the front without removing it
    public int peek() {
        if (isEmpty()) {
            System.out.println("Queue is empty. No element to peek.");
            return -1; // Or throw an exception
        }
        return data[front];
    }

    // Checks if the queue is empty
    public boolean isEmpty() {
        return size == 0;
    }

    // Checks if the queue is full
    public boolean isFull() {
        return size == capacity;
    }

    // Returns the current size of the queue
    public int size() {
        return size;
    }

    // Displays the elements in the queue
    public void display() {
        if (isEmpty()) {
            System.out.println("Queue is empty.");
            return;
        }
        System.out.print("Queue elements: ");
        for (int i = 0; i < size; i++) {
            System.out.print(data[(front + i) % capacity] + " ");
        }
        System.out.println();
    }

    public static void main(String[] args) {
        ArrayQueue queue = new ArrayQueue(5);

        queue.enqueue(10);
        queue.enqueue(20);
        queue.enqueue(30);
        queue.display();

        queue.dequeue();
        queue.display();

        queue.enqueue(40);
        queue.enqueue(50);
        queue.enqueue(60); // This will show "Queue is full"
        queue.display();

        System.out.println("Front element: " + queue.peek());
        System.out.println("Queue size: " + queue.size());
    }
}