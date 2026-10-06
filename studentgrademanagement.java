/*
 * CS22301 OOP - Assignment 2, Question 2
 * Student Grade Management System  (single file, no packages)
 *
 * Demonstrates the Java Collections Framework:
 *   HashMap / LinkedHashMap, TreeSet, LinkedHashSet, ArrayList + Comparator,
 *   Queue (LinkedList), Deque as stack (ArrayDeque), PriorityQueue,
 *   TreeMap, Iterator, and a generic method.
 *
 * Save as : StudentGradeManagement.java
 * Compile : javac StudentGradeManagement.java
 * Run     : java StudentGradeManagement
 * Needs JDK 17 or newer.
 */
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

class StudentGradeManagement {

    /** A student. Two students are equal when they have the same ID (needed for HashSet / HashMap). */
    static class Student implements Comparable<Student> {
        private final String id;
        private final String name;
        private final String department;

        public Student(String id, String name, String department) {
            this.id = id;
            this.name = name;
            this.department = department;
        }

        public String getId()         { return id; }
        public String getName()       { return name; }
        public String getDepartment() { return department; }

        @Override
        public int compareTo(Student other) {
            return name.compareToIgnoreCase(other.name);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Student)) return false;
            return id.equals(((Student) o).id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public String toString() {
            return id + " " + name + " (" + department + ")";
        }
    }

    /**
     * Student Grade Management System - core of Q2.
     *
     * Collections used and why:
     *  Map  (LinkedHashMap)   students by ID           -> O(1) lookup, keeps insertion order
     *  Map  (HashMap)         marks: studentId -> (subject -> mark)  -> nested map
     *  Set  (TreeSet)         subjects                 -> no duplicates, always sorted
     *  Set  (LinkedHashSet)   failing / honours groups -> no duplicates, set operations
     *  List (ArrayList)       ranking                  -> sortable with Comparator
     *  Queue (LinkedList)     pending grade entries    -> FIFO processing
     *  Deque (ArrayDeque)     undo history             -> LIFO stack
     *  PriorityQueue          top-N students           -> heap
     *  TreeMap                grade distribution       -> keys sorted A..F
     *  Iterator               safe removal while looping
     */
    static class GradeBook {
        public static final int PASS_MARK = 40;
        public static final double HONOURS_AVERAGE = 75.0;

        /** A grade entry waiting in the queue. */
        public record GradeEntry(String studentId, String subject, int mark) { }

        private record UndoRecord(String studentId, String subject, Integer previousMark) { }

        private final Map<String, Student> students = new LinkedHashMap<>();
        private final Map<String, Map<String, Integer>> marks = new HashMap<>();
        private final Set<String> subjects = new TreeSet<>();
        private final Queue<GradeEntry> pendingQueue = new LinkedList<>();
        private final Deque<UndoRecord> undoStack = new ArrayDeque<>();

        // ---------- student management ----------

        /** @return false if a student with the same ID already exists */
        public boolean addStudent(Student student) {
            if (students.putIfAbsent(student.getId(), student) != null) {
                return false;
            }
            marks.put(student.getId(), new LinkedHashMap<>());
            return true;
        }

        public Collection<Student> getStudents() {
            return Collections.unmodifiableCollection(students.values());
        }

        public Map<String, Integer> getMarks(String studentId) {
            return Collections.unmodifiableMap(marks.getOrDefault(studentId, Collections.emptyMap()));
        }

        /** Removes students who have no marks, using an Iterator (the only safe way to remove while looping). */
        public int removeStudentsWithoutMarks() {
            int removed = 0;
            Iterator<Map.Entry<String, Student>> it = students.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Student> entry = it.next();
                if (marks.get(entry.getKey()).isEmpty()) {
                    it.remove();
                    marks.remove(entry.getKey());
                    removed++;
                }
            }
            return removed;
        }

        // ---------- queue: grade entry processing ----------

        public void submitEntry(String studentId, String subject, int mark) {
            pendingQueue.offer(new GradeEntry(studentId, subject, mark));
        }

        /** Processes the queue in FIFO order. Invalid entries are rejected and reported. */
        public int processPending() {
            int accepted = 0;
            while (!pendingQueue.isEmpty()) {
                GradeEntry entry = pendingQueue.poll();
                try {
                    apply(entry);
                    accepted++;
                } catch (NoSuchElementException | IllegalArgumentException e) {
                    System.out.println("  Rejected " + entry + " -> " + e.getMessage());
                }
            }
            return accepted;
        }

        private void apply(GradeEntry entry) {
            if (!students.containsKey(entry.studentId())) {
                throw new NoSuchElementException("unknown student ID " + entry.studentId());
            }
            if (entry.mark() < 0 || entry.mark() > 100) {
                throw new IllegalArgumentException("mark must be between 0 and 100");
            }
            Integer previous = marks.get(entry.studentId()).put(entry.subject(), entry.mark());
            subjects.add(entry.subject());
            undoStack.push(new UndoRecord(entry.studentId(), entry.subject(), previous));
        }

        // ---------- stack: undo ----------

        public boolean undoLast() {
            UndoRecord record = undoStack.poll(); // head of the deque = most recent push
            if (record == null) {
                return false;
            }
            Map<String, Integer> studentMarks = marks.get(record.studentId());
            if (record.previousMark() == null) {
                studentMarks.remove(record.subject());
            } else {
                studentMarks.put(record.subject(), record.previousMark());
            }
            return true;
        }

        // ---------- calculations ----------

        public double average(String studentId) {
            return marks.getOrDefault(studentId, Collections.emptyMap()).values().stream()
                    .mapToInt(Integer::intValue).average().orElse(0.0);
        }

        public static char gradeFor(double average) {
            if (average >= 90) return 'A';
            if (average >= 75) return 'B';
            if (average >= 60) return 'C';
            if (average >= 50) return 'D';
            return 'F';
        }

        /** List + Comparator: highest average first, ties broken by name. */
        public List<Student> ranking() {
            List<Student> list = new ArrayList<>(students.values());
            list.sort(byAverageDescending());
            return list;
        }

        /** PriorityQueue (min-heap) keeps only the best n students while scanning once. */
        public List<Student> topN(int n) {
            PriorityQueue<Student> heap = new PriorityQueue<>(byAverageDescending().reversed());
            for (Student s : students.values()) {
                heap.offer(s);
                if (heap.size() > n) {
                    heap.poll(); // drops the weakest of the current candidates
                }
            }
            List<Student> result = new ArrayList<>(heap);
            result.sort(byAverageDescending());
            return result;
        }

        /** TreeMap keeps the grade letters sorted: A, B, C, D, F. */
        public Map<Character, List<Student>> gradeDistribution() {
            Map<Character, List<Student>> distribution = new TreeMap<>();
            for (Student s : students.values()) {
                distribution.computeIfAbsent(gradeFor(average(s.getId())), k -> new ArrayList<>()).add(s);
            }
            return distribution;
        }

        public Map<String, Double> subjectAverages() {
            Map<String, Double> result = new TreeMap<>();
            for (String subject : subjects) {
                double avg = marks.values().stream()
                        .filter(m -> m.containsKey(subject))
                        .mapToInt(m -> m.get(subject))
                        .average().orElse(0.0);
                result.put(subject, avg);
            }
            return result;
        }

        public Set<String> getSubjects() {
            return Collections.unmodifiableSet(subjects);
        }

        // ---------- sets ----------

        public Set<Student> failingStudents() {
            Set<Student> failing = new LinkedHashSet<>();
            for (Student s : students.values()) {
                if (marks.get(s.getId()).values().stream().anyMatch(m -> m < PASS_MARK)) {
                    failing.add(s);
                }
            }
            return failing;
        }

        public Set<Student> honoursStudents() {
            Set<Student> honours = new LinkedHashSet<>();
            for (Student s : students.values()) {
                if (average(s.getId()) >= HONOURS_AVERAGE) {
                    honours.add(s);
                }
            }
            return honours;
        }

        private Comparator<Student> byAverageDescending() {
            return Comparator.comparingDouble((Student s) -> average(s.getId()))
                    .reversed()
                    .thenComparing(Student::getName);
        }
    }

    // ======================= MAIN PROGRAM =======================

    /** Generic helper: works for any collection of any element type. */
    private static <T> void printAll(String title, Collection<T> items) {
        System.out.println("\n--- " + title + " ---");
        if (items.isEmpty()) {
            System.out.println("  (none)");
        }
        for (T item : items) {
            System.out.println("  " + item);
        }
    }

    private static void printMarks(GradeBook book) {
        System.out.println("\n--- Marks sheet ---");
        for (Student s : book.getStudents()) {
            System.out.printf("  %-28s %-32s avg %6.2f  grade %c%n",
                    s, book.getMarks(s.getId()), book.average(s.getId()), GradeBook.gradeFor(book.average(s.getId())));
        }
    }

    public static void main(String[] args) {
        GradeBook book = new GradeBook();

        // Map: duplicate IDs are detected because IDs are keys
        book.addStudent(new Student("S101", "Arun", "CSE"));
        book.addStudent(new Student("S102", "Bhavana", "CSE"));
        book.addStudent(new Student("S103", "Charles", "ECE"));
        book.addStudent(new Student("S104", "Deepa", "CSE"));
        book.addStudent(new Student("S105", "Elan", "MECH"));
        book.addStudent(new Student("S106", "Farah", "IT"));   // will have no marks
        System.out.println("Duplicate S101 added? " + book.addStudent(new Student("S101", "Arun Copy", "CSE")));

        // Queue: grade entries wait in FIFO order, including some wrong ones
        String[] subjects = {"Maths", "Physics", "Java"};
        int[][] data = {
                {95, 88, 92},   // Arun
                {72, 65, 80},   // Bhavana
                {35, 78, 60},   // Charles  (fails Maths)
                {96, 38, 91},   // Deepa   (honours average, but fails Physics)
                {55, 48, 52}    // Elan
        };
        String[] ids = {"S101", "S102", "S103", "S104", "S105"};
        for (int i = 0; i < ids.length; i++) {
            for (int j = 0; j < subjects.length; j++) {
                book.submitEntry(ids[i], subjects[j], data[i][j]);
            }
        }
        book.submitEntry("S999", "Java", 70);   // unknown student
        book.submitEntry("S102", "Java", 150);  // invalid mark
        System.out.println("\nProcessing queued grade entries...");
        System.out.println("Accepted entries: " + book.processPending());

        printMarks(book);

        // List + Comparator
        printAll("Ranking (List sorted by average)", book.ranking());

        // PriorityQueue
        printAll("Top 3 students (PriorityQueue)", book.topN(3));

        // TreeMap
        System.out.println("\n--- Grade distribution (TreeMap) ---");
        for (Map.Entry<Character, List<Student>> e : book.gradeDistribution().entrySet()) {
            System.out.println("  Grade " + e.getKey() + " : " + e.getValue());
        }

        System.out.println("\n--- Subject averages (TreeMap) ---");
        book.subjectAverages().forEach((subject, avg) -> System.out.printf("  %-8s %.2f%n", subject, avg));

        // Sets: no duplicates + set operations
        Set<Student> failing = book.failingStudents();
        Set<Student> honours = book.honoursStudents();
        printAll("Failing in at least one subject (Set)", failing);
        printAll("Honours students, average >= 75 (Set)", honours);

        Set<Student> honoursButFailed = new LinkedHashSet<>(honours);
        honoursButFailed.retainAll(failing);          // intersection
        printAll("Honours AND failed a subject (intersection)", honoursButFailed);

        Set<Student> cleanHonours = new LinkedHashSet<>(honours);
        cleanHonours.removeAll(failing);              // difference
        printAll("Honours with no failed subject (difference)", cleanHonours);

        // Stack (Deque): undo the most recent entry
        book.submitEntry("S101", "Java", 40);
        book.processPending();
        System.out.println("\nArun's Java mark changed to: " + book.getMarks("S101").get("Java"));
        book.undoLast();
        System.out.println("After undo, Arun's Java mark is: " + book.getMarks("S101").get("Java"));

        // Iterator: safe removal
        System.out.println("\nStudents removed (no marks): " + book.removeStudentsWithoutMarks());
        printAll("Students after cleanup", book.getStudents());
        printAll("Subjects offered (TreeSet)", book.getSubjects());
    }
}